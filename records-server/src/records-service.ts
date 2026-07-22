import { and, asc, count, desc, eq, gte, inArray, lt, lte, ne, sql } from "drizzle-orm";
import type { RecordEntryInput, RecordEntryUpdateInput, RecordItemInput } from "./domain.js";
import { ApiError } from "./domain.js";
import type { RecordsDatabase } from "./database.js";
import { recordAuditLogs, recordEntries, recordItems, recordWindows } from "./schema.js";

const AuditLogPageSize = 100;

type RecordItemRow = typeof recordItems.$inferSelect;
type RecordWindowRow = typeof recordWindows.$inferSelect;
type RecordEntryRow = typeof recordEntries.$inferSelect;
type RecordsTransaction = Parameters<Parameters<RecordsDatabase["transaction"]>[0]>[0];

type AuditAction =
  | "ITEM_CREATED"
  | "ITEM_UPDATED"
  | "ITEM_ARCHIVED"
  | "ITEM_RESTORED"
  | "ITEM_DELETED"
  | "ENTRY_CREATED"
  | "ENTRY_UPDATED"
  | "ENTRY_DELETED";

interface ItemAuditSnapshot {
  id: number;
  name: string;
  description: string;
  mode: string;
  valueType: string;
  defaultValue: number | null;
  stepValue: number;
  unit: string;
  goalValue: number | null;
  goalPeriod: string | null;
  priority: number;
  archived: boolean;
  windows: Array<{ startMinute: number; endMinute: number }>;
  entryCount?: number;
}

interface EntryAuditSnapshot {
  id: number;
  itemId: number;
  itemName: string;
  occurredAt: number;
  localDate: string;
  localMinute: number;
  value: number;
}

type AuditSnapshot = ItemAuditSnapshot | EntryAuditSnapshot;

interface AuditWrite {
  action: AuditAction;
  entityType: "ITEM" | "ENTRY";
  entityId: number;
  before?: AuditSnapshot;
  after?: AuditSnapshot;
  createdAt: number;
}

export class RecordsService {
  constructor(
    private readonly db: RecordsDatabase,
    private readonly now: () => number = Date.now,
  ) {}

  listItems(archived: boolean) {
    const rows = this.db
      .select()
      .from(recordItems)
      .where(eq(recordItems.archived, archived))
      .orderBy(sql`${recordItems.name} COLLATE NOCASE`)
      .all();
    return this.attachWindows(rows);
  }

  getItem(id: number) {
    const row = this.db.select().from(recordItems).where(eq(recordItems.id, id)).get();
    if (!row) throw new ApiError(404, "Record item not found.");
    return this.attachWindows([row])[0]!;
  }

  createItem(input: RecordItemInput) {
    const id = this.db.transaction((tx) => {
      this.requireUniqueName(input.name);
      const now = this.now();
      const created = tx
        .insert(recordItems)
        .values({ ...withoutWindows(input), archived: false, createdAt: now, updatedAt: now })
        .returning({ id: recordItems.id })
        .get();
      if (input.windows.length > 0) {
        tx.insert(recordWindows)
          .values(input.windows.map((window) => ({ ...window, itemId: created.id })))
          .run();
      }
      this.writeAudit(tx, {
        action: "ITEM_CREATED",
        entityType: "ITEM",
        entityId: created.id,
        after: itemSnapshot({ id: created.id, ...input, archived: false }),
        createdAt: now,
      });
      return created.id;
    });
    return this.getItem(id);
  }

