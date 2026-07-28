import { isDeepStrictEqual } from "node:util";
import { and, asc, count, desc, eq, gte, inArray, lt, lte, ne, sql } from "drizzle-orm";
import type { RecordEntryInput, RecordItemInput, RecordMode, StepInput, TaskCreateInput, TaskTitleInput, TaskWriteInput } from "./domain.js";
import { ApiError, isMeasurementValueOnGrid } from "./domain.js";
import type { RecordsDatabase } from "./database.js";
import {
  recordAuditLogs,
  recordEntries,
  recordItems,
  recordWindows,
  todoSteps,
  todoStepWindows,
  todoTasks,
} from "./schema.js";

const AuditLogPageSize = 100;

type RecordItemRow = typeof recordItems.$inferSelect;
type RecordWindowRow = typeof recordWindows.$inferSelect;
type RecordEntryRow = typeof recordEntries.$inferSelect;
type TodoTaskRow = typeof todoTasks.$inferSelect;
type TodoStepWindowRow = typeof todoStepWindows.$inferSelect;
type TodoStepRow = typeof todoSteps.$inferSelect;
type RecordsTransaction = Parameters<Parameters<RecordsDatabase["transaction"]>[0]>[0];

type AuditAction =
  | "ITEM_CREATED"
  | "ITEM_UPDATED"
  | "ITEM_ARCHIVED"
  | "ITEM_RESTORED"
  | "ITEM_DELETED"
  | "ENTRY_CREATED"
  | "ENTRY_DELETED"
  | "TASK_CREATED"
  | "TASK_UPDATED"
  | "TASK_COMPLETED"
  | "TASK_REOPENED"
  | "TASK_DELETED"
  | "TASK_STEPS_REORDERED"
  | "STEP_CREATED"
  | "STEP_UPDATED"
  | "STEP_COMPLETED"
  | "STEP_REOPENED"
  | "STEP_DELETED";

interface ItemAuditSnapshot {
  id: number;
  name: string;
  description: string;
  mode: string;
  valueType: string;
  defaultValue: number | null;
  minValue: number | null;
  maxValue: number | null;
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

interface TaskAuditSnapshot {
  id: number;
  title: string;
  plannedDate: string | null;
  completedAt: number | null;
  stepCount?: number;
  stepIds?: number[];
}

interface StepAuditSnapshot {
  id: number;
  taskId: number;
  taskTitle: string;
  title: string;
  completedAt: number | null;
  windows: Array<{ startMinute: number; endMinute: number }>;
}

type AuditSnapshot = ItemAuditSnapshot | EntryAuditSnapshot | TaskAuditSnapshot | StepAuditSnapshot;

interface AuditWrite {
  action: AuditAction;
  entityType: "ITEM" | "ENTRY" | "TASK" | "STEP";
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

  overview(startDate: string, endDate: string) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    const items = this.listItems(false);
    const tasks = this.listTasks();
    const accumulateEntries = this.listEntries(startDate, endDate, undefined, "ACCUMULATE");
    return {
      items,
      tasks,
      accumulateEntries,
      latestMeasurementEntries: this.listLatestEntries(),
    };
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
      const before = itemSnapshot(
        current,
        tx.select().from(recordWindows).where(eq(recordWindows.itemId, id)).all(),
      );
      const after = itemSnapshot({ ...current, ...withoutWindows(input) }, input.windows);
      if (isDeepStrictEqual(before, after)) return;

      const entryTotal = tx
        .select({ value: count() })
        .from(recordEntries)
        .where(eq(recordEntries.itemId, id))
        .get()!.value;
      if (entryTotal > 0 && (
        current.mode !== input.mode
        || current.valueType !== input.valueType
        || current.unit !== input.unit
        || (current.mode === "MEASUREMENT" && (
          current.minValue !== input.minValue
          || current.maxValue !== input.maxValue
          || current.stepValue !== input.stepValue
        ))
      )) {
        throw new ApiError(400, "Recorded item mode, type, unit, and measurement range cannot change.");
      }

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
        after,
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

