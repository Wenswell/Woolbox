import { and, asc, count, eq, gte, inArray, lte, ne, sql } from "drizzle-orm";
import type { RecordEntryInput, RecordItemInput } from "./domain.js";
import { ApiError } from "./domain.js";
import type { RecordsDatabase } from "./database.js";
import { recordEntries, recordItems, recordWindows } from "./schema.js";

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
      if (entryTotal > 0 && (current.valueType !== input.valueType || current.unit !== input.unit)) {
        throw new ApiError(400, "Type and unit cannot change after records exist.");
      }

      tx.update(recordItems)
        .set({ ...withoutWindows(input), updatedAt: this.now() })
        .where(eq(recordItems.id, id))
        .run();
      tx.delete(recordWindows).where(eq(recordWindows.itemId, id)).run();
      if (input.windows.length > 0) {
        tx.insert(recordWindows)
          .values(input.windows.map((window) => ({ ...window, itemId: id })))
          .run();
      }
    });
    return this.getItem(id);
  }

  setItemsArchived(ids: number[], archived: boolean) {
    this.db.transaction((tx) => {
      const existing = tx
        .select({ id: recordItems.id })
        .from(recordItems)
        .where(inArray(recordItems.id, ids))
        .all();
      if (existing.length !== ids.length) throw new ApiError(404, "Record item not found.");
      tx.update(recordItems)
        .set({ archived, updatedAt: this.now() })
        .where(inArray(recordItems.id, ids))
        .run();
    });
    return ids.map((id) => this.getItem(id));
  }

  deleteArchivedItem(id: number) {
    const deleted = this.getItem(id);
    this.db.transaction((tx) => {
      const current = tx.select().from(recordItems).where(eq(recordItems.id, id)).get();
      if (!current) throw new ApiError(404, "Record item not found.");
      if (!current.archived) throw new ApiError(400, "Archive the record item before deleting it.");
      tx.delete(recordItems).where(eq(recordItems.id, id)).run();
    });
    return deleted;
  }

  listEntries(startDate: string, endDate: string, itemId?: number) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    const dateRange = and(gte(recordEntries.localDate, startDate), lte(recordEntries.localDate, endDate));
    const condition = itemId === undefined ? dateRange : and(dateRange, eq(recordEntries.itemId, itemId));
    return this.entryQuery().where(condition).orderBy(
      asc(recordEntries.localDate),
      asc(recordEntries.localMinute),
      asc(recordEntries.createdAt),
      asc(recordEntries.id),
    ).all();
  }

  getEntry(id: number) {
    const row = this.entryQuery().where(eq(recordEntries.id, id)).get();
    if (!row) throw new ApiError(404, "Record not found.");
    return row;
  }

  createEntry(input: RecordEntryInput) {
    const id = this.db.transaction((tx) => {
      const item = tx.select().from(recordItems).where(eq(recordItems.id, input.itemId)).get();
      if (!item) throw new ApiError(404, "Record item not found.");
      if (item.archived) throw new ApiError(400, "Archived items cannot be recorded.");
      return tx
        .insert(recordEntries)
        .values({ ...input, createdAt: this.now() })
        .returning({ id: recordEntries.id })
        .get().id;
    });
    return this.getEntry(id);
  }

  updateEntry(id: number, input: RecordEntryInput) {
    this.db.transaction((tx) => {
      const current = tx.select().from(recordEntries).where(eq(recordEntries.id, id)).get();
      if (!current) throw new ApiError(404, "Record not found.");
      const item = tx.select().from(recordItems).where(eq(recordItems.id, input.itemId)).get();
      if (!item) throw new ApiError(404, "Record item not found.");
      tx.update(recordEntries).set(input).where(eq(recordEntries.id, id)).run();
    });
    return this.getEntry(id);
  }

  deleteEntry(id: number) {
    const deleted = this.getEntry(id);
    this.db.delete(recordEntries).where(eq(recordEntries.id, id)).run();
    return deleted;
  }

  deleteEntries(ids: number[]) {
    const deleted = this.db.transaction((tx) => {
      const existing = this.entryQuery().where(inArray(recordEntries.id, ids)).all();
      if (existing.length !== ids.length) throw new ApiError(404, "Record not found.");
      tx.delete(recordEntries).where(inArray(recordEntries.id, ids)).run();
      return existing;
    });
    const byId = new Map(deleted.map((entry) => [entry.id, entry]));
    return ids.map((id) => byId.get(id)!);
  }

  private attachWindows(rows: Array<typeof recordItems.$inferSelect>) {
    if (rows.length === 0) return [];
    const windows = this.db
      .select()
      .from(recordWindows)
      .where(inArray(recordWindows.itemId, rows.map((row) => row.id)))
      .orderBy(asc(recordWindows.startMinute), asc(recordWindows.endMinute), asc(recordWindows.id))
      .all();
    const grouped = new Map<number, typeof windows>();
    for (const window of windows) {
      const itemWindows = grouped.get(window.itemId) ?? [];
      itemWindows.push(window);
      grouped.set(window.itemId, itemWindows);
    }
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
        itemUnit: recordItems.unit,
        itemValueType: recordItems.valueType,
      })
      .from(recordEntries)
      .innerJoin(recordItems, eq(recordItems.id, recordEntries.itemId));
  }

  private requireUniqueName(name: string, excludedId?: number) {
    const sameName = sql`lower(${recordItems.name}) = lower(${name}) COLLATE NOCASE`;
    const condition = excludedId === undefined ? sameName : and(sameName, ne(recordItems.id, excludedId));
    if (this.db.select({ id: recordItems.id }).from(recordItems).where(condition).get()) {
      throw new ApiError(409, "A record item with this name already exists.");
    }
  }
}

function withoutWindows(input: RecordItemInput) {
  const { windows: _windows, ...item } = input;
  return item;
}