  updateItem(id: number, input: RecordItemInput) {
    this.db.transaction((tx) => {
      const current = tx.select().from(recordItems).where(eq(recordItems.id, id)).get();
      if (!current) throw new ApiError(404, "Record item not found.");
      this.requireUniqueName(input.name, id);
      const entryTotal = tx
        .select({ value: count() })
        .from(recordEntries)
        .where(eq(recordEntries.itemId, id))
        .get()!.value;
      if (entryTotal > 0 && (
        current.mode !== input.mode
        || current.valueType !== input.valueType
        || current.unit !== input.unit
      )) {
        throw new ApiError(400, "Mode, type, and unit cannot change after records exist.");
      }

      const before = itemSnapshot(
        current,
        tx.select().from(recordWindows).where(eq(recordWindows.itemId, id)).all(),
      );
      const now = this.now();
      tx.update(recordItems)
        .set({ ...withoutWindows(input), updatedAt: now })
        .where(eq(recordItems.id, id))
        .run();
      tx.delete(recordWindows).where(eq(recordWindows.itemId, id)).run();
      if (input.windows.length > 0) {
        tx.insert(recordWindows)
          .values(input.windows.map((window) => ({ ...window, itemId: id })))
          .run();
      }
      this.writeAudit(tx, {
        action: "ITEM_UPDATED",
        entityType: "ITEM",
        entityId: id,
        before,
        after: itemSnapshot({ ...current, ...withoutWindows(input) }, input.windows),
        createdAt: now,
      });
    });
    return this.getItem(id);
  }

  setItemsArchived(ids: number[], archived: boolean) {
    this.db.transaction((tx) => {
      const existing = tx.select().from(recordItems).where(inArray(recordItems.id, ids)).all();
      if (existing.length !== ids.length) throw new ApiError(404, "Record item not found.");
      const changed = existing.filter((item) => item.archived !== archived);
      if (changed.length === 0) return;

      const windows = tx
        .select()
        .from(recordWindows)
        .where(inArray(recordWindows.itemId, changed.map((item) => item.id)))
        .all();
      const windowsByItem = groupWindows(windows);
      const now = this.now();
      tx.update(recordItems)
        .set({ archived, updatedAt: now })
        .where(inArray(recordItems.id, changed.map((item) => item.id)))
        .run();
      for (const item of changed) {
        this.writeAudit(tx, {
          action: archived ? "ITEM_ARCHIVED" : "ITEM_RESTORED",
          entityType: "ITEM",
          entityId: item.id,
          before: itemSnapshot(item, windowsByItem.get(item.id) ?? []),
          after: itemSnapshot({ ...item, archived }, windowsByItem.get(item.id) ?? []),
          createdAt: now,
        });
      }
    });
    return ids.map((id) => this.getItem(id));
  }

  deleteArchivedItem(id: number) {
    const deleted = this.db.transaction((tx) => {
      const current = tx.select().from(recordItems).where(eq(recordItems.id, id)).get();
      if (!current) throw new ApiError(404, "Record item not found.");
      if (!current.archived) throw new ApiError(400, "Archive the record item before deleting it.");
      const windows = tx.select().from(recordWindows).where(eq(recordWindows.itemId, id)).all();
      const entryCount = tx
        .select({ value: count() })
        .from(recordEntries)
        .where(eq(recordEntries.itemId, id))
        .get()!.value;
      this.writeAudit(tx, {
        action: "ITEM_DELETED",
        entityType: "ITEM",
        entityId: id,
        before: { ...itemSnapshot(current, windows), entryCount },
        createdAt: this.now(),
      });
      tx.delete(recordItems).where(eq(recordItems.id, id)).run();
      return { ...current, windows };
    });
    return deleted;
  }