  listEntries(startDate: string, endDate: string, itemId?: number, mode?: RecordMode) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    const dateRange = and(gte(recordEntries.localDate, startDate), lte(recordEntries.localDate, endDate));
    const itemCondition = itemId === undefined ? dateRange : and(dateRange, eq(recordEntries.itemId, itemId));
    const condition = mode === undefined ? itemCondition : and(itemCondition, eq(recordItems.mode, mode));
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
    const rows = this.db
      .select()
      .from(recordItems)
      .where(and(eq(recordItems.archived, false), eq(recordItems.mode, "MEASUREMENT")))
      .orderBy(sql`${recordItems.name} COLLATE NOCASE`)
      .all();
    if (rows.length === 0) return [];
    const itemsById = new Map(rows.map((item) => [item.id, item]));
    const itemIds = sql.join(rows.map((item) => sql`${item.id}`), sql`, `);
    const entries = this.db.all<RecordEntryRow>(sql`
      SELECT entry.id, entry.item_id AS itemId, entry.occurred_at AS occurredAt,
             entry.local_date AS localDate, entry.local_minute AS localMinute,
             entry.value, entry.created_at AS createdAt
      FROM record_entries AS entry
      WHERE entry.item_id IN (${itemIds})
        AND NOT EXISTS (
          SELECT 1
          FROM record_entries AS newer
          WHERE newer.item_id = entry.item_id
            AND (
              newer.occurred_at > entry.occurred_at
              OR (newer.occurred_at = entry.occurred_at AND newer.id > entry.id)
            )
        )
    `);
    return entries
      .map((entry) => entryResponse(entry, itemsById.get(entry.itemId)!))
      .sort((left, right) => left.itemName.localeCompare(right.itemName));
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

  listTasks() {
    const tasks = this.db
      .select()
      .from(todoTasks)
      .orderBy(asc(todoTasks.createdAt), asc(todoTasks.id))
      .all();
    return this.attachSteps(tasks);
  }

  getTask(id: number) {
    const task = this.db.select().from(todoTasks).where(eq(todoTasks.id, id)).get();
    if (!task) throw new ApiError(404, "Task not found.");
    return this.attachSteps([task])[0]!;
  }

  getStep(id: number) {
    const step = this.db.select().from(todoSteps).where(eq(todoSteps.id, id)).get();
    if (!step) throw new ApiError(404, "Step not found.");
    return { ...step, windows: this.stepWindows([id]).get(id) ?? [] };
  }

  createTask(input: TaskCreateInput) {
    const id = this.db.transaction((tx) => {
      const now = this.now();
      const task = tx
        .insert(todoTasks)
        .values({ title: input.title, plannedDate: input.plannedDate, completedAt: null, createdAt: now, updatedAt: now })
        .returning({ id: todoTasks.id })
        .get();
      const steps = tx.insert(todoSteps)
        .values(input.steps.map((step, position) => ({
          taskId: task.id,
          title: step.title,
          position,
          completedAt: null,
          createdAt: now,
          updatedAt: now,
        })))
        .returning({ id: todoSteps.id, title: todoSteps.title })
        .all();
      const stepWindows = input.steps.flatMap((step, position) =>
        step.windows.map((window) => ({ ...window, stepId: steps[position]!.id })),
      );
      if (stepWindows.length > 0) {
        tx.insert(todoStepWindows).values(stepWindows).run();
      }
      this.writeAudit(tx, {
        action: "TASK_CREATED",
        entityType: "TASK",
        entityId: task.id,
        after: {
          ...taskSnapshot({ id: task.id, title: input.title, plannedDate: input.plannedDate, completedAt: null }),
          stepCount: steps.length,
        },
        createdAt: now,
      });
      steps.forEach((step, position) => {
        this.writeAudit(tx, {
          action: "STEP_CREATED",
          entityType: "STEP",
          entityId: step.id,
          after: stepSnapshot(
            { id: step.id, taskId: task.id, title: step.title, completedAt: null },
            input.title,
            input.steps[position]!.windows,
          ),
          createdAt: now,
        });
      });
      return task.id;
    });
    return this.getTask(id);
  }