  listEntries(startDate: string, endDate: string, itemId?: number) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    const dateRange = and(gte(recordEntries.localDate, startDate), lte(recordEntries.localDate, endDate));
    const condition = itemId === undefined ? dateRange : and(dateRange, eq(recordEntries.itemId, itemId));
    return this.entryQuery().where(condition).orderBy(
      desc(recordEntries.occurredAt),
      desc(recordEntries.id),
    ).all();
  }

  getEntry(id: number) {
    const row = this.entryQuery().where(eq(recordEntries.id, id)).get();
    if (!row) throw new ApiError(404, "Record not found.");
    return row;
  }

  listLatestEntries() {
    const activeItems = this.db
      .select()
      .from(recordItems)
      .where(eq(recordItems.archived, false))
      .orderBy(sql`${recordItems.name} COLLATE NOCASE`)
      .all();
    return activeItems.map((item) => {
      const entry = this.db
        .select()
        .from(recordEntries)
        .where(eq(recordEntries.itemId, item.id))
        .orderBy(desc(recordEntries.occurredAt), desc(recordEntries.id))
        .limit(1)
        .get();
      return entry === undefined ? undefined : entryResponse(entry, item);
    }).filter((entry): entry is NonNullable<typeof entry> => entry !== undefined);
  }

  createEntry(input: RecordEntryInput) {
    const id = this.db.transaction((tx) => {
      const item = tx.select().from(recordItems).where(eq(recordItems.id, input.itemId)).get();
      if (!item) throw new ApiError(404, "Record item not found.");
      if (item.archived) throw new ApiError(400, "Archived items cannot be recorded.");
      this.requireEntryValue(item, input.value);
      const now = this.now();
      const created = tx
        .insert(recordEntries)
        .values({ ...input, createdAt: now })
        .returning({ id: recordEntries.id })
        .get();
      this.writeAudit(tx, {
        action: "ENTRY_CREATED",
        entityType: "ENTRY",
        entityId: created.id,
        after: entrySnapshot({ id: created.id, ...input, createdAt: now }, item.name),
        createdAt: now,
      });
      return created.id;
    });
    return this.getEntry(id);
  }

  updateEntry(id: number, input: RecordEntryUpdateInput) {
    this.db.transaction((tx) => {
      const current = tx.select().from(recordEntries).where(eq(recordEntries.id, id)).get();
      if (!current) throw new ApiError(404, "Record not found.");
      const item = tx.select().from(recordItems).where(eq(recordItems.id, current.itemId)).get();
      if (!item) throw new ApiError(404, "Record item not found.");
      if (item.archived) throw new ApiError(400, "Archived items cannot be recorded.");
      this.requireEntryValue(item, input.value);
      const now = this.now();
      tx.update(recordEntries).set(input).where(eq(recordEntries.id, id)).run();
      this.writeAudit(tx, {
        action: "ENTRY_UPDATED",
        entityType: "ENTRY",
        entityId: id,
        before: entrySnapshot(current, item.name),
        after: entrySnapshot({ ...current, ...input }, item.name),
        createdAt: now,
      });
    });
    return this.getEntry(id);
  }

  deleteEntry(id: number) {
    return this.db.transaction((tx) => {
      const entry = tx.select().from(recordEntries).where(eq(recordEntries.id, id)).get();
      if (!entry) throw new ApiError(404, "Record not found.");
      const item = tx.select().from(recordItems).where(eq(recordItems.id, entry.itemId)).get();
      if (!item) throw new ApiError(404, "Record item not found.");
      this.writeAudit(tx, {
        action: "ENTRY_DELETED",
        entityType: "ENTRY",
        entityId: id,
        before: entrySnapshot(entry, item.name),
        createdAt: this.now(),
      });
      tx.delete(recordEntries).where(eq(recordEntries.id, id)).run();
      return entryResponse(entry, item);
    });
  }

  deleteEntries(ids: number[]) {
    const deleted = this.db.transaction((tx) => {
      const existing = tx
        .select({ entry: recordEntries, item: recordItems })
        .from(recordEntries)
        .innerJoin(recordItems, eq(recordItems.id, recordEntries.itemId))
        .where(inArray(recordEntries.id, ids))
        .all();
      if (existing.length !== ids.length) throw new ApiError(404, "Record not found.");
      const now = this.now();
      tx.delete(recordEntries).where(inArray(recordEntries.id, ids)).run();
      for (const { entry, item } of existing) {
        this.writeAudit(tx, {
          action: "ENTRY_DELETED",
          entityType: "ENTRY",
          entityId: entry.id,
          before: entrySnapshot(entry, item.name),
          createdAt: now,
        });
      }
      return existing;
    });
    const byId = new Map(deleted.map(({ entry, item }) => [entry.id, entryResponse(entry, item)]));
    return ids.map((id) => byId.get(id)!);
  }

  listAuditLogs(beforeId?: number) {
    const query = this.db.select().from(recordAuditLogs);
    const rows = (beforeId === undefined ? query : query.where(lt(recordAuditLogs.id, beforeId)))
      .orderBy(desc(recordAuditLogs.id))
      .limit(AuditLogPageSize)
      .all();
    return {
      logs: rows.map((row) => ({
        ...row,
        before: row.beforeJson === null ? null : JSON.parse(row.beforeJson),
        after: row.afterJson === null ? null : JSON.parse(row.afterJson),
      })),
      nextBeforeId: rows.length === AuditLogPageSize ? rows.at(-1)!.id : null,
    };
  }

  private attachWindows(rows: RecordItemRow[]) {
    if (rows.length === 0) return [];
    const windows = this.db
      .select()
      .from(recordWindows)
      .where(inArray(recordWindows.itemId, rows.map((row) => row.id)))
      .orderBy(asc(recordWindows.startMinute), asc(recordWindows.endMinute), asc(recordWindows.id))
      .all();
    const grouped = groupWindows(windows);
    return rows.map((row) => ({ ...row, windows: grouped.get(row.id) ?? [] }));
  }

  private entryQuery() {
    return this.db
      .select({
        id: recordEntries.id,
        itemId: recordEntries.itemId,
        occurredAt: recordEntries.occurredAt,
        localDate: recordEntries.localDate,
        localMinute: recordEntries.localMinute,
        value: recordEntries.value,
        createdAt: recordEntries.createdAt,
        itemName: recordItems.name,
        itemMode: recordItems.mode,
        itemUnit: recordItems.unit,
        itemValueType: recordItems.valueType,
      })
      .from(recordEntries)
      .innerJoin(recordItems, eq(recordItems.id, recordEntries.itemId));
  }

  private writeAudit(tx: RecordsTransaction, audit: AuditWrite) {
    tx.insert(recordAuditLogs).values({
      action: audit.action,
      entityType: audit.entityType,
      entityId: audit.entityId,
      beforeJson: audit.before === undefined ? null : JSON.stringify(audit.before),
      afterJson: audit.after === undefined ? null : JSON.stringify(audit.after),
      createdAt: audit.createdAt,
    }).run();
  }

  private requireUniqueName(name: string, excludedId?: number) {
    const sameName = sql`lower(${recordItems.name}) = lower(${name}) COLLATE NOCASE`;
    const condition = excludedId === undefined ? sameName : and(sameName, ne(recordItems.id, excludedId));
    if (this.db.select({ id: recordItems.id }).from(recordItems).where(condition).get()) {
      throw new ApiError(409, "A record item with this name already exists.");
    }
  }

  private requireEntryValue(item: RecordItemRow, value: number) {
    if (!Number.isFinite(value)) throw new ApiError(400, "Value must be a finite number.");
    if (item.mode === "ACCUMULATE" && value <= 0) {
      throw new ApiError(400, "Value must be a positive number.");
    }
  }
}

function withoutWindows(input: RecordItemInput) {
  const { windows: _windows, ...item } = input;
  return item;
}

function groupWindows(windows: RecordWindowRow[]) {
  const grouped = new Map<number, RecordWindowRow[]>();
  for (const window of windows) {
    const itemWindows = grouped.get(window.itemId) ?? [];
    itemWindows.push(window);
    grouped.set(window.itemId, itemWindows);
  }
  return grouped;
}

function itemSnapshot(
  item: Pick<RecordItemRow, "id" | "name" | "description" | "mode" | "valueType" | "defaultValue" | "stepValue" | "unit" | "goalValue" | "goalPeriod" | "priority" | "archived">,
  windows: Array<Pick<RecordWindowRow, "startMinute" | "endMinute">> = [],
): ItemAuditSnapshot {
  return {
    ...item,
    windows: windows.map(({ startMinute, endMinute }) => ({ startMinute, endMinute })),
  };
}

function entrySnapshot(entry: RecordEntryRow, itemName: string): EntryAuditSnapshot {
  return {
    id: entry.id,
    itemId: entry.itemId,
    itemName,
    occurredAt: entry.occurredAt,
    localDate: entry.localDate,
    localMinute: entry.localMinute,
    value: entry.value,
  };
}

function entryResponse(entry: RecordEntryRow, item: RecordItemRow) {
  return {
    ...entry,
    itemName: item.name,
    itemMode: item.mode,
    itemUnit: item.unit,
    itemValueType: item.valueType,
  };
}