  updateTask(id: number, input: TaskWriteInput) {
    this.db.transaction((tx) => {
      const current = this.requireTask(tx, id);
      if (input.plannedDate === null) this.requireTaskWithoutSuggestedTimes(tx, id);
      const now = this.now();
      const updated = { ...current, title: input.title, plannedDate: input.plannedDate, updatedAt: now };
      tx.update(todoTasks).set({ title: input.title, plannedDate: input.plannedDate, updatedAt: now }).where(eq(todoTasks.id, id)).run();
      this.writeAudit(tx, {
        action: "TASK_UPDATED",
        entityType: "TASK",
        entityId: id,
        before: taskSnapshot(current),
        after: taskSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getTask(id);
  }

  deleteTask(id: number) {
    return this.db.transaction((tx) => {
      const task = this.requireTask(tx, id);
      const stepCount = tx
        .select({ value: count() })
        .from(todoSteps)
        .where(eq(todoSteps.taskId, id))
        .get()!.value;
      this.writeAudit(tx, {
        action: "TASK_DELETED",
        entityType: "TASK",
        entityId: id,
        before: { ...taskSnapshot(task), stepCount },
        createdAt: this.now(),
      });
      tx.delete(todoTasks).where(eq(todoTasks.id, id)).run();
      return task;
    });
  }

  createStep(taskId: number, input: StepInput) {
    const id = this.db.transaction((tx) => {
      const task = this.requireTask(tx, taskId);
      this.requireTaskDateForSuggestedTimes(task, input.windows);
      const now = this.now();
      const existing = tx.select().from(todoSteps).where(eq(todoSteps.taskId, taskId)).all();
      const position = existing.reduce((max, step) => Math.max(max, step.position), -1) + 1;
      const step = tx
        .insert(todoSteps)
        .values({ taskId, title: input.title, position, completedAt: null, createdAt: now, updatedAt: now })
        .returning({ id: todoSteps.id })
        .get();
      if (input.windows.length > 0) {
        tx.insert(todoStepWindows)
          .values(input.windows.map((window) => ({ ...window, stepId: step.id })))
          .run();
      }
      this.writeAudit(tx, {
        action: "STEP_CREATED",
        entityType: "STEP",
        entityId: step.id,
        after: stepSnapshot(
          { id: step.id, taskId, title: input.title, completedAt: null },
          task.title,
          input.windows,
        ),
        createdAt: now,
      });
      this.setTaskCompletion(tx, task, false, now);
      return step.id;
    });
    return this.getStep(id);
  }

  updateStep(id: number, input: StepInput) {
    this.db.transaction((tx) => {
      const step = this.requireStep(tx, id);
      const task = this.requireTask(tx, step.taskId);
      this.requireTaskDateForSuggestedTimes(task, input.windows);
      const now = this.now();
      const currentWindows = tx.select().from(todoStepWindows).where(eq(todoStepWindows.stepId, id)).all();
      const updated = { ...step, title: input.title, updatedAt: now };
      tx.update(todoSteps).set({ title: input.title, updatedAt: now }).where(eq(todoSteps.id, id)).run();
      tx.delete(todoStepWindows).where(eq(todoStepWindows.stepId, id)).run();
      if (input.windows.length > 0) {
        tx.insert(todoStepWindows)
          .values(input.windows.map((window) => ({ ...window, stepId: id })))
          .run();
      }
      this.writeAudit(tx, {
        action: "STEP_UPDATED",
        entityType: "STEP",
        entityId: id,
        before: stepSnapshot(step, task.title, currentWindows),
        after: stepSnapshot(updated, task.title, input.windows),
        createdAt: now,
      });
    });
    return this.getStep(id);
  }

  setStepCompleted(id: number, completed: boolean) {
    this.db.transaction((tx) => {
      const step = this.requireStep(tx, id);
      const task = this.requireTask(tx, step.taskId);
      const now = this.now();
      if ((step.completedAt !== null) !== completed) {
        const updated = { ...step, completedAt: completed ? now : null, updatedAt: now };
        tx.update(todoSteps)
          .set({ completedAt: updated.completedAt, updatedAt: now })
          .where(eq(todoSteps.id, id))
          .run();
        this.writeAudit(tx, {
          action: completed ? "STEP_COMPLETED" : "STEP_REOPENED",
          entityType: "STEP",
          entityId: id,
          before: stepSnapshot(step, task.title),
          after: stepSnapshot(updated, task.title),
          createdAt: now,
        });
      }
      this.reconcileTaskCompletion(tx, task, now);
    });
    return this.getStep(id);
  }

  deleteStep(id: number) {
    return this.db.transaction((tx) => {
      const step = this.requireStep(tx, id);
      const task = this.requireTask(tx, step.taskId);
      const stepCount = tx
        .select({ value: count() })
        .from(todoSteps)
        .where(eq(todoSteps.taskId, task.id))
        .get()!.value;
      if (stepCount <= 1) throw new ApiError(400, "Tasks must keep at least one subtask.");
      const now = this.now();
      this.writeAudit(tx, {
        action: "STEP_DELETED",
        entityType: "STEP",
        entityId: id,
        before: stepSnapshot(step, task.title),
        createdAt: now,
      });
      tx.delete(todoSteps).where(eq(todoSteps.id, id)).run();
      const remaining = tx.select().from(todoSteps).where(eq(todoSteps.taskId, task.id)).all();
      this.setTaskCompletion(tx, task, remaining.every((candidate) => candidate.completedAt !== null), now);
      return step;
    });
  }

  reorderSteps(taskId: number, stepIds: number[]) {
    this.db.transaction((tx) => {
      const task = this.requireTask(tx, taskId);
      const current = tx
        .select()
        .from(todoSteps)
        .where(eq(todoSteps.taskId, taskId))
        .orderBy(asc(todoSteps.position), asc(todoSteps.id))
        .all();
      if (current.length === 0) throw new ApiError(500, "Task must have at least one subtask.");
      if (stepIds.length !== current.length) {
        throw new ApiError(400, "Step order must include every subtask exactly once.");
      }
      const currentIds = new Set(current.map((step) => step.id));
      for (const id of stepIds) {
        if (!currentIds.has(id)) throw new ApiError(400, "Step does not belong to this task.");
      }
      if (new Set(stepIds).size !== stepIds.length) {
        throw new ApiError(400, "Step order must include every subtask exactly once.");
      }
      const now = this.now();
      const before = current.map((step) => step.id);
      stepIds.forEach((id, position) => {
        tx.update(todoSteps)
          .set({ position, updatedAt: now })
          .where(eq(todoSteps.id, id))
          .run();
      });
      tx.update(todoTasks).set({ updatedAt: now }).where(eq(todoTasks.id, taskId)).run();
      this.writeAudit(tx, {
        action: "TASK_STEPS_REORDERED",
        entityType: "TASK",
        entityId: taskId,
        before: { ...taskSnapshot(task), stepIds: before },
        after: { ...taskSnapshot(task), stepIds },
        createdAt: now,
      });
    });
    return this.getTask(taskId);
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

  /** Suggested windows for the given steps, oldest start first. */
  private stepWindows(stepIds: number[]) {
    const grouped = new Map<number, TodoStepWindowRow[]>();
    if (stepIds.length === 0) return grouped;
    const windows = this.db
      .select()
      .from(todoStepWindows)
      .where(inArray(todoStepWindows.stepId, stepIds))
      .orderBy(asc(todoStepWindows.startMinute), asc(todoStepWindows.endMinute), asc(todoStepWindows.id))
      .all();
    for (const window of windows) {
      const stepWindows = grouped.get(window.stepId) ?? [];
      stepWindows.push(window);
      grouped.set(window.stepId, stepWindows);
    }
    return grouped;
  }

  private attachSteps(tasks: TodoTaskRow[]) {
    if (tasks.length === 0) return [];
    const taskIds = tasks.map((task) => task.id);
    const steps = this.db
      .select()
      .from(todoSteps)
      .where(inArray(todoSteps.taskId, taskIds))
      .orderBy(asc(todoSteps.taskId), asc(todoSteps.position), asc(todoSteps.id))
      .all();
    const stepsByTask = new Map<number, TodoStepRow[]>();
    for (const step of steps) {
      const taskSteps = stepsByTask.get(step.taskId) ?? [];
      taskSteps.push(step);
      stepsByTask.set(step.taskId, taskSteps);
    }
    const windowsByStep = this.stepWindows(steps.map((step) => step.id));
    return tasks.map((task) => ({
      ...task,
      steps: (stepsByTask.get(task.id) ?? []).map((step) => ({
        ...step,
        windows: windowsByStep.get(step.id) ?? [],
      })),
    }));
  }

  private requireTask(tx: RecordsTransaction, id: number) {
    const task = tx.select().from(todoTasks).where(eq(todoTasks.id, id)).get();
    if (!task) throw new ApiError(404, "Task not found.");
    return task;
  }

  private requireStep(tx: RecordsTransaction, id: number) {
    const step = tx.select().from(todoSteps).where(eq(todoSteps.id, id)).get();
    if (!step) throw new ApiError(404, "Step not found.");
    return step;
  }

  private requireTaskDateForSuggestedTimes(task: TodoTaskRow, windows: StepInput["windows"]) {
    if (task.plannedDate === null && windows.length > 0) {
      throw new ApiError(400, "Suggested times require a task date.");
    }
  }

  private requireTaskWithoutSuggestedTimes(tx: RecordsTransaction, taskId: number) {
    const stepIds = tx
      .select({ id: todoSteps.id })
      .from(todoSteps)
      .where(eq(todoSteps.taskId, taskId))
      .all()
      .map((step) => step.id);
    if (stepIds.length === 0) return;
    const hasSuggestedTimes = tx
      .select({ id: todoStepWindows.id })
      .from(todoStepWindows)
      .where(inArray(todoStepWindows.stepId, stepIds))
      .get() !== undefined;
    if (hasSuggestedTimes) throw new ApiError(400, "Remove suggested times before moving a task to Later.");
  }

  private reconcileTaskCompletion(tx: RecordsTransaction, task: TodoTaskRow, now: number) {
    const steps = tx.select().from(todoSteps).where(eq(todoSteps.taskId, task.id)).all();
    if (steps.length === 0) throw new ApiError(500, "Task must have at least one subtask.");
    this.setTaskCompletion(tx, task, steps.every((step) => step.completedAt !== null), now);
  }

  private setTaskCompletion(
    tx: RecordsTransaction,
    task: TodoTaskRow,
    completed: boolean,
    now: number,
  ) {
    if ((task.completedAt !== null) === completed) return task;
    const updated = { ...task, completedAt: completed ? now : null, updatedAt: now };
    tx.update(todoTasks)
      .set({ completedAt: updated.completedAt, updatedAt: now })
      .where(eq(todoTasks.id, task.id))
      .run();
    this.writeAudit(tx, {
      action: completed ? "TASK_COMPLETED" : "TASK_REOPENED",
      entityType: "TASK",
      entityId: task.id,
      before: taskSnapshot(task),
      after: taskSnapshot(updated),
      createdAt: now,
    });
    return updated;
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
    if (item.mode === "MEASUREMENT") {
      if (
        item.minValue === null
        || item.maxValue === null
        || !isMeasurementValueOnGrid(value, item.minValue, item.maxValue, item.stepValue)
      ) {
        throw new ApiError(400, "Value must be within the configured measurement range and step.");
      }
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
  item: Pick<RecordItemRow, "id" | "name" | "description" | "mode" | "valueType" | "defaultValue" | "minValue" | "maxValue" | "stepValue" | "unit" | "goalValue" | "goalPeriod" | "priority" | "archived">,
  windows: Array<Pick<RecordWindowRow, "startMinute" | "endMinute">> = [],
): ItemAuditSnapshot {
  return {
    ...item,
    windows: windows
      .map(({ startMinute, endMinute }) => ({ startMinute, endMinute }))
      .sort((left, right) => left.startMinute - right.startMinute || left.endMinute - right.endMinute),
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

function taskSnapshot(task: { id: number; title: string; plannedDate: string | null; completedAt: number | null }): TaskAuditSnapshot {
  return {
    id: task.id,
    title: task.title,
    plannedDate: task.plannedDate,
    completedAt: task.completedAt,
  };
}

function sortedWindows(windows: Array<Pick<TodoStepWindowRow, "startMinute" | "endMinute">>) {
  return windows
    .map(({ startMinute, endMinute }) => ({ startMinute, endMinute }))
    .sort((left, right) => left.startMinute - right.startMinute || left.endMinute - right.endMinute);
}

function stepSnapshot(
  step: Pick<TodoStepRow, "id" | "taskId" | "title" | "completedAt">,
  taskTitle: string,
  windows: Array<Pick<TodoStepWindowRow, "startMinute" | "endMinute">> = [],
): StepAuditSnapshot {
  return {
    id: step.id,
    taskId: step.taskId,
    taskTitle,
    title: step.title,
    completedAt: step.completedAt,
    windows: sortedWindows(windows),
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
