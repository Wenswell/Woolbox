import { isDeepStrictEqual } from "node:util";
import { and, asc, count, desc, eq, gte, inArray, isNull, lt, lte, ne, or, sql } from "drizzle-orm";
import type {
  DailyPlanCreateInput,
  DailyPlanReviewInput,
  DailyPlanTransitionInput,
  DailyPlanUpdateInput,
  RecordEntryInput,
  RecordItemInput,
  RecordMode,
  DatedStepInput,
  TaskCreateInput,
  TaskRepeatMode,
  TaskWriteInput,
} from "./domain.js";
import { ApiError, isMeasurementValueOnGrid } from "./domain.js";
import type { RecordsDatabase } from "./database.js";
import {
  dailyPlans,
  recordAuditLogs,
  recordEntries,
  recordItems,
  todoStepCompletions,
  todoSteps,
  todoTasks,
} from "./schema.js";

const AuditLogPageSize = 100;
const ReviewIntervalMs = 60 * 60 * 1_000;

type RecordItemRow = typeof recordItems.$inferSelect;
type RecordEntryRow = typeof recordEntries.$inferSelect;
type TodoTaskRow = typeof todoTasks.$inferSelect;
type TodoStepRow = typeof todoSteps.$inferSelect;
type DailyPlanRow = typeof dailyPlans.$inferSelect;
type RecordsTransaction = Parameters<Parameters<RecordsDatabase["transaction"]>[0]>[0];
type RecordsReader = Pick<RecordsTransaction, "select">;

interface TodoStepResponse extends TodoStepRow {
  completedAt: number | null;
}

interface TodoTaskResponse extends TodoTaskRow {
  completedAt: number | null;
  steps: TodoStepResponse[];
}

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
  | "STEP_DELETED"
  | "DAILY_PLAN_CREATED"
  | "DAILY_PLAN_UPDATED"
  | "DAILY_PLAN_ENDED";

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
  repeatMode: TaskRepeatMode;
  localDate: string;
  completedAt: number | null;
  stepCount?: number;
  stepIds?: number[];
}

interface StepAuditSnapshot {
  id: number;
  taskId: number;
  taskTitle: string;
  repeatMode: TaskRepeatMode;
  localDate: string;
  title: string;
  completedAt: number | null;
}

interface DailyPlanAuditSnapshot {
  id: number;
  localDate: string;
  firstMove: string;
  primaryTaskId: number | null;
  secondaryTaskId: number | null;
  currentTaskId: number | null;
  state: string;
  completedRounds: number;
  nextReviewAt: number | null;
}

type AuditSnapshot = ItemAuditSnapshot | EntryAuditSnapshot | TaskAuditSnapshot | StepAuditSnapshot | DailyPlanAuditSnapshot;

interface AuditWrite {
  action: AuditAction;
  entityType: "ITEM" | "ENTRY" | "TASK" | "STEP" | "DAILY_PLAN";
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
    return rows;
  }

  overview(startDate: string, endDate: string, planDate: string) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    const items = this.listItems(false);
    const tasks = this.listTasks(planDate);
    const accumulateEntries = this.listEntries(startDate, endDate, undefined, "ACCUMULATE");
    return {
      items,
      tasks,
      dailyPlan: this.getDailyPlan(planDate),
      accumulateEntries,
      latestMeasurementEntries: this.listLatestEntries(),
    };
  }

  getItem(id: number) {
    const row = this.db.select().from(recordItems).where(eq(recordItems.id, id)).get();
    if (!row) throw new ApiError(404, "Record item not found.");
    return row;
  }

  createItem(input: RecordItemInput) {
    const id = this.db.transaction((tx) => {
      this.requireUniqueName(input.name);
      const now = this.now();
      const created = tx
        .insert(recordItems)
        .values({ ...input, archived: false, createdAt: now, updatedAt: now })
        .returning({ id: recordItems.id })
        .get();
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
      const before = itemSnapshot(current);
      const after = itemSnapshot({ ...current, ...input });
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
        .set({ ...input, updatedAt: now })
        .where(eq(recordItems.id, id))
        .run();
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
          before: itemSnapshot(item),
          after: itemSnapshot({ ...item, archived }),
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
      const entryCount = tx
        .select({ value: count() })
        .from(recordEntries)
        .where(eq(recordEntries.itemId, id))
        .get()!.value;
      this.writeAudit(tx, {
        action: "ITEM_DELETED",
        entityType: "ITEM",
        entityId: id,
        before: { ...itemSnapshot(current), entryCount },
        createdAt: this.now(),
      });
      tx.delete(recordItems).where(eq(recordItems.id, id)).run();
      return current;
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

  listTasks(localDate: string) {
    const tasks = this.db
      .select()
      .from(todoTasks)
      .orderBy(asc(todoTasks.createdAt), asc(todoTasks.id))
      .all();
    return this.attachSteps(this.db, tasks, localDate);
  }

  getTask(id: number, localDate: string) {
    const task = this.db.select().from(todoTasks).where(eq(todoTasks.id, id)).get();
    if (!task) throw new ApiError(404, "Task not found.");
    return this.attachSteps(this.db, [task], localDate)[0]!;
  }

  getStep(id: number, localDate: string) {
    const step = this.db.select().from(todoSteps).where(eq(todoSteps.id, id)).get();
    if (!step) throw new ApiError(404, "Step not found.");
    return this.getTask(step.taskId, localDate).steps.find((candidate) => candidate.id === id)!;
  }

  getDailyPlan(localDate: string) {
    return this.db.select().from(dailyPlans).where(eq(dailyPlans.localDate, localDate)).get() ?? null;
  }

  createDailyPlan(input: DailyPlanCreateInput) {
    const id = this.db.transaction((tx) => {
      if (tx.select({ id: dailyPlans.id }).from(dailyPlans).where(eq(dailyPlans.localDate, input.localDate)).get()) {
        throw new ApiError(409, "A plan already exists for this date.");
      }
      this.requireOpenTask(tx, input.primaryTaskId, "Primary task", input.localDate);
      if (input.secondaryTaskId !== null) {
        this.requireOpenTask(tx, input.secondaryTaskId, "Secondary task", input.localDate);
      }
      const now = this.now();
      const created = tx.insert(dailyPlans).values({
        localDate: input.localDate,
        firstMove: input.firstMove,
        firstMoveCompletedAt: null,
        primaryTaskId: input.primaryTaskId,
        secondaryTaskId: input.secondaryTaskId,
        currentTaskId: null,
        state: "READY",
        completedRounds: 0,
        nextReviewAt: null,
        revision: 0,
        endedAt: null,
        createdAt: now,
        updatedAt: now,
      }).returning({ id: dailyPlans.id }).get();
      const plan = {
        id: created.id,
        ...input,
        currentTaskId: null,
        state: "READY" as const,
        completedRounds: 0,
        nextReviewAt: null,
      };
      this.writeAudit(tx, {
        action: "DAILY_PLAN_CREATED",
        entityType: "DAILY_PLAN",
        entityId: created.id,
        after: dailyPlanSnapshot(plan),
        createdAt: now,
      });
      return created.id;
    });
    return this.requireDailyPlanById(id);
  }

  updateDailyPlan(localDate: string, input: DailyPlanUpdateInput) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      if (plan.state !== "READY" && plan.state !== "ACTIVE") {
        throw new ApiError(409, "Only ready or active plans can change commitments.");
      }
      if (plan.state !== "READY" && input.firstMove !== plan.firstMove) {
        throw new ApiError(409, "The first move cannot change after the plan starts.");
      }
      this.requireReplaceableSlot(tx, plan.primaryTaskId, input.primaryTaskId, "Primary task", localDate);
      this.requireReplaceableSlot(tx, plan.secondaryTaskId, input.secondaryTaskId, "Secondary task", localDate);

      if (
        input.firstMove === plan.firstMove
        && input.primaryTaskId === plan.primaryTaskId
        && input.secondaryTaskId === plan.secondaryTaskId
      ) return;

      let currentTaskId = plan.currentTaskId;
      let nextReviewAt = plan.nextReviewAt;
      if (
        plan.state === "ACTIVE"
        && !this.isOpenCommitment(tx, plan.currentTaskId, input.primaryTaskId, input.secondaryTaskId, localDate)
      ) {
        currentTaskId = this.firstOpenCommitment(tx, input.primaryTaskId, input.secondaryTaskId, localDate);
        nextReviewAt = input.occurredAt + ReviewIntervalMs;
      }
      const now = this.now();
      const updated: DailyPlanRow = {
        ...plan,
        firstMove: input.firstMove,
        primaryTaskId: input.primaryTaskId,
        secondaryTaskId: input.secondaryTaskId,
        currentTaskId,
        nextReviewAt,
        revision: plan.revision + 1,
        updatedAt: now,
      };
      tx.update(dailyPlans).set({
        firstMove: updated.firstMove,
        primaryTaskId: updated.primaryTaskId,
        secondaryTaskId: updated.secondaryTaskId,
        currentTaskId: updated.currentTaskId,
        nextReviewAt: updated.nextReviewAt,
        revision: updated.revision,
        updatedAt: now,
      }).where(eq(dailyPlans.id, plan.id)).run();
      this.writeAudit(tx, {
        action: "DAILY_PLAN_UPDATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(plan),
        after: dailyPlanSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
  }

  startDailyPlan(localDate: string, input: DailyPlanTransitionInput) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "READY") throw new ApiError(409, "Only ready plans can start.");
      const currentTaskId = this.firstOpenCommitment(tx, plan.primaryTaskId, plan.secondaryTaskId, localDate);
      return {
        firstMoveCompletedAt: input.occurredAt,
        currentTaskId,
        state: currentTaskId === null ? "ENOUGH" as const : "ACTIVE" as const,
        nextReviewAt: currentTaskId === null ? null : input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  reviewDailyPlan(localDate: string, input: DailyPlanReviewInput) {
    return this.completeDailyPlanRound(localDate, input, true);
  }

  reviewDailyPlanNow(localDate: string, input: DailyPlanReviewInput) {
    return this.completeDailyPlanRound(localDate, input, false);
  }

  switchDailyPlanTask(localDate: string, input: DailyPlanReviewInput) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      if (plan.state !== "ACTIVE") throw new ApiError(409, "Only active plans can switch the current task.");
      if (input.currentTaskId === plan.currentTaskId) throw new ApiError(409, "Choose the other unfinished commitment.");
      if (!this.isOpenCommitment(tx, input.currentTaskId, plan.primaryTaskId, plan.secondaryTaskId, localDate)) {
        throw new ApiError(409, "Current task must be an unfinished commitment.");
      }
      const now = this.now();
      const updated: DailyPlanRow = {
        ...plan,
        currentTaskId: input.currentTaskId,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
        revision: plan.revision + 1,
        updatedAt: now,
      };
      tx.update(dailyPlans).set({
        currentTaskId: updated.currentTaskId,
        nextReviewAt: updated.nextReviewAt,
        revision: updated.revision,
        updatedAt: now,
      }).where(eq(dailyPlans.id, plan.id)).run();
      this.writeAudit(tx, {
        action: "DAILY_PLAN_UPDATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(plan),
        after: dailyPlanSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
  }

  resetDailyPlan(localDate: string, input: DailyPlanTransitionInput) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      if (plan.state === "READY") throw new ApiError(409, "The plan is already ready.");
      const now = this.now();
      const updated: DailyPlanRow = {
        ...plan,
        firstMoveCompletedAt: null,
        currentTaskId: null,
        state: "READY",
        completedRounds: 0,
        nextReviewAt: null,
        endedAt: null,
        revision: plan.revision + 1,
        updatedAt: now,
      };
      tx.update(dailyPlans).set({
        firstMoveCompletedAt: null,
        currentTaskId: null,
        state: updated.state,
        completedRounds: 0,
        nextReviewAt: null,
        endedAt: null,
        revision: updated.revision,
        updatedAt: now,
      }).where(eq(dailyPlans.id, plan.id)).run();
      this.writeAudit(tx, {
        action: "DAILY_PLAN_UPDATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(plan),
        after: dailyPlanSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
  }

  private completeDailyPlanRound(localDate: string, input: DailyPlanReviewInput, requireDue: boolean) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "ACTIVE" || plan.nextReviewAt === null) {
        throw new ApiError(409, "Only active plans can be reviewed.");
      }
      if (requireDue && input.occurredAt < plan.nextReviewAt) throw new ApiError(409, "The next review is not due yet.");
      if (!this.isOpenCommitment(tx, input.currentTaskId, plan.primaryTaskId, plan.secondaryTaskId, localDate)) {
        throw new ApiError(409, "Current task must be an unfinished commitment.");
      }
      const completedRounds = plan.completedRounds + 1;
      const enough = completedRounds >= 4;
      return {
        completedRounds,
        currentTaskId: enough ? null : input.currentTaskId,
        state: enough ? "ENOUGH" as const : "ACTIVE" as const,
        nextReviewAt: enough ? null : input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  startExtraRound(localDate: string, input: DailyPlanReviewInput) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "ENOUGH") throw new ApiError(409, "Only enough plans can start an extra round.");
      if (!this.isOpenCommitment(tx, input.currentTaskId, plan.primaryTaskId, plan.secondaryTaskId, localDate)) {
        throw new ApiError(409, "An extra round requires an unfinished commitment.");
      }
      return {
        currentTaskId: input.currentTaskId,
        state: "ACTIVE" as const,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  endDailyPlan(localDate: string, input: DailyPlanTransitionInput) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      if (plan.state === "ENDED") throw new ApiError(409, "The plan has already ended.");
      const now = this.now();
      const updated: DailyPlanRow = {
        ...plan,
        currentTaskId: null,
        state: "ENDED",
        nextReviewAt: null,
        revision: plan.revision + 1,
        endedAt: input.occurredAt,
        updatedAt: now,
      };
      tx.update(dailyPlans).set({
        currentTaskId: null,
        state: updated.state,
        nextReviewAt: null,
        revision: updated.revision,
        endedAt: updated.endedAt,
        updatedAt: now,
      }).where(eq(dailyPlans.id, plan.id)).run();
      this.writeAudit(tx, {
        action: "DAILY_PLAN_ENDED",
        entityType: "DAILY_PLAN",
        entityId: updated.id,
        before: dailyPlanSnapshot(plan),
        after: dailyPlanSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
  }

  createTask(input: TaskCreateInput) {
    const id = this.db.transaction((tx) => {
      const now = this.now();
      const task = tx
        .insert(todoTasks)
        .values({ title: input.title, repeatMode: input.repeatMode, createdAt: now, updatedAt: now })
        .returning({ id: todoTasks.id })
        .get();
      const steps = tx.insert(todoSteps)
        .values(input.steps.map((step, position) => ({
          taskId: task.id,
          title: step.title,
          position,
          createdAt: now,
          updatedAt: now,
        })))
        .returning({ id: todoSteps.id, title: todoSteps.title })
        .all();
      this.writeAudit(tx, {
        action: "TASK_CREATED",
        entityType: "TASK",
        entityId: task.id,
        after: {
          ...taskSnapshot({
            id: task.id,
            title: input.title,
            repeatMode: input.repeatMode,
            completedAt: null,
          }, input.localDate),
          stepCount: steps.length,
        },
        createdAt: now,
      });
      steps.forEach((step) => {
        this.writeAudit(tx, {
          action: "STEP_CREATED",
          entityType: "STEP",
          entityId: step.id,
          after: stepSnapshot(
            { id: step.id, taskId: task.id, title: step.title, completedAt: null },
            { title: input.title, repeatMode: input.repeatMode },
            input.localDate,
          ),
          createdAt: now,
        });
      });
      return task.id;
    });
    return this.getTask(id, input.localDate);
  }

  updateTask(id: number, input: TaskWriteInput) {
    this.db.transaction((tx) => {
      const current = this.requireTask(tx, id);
      const before = this.attachSteps(tx, [current], input.localDate)[0]!;
      if (current.repeatMode !== input.repeatMode) {
        this.requireTaskOutsidePlan(tx, id, input.localDate, "Change the repeat mode after replacing this task in Today or ending the plan.");
        this.clearCompletionScope(tx, id, input.repeatMode, input.localDate);
      }
      if (current.title === input.title && current.repeatMode === input.repeatMode) return;
      const now = this.now();
      const updated = { ...current, title: input.title, repeatMode: input.repeatMode, updatedAt: now };
      tx.update(todoTasks)
        .set({ title: input.title, repeatMode: input.repeatMode, updatedAt: now })
        .where(eq(todoTasks.id, id))
        .run();
      const after = this.attachSteps(tx, [updated], input.localDate)[0]!;
      this.writeAudit(tx, {
        action: "TASK_UPDATED",
        entityType: "TASK",
        entityId: id,
        before: taskSnapshot(before, input.localDate),
        after: taskSnapshot(after, input.localDate),
        createdAt: now,
      });
    });
    return this.getTask(id, input.localDate);
  }

  deleteTask(id: number, localDate: string) {
    return this.db.transaction((tx) => {
      const task = this.requireTask(tx, id);
      const response = this.attachSteps(tx, [task], localDate)[0]!;
      this.requireTaskOutsidePlan(tx, id, localDate, "Replace this task in Today or end the plan before deleting it.");
      const stepCount = tx
        .select({ value: count() })
        .from(todoSteps)
        .where(eq(todoSteps.taskId, id))
        .get()!.value;
      this.writeAudit(tx, {
        action: "TASK_DELETED",
        entityType: "TASK",
        entityId: id,
        before: { ...taskSnapshot(response, localDate), stepCount },
        createdAt: this.now(),
      });
      tx.delete(todoTasks).where(eq(todoTasks.id, id)).run();
      return response;
    });
  }

  createStep(taskId: number, input: DatedStepInput) {
    const id = this.db.transaction((tx) => {
      const task = this.requireTask(tx, taskId);
      const beforeTask = this.attachSteps(tx, [task], input.localDate)[0]!;
      const now = this.now();
      const existing = tx.select().from(todoSteps).where(eq(todoSteps.taskId, taskId)).all();
      const position = existing.reduce((max, step) => Math.max(max, step.position), -1) + 1;
      const step = tx
        .insert(todoSteps)
        .values({ taskId, title: input.title, position, createdAt: now, updatedAt: now })
        .returning({ id: todoSteps.id })
        .get();
      tx.update(todoTasks).set({ updatedAt: now }).where(eq(todoTasks.id, taskId)).run();
      this.writeAudit(tx, {
        action: "STEP_CREATED",
        entityType: "STEP",
        entityId: step.id,
        after: stepSnapshot(
          { id: step.id, taskId, title: input.title, completedAt: null },
          task,
          input.localDate,
        ),
        createdAt: now,
      });
      const afterTask = this.attachSteps(tx, [{ ...task, updatedAt: now }], input.localDate)[0]!;
      this.reconcileTaskCompletion(tx, beforeTask, afterTask, input.localDate, now);
      return step.id;
    });
    return this.getStep(id, input.localDate);
  }

  updateStep(id: number, input: DatedStepInput) {
    this.db.transaction((tx) => {
      const step = this.requireStep(tx, id);
      const task = this.requireTask(tx, step.taskId);
      const before = this.stepResponse(tx, task, step, input.localDate);
      if (step.title === input.title) return;
      const now = this.now();
      const updated = { ...step, title: input.title, updatedAt: now };
      tx.update(todoSteps).set({ title: input.title, updatedAt: now }).where(eq(todoSteps.id, id)).run();
      tx.update(todoTasks).set({ updatedAt: now }).where(eq(todoTasks.id, task.id)).run();
      this.writeAudit(tx, {
        action: "STEP_UPDATED",
        entityType: "STEP",
        entityId: id,
        before: stepSnapshot(before, task, input.localDate),
        after: stepSnapshot({ ...updated, completedAt: before.completedAt }, task, input.localDate),
        createdAt: now,
      });
    });
    return this.getStep(id, input.localDate);
  }

  setStepCompleted(id: number, completed: boolean, localDate: string) {
    this.db.transaction((tx) => {
      const step = this.requireStep(tx, id);
      const task = this.requireTask(tx, step.taskId);
      const beforeTask = this.attachSteps(tx, [task], localDate)[0]!;
      const beforeStep = beforeTask.steps.find((candidate) => candidate.id === id)!;
      if ((beforeStep.completedAt !== null) === completed) return;
      const now = this.now();
      const scope = this.completionScope(task.repeatMode, localDate);
      if (completed) {
        tx.insert(todoStepCompletions).values({ stepId: id, ...scope, completedAt: now }).run();
      } else {
        tx.delete(todoStepCompletions).where(this.completionCondition(id, task.repeatMode, localDate)).run();
      }
      tx.update(todoSteps).set({ updatedAt: now }).where(eq(todoSteps.id, id)).run();
      tx.update(todoTasks).set({ updatedAt: now }).where(eq(todoTasks.id, task.id)).run();
      const afterStep = { ...beforeStep, completedAt: completed ? now : null, updatedAt: now };
      this.writeAudit(tx, {
        action: completed ? "STEP_COMPLETED" : "STEP_REOPENED",
        entityType: "STEP",
        entityId: id,
        before: stepSnapshot(beforeStep, task, localDate),
        after: stepSnapshot(afterStep, task, localDate),
        createdAt: now,
      });
      const afterTask = this.attachSteps(tx, [{ ...task, updatedAt: now }], localDate)[0]!;
      this.reconcileTaskCompletion(tx, beforeTask, afterTask, localDate, now);
    });
    return this.getStep(id, localDate);
  }

  deleteStep(id: number, localDate: string) {
    return this.db.transaction((tx) => {
      const step = this.requireStep(tx, id);
      const task = this.requireTask(tx, step.taskId);
      const beforeTask = this.attachSteps(tx, [task], localDate)[0]!;
      const beforeStep = beforeTask.steps.find((candidate) => candidate.id === id)!;
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
        before: stepSnapshot(beforeStep, task, localDate),
        createdAt: now,
      });
      tx.delete(todoSteps).where(eq(todoSteps.id, id)).run();
      tx.update(todoTasks).set({ updatedAt: now }).where(eq(todoTasks.id, task.id)).run();
      const afterTask = this.attachSteps(tx, [{ ...task, updatedAt: now }], localDate)[0]!;
      this.reconcileTaskCompletion(tx, beforeTask, afterTask, localDate, now);
      return beforeStep;
    });
  }

  reorderSteps(taskId: number, stepIds: number[], localDate: string) {
    this.db.transaction((tx) => {
      const task = this.requireTask(tx, taskId);
      const taskResponse = this.attachSteps(tx, [task], localDate)[0]!;
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
        before: { ...taskSnapshot(taskResponse, localDate), stepIds: before },
        after: { ...taskSnapshot(taskResponse, localDate), stepIds },
        createdAt: now,
      });
    });
    return this.getTask(taskId, localDate);
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

  private attachSteps(reader: RecordsReader, tasks: TodoTaskRow[], localDate: string): TodoTaskResponse[] {
    if (tasks.length === 0) return [];
    const taskIds = tasks.map((task) => task.id);
    const steps = reader
      .select()
      .from(todoSteps)
      .where(inArray(todoSteps.taskId, taskIds))
      .orderBy(asc(todoSteps.taskId), asc(todoSteps.position), asc(todoSteps.id))
      .all();
    const stepIds = steps.map((step) => step.id);
    const completions = stepIds.length === 0 ? [] : reader
      .select()
      .from(todoStepCompletions)
      .where(and(
        inArray(todoStepCompletions.stepId, stepIds),
        or(
          and(eq(todoStepCompletions.scope, "ONCE"), isNull(todoStepCompletions.localDate)),
          and(eq(todoStepCompletions.scope, "DAILY"), eq(todoStepCompletions.localDate, localDate)),
        ),
      ))
      .all();
    const completionByStepAndScope = new Map(
      completions.map((completion) => [`${completion.stepId}:${completion.scope}`, completion.completedAt]),
    );
    const taskById = new Map(tasks.map((task) => [task.id, task]));
    const stepsByTask = new Map<number, TodoStepResponse[]>();
    for (const step of steps) {
      const task = taskById.get(step.taskId)!;
      const taskSteps = stepsByTask.get(step.taskId) ?? [];
      taskSteps.push({
        ...step,
        completedAt: completionByStepAndScope.get(`${step.id}:${task.repeatMode}`) ?? null,
      });
      stepsByTask.set(step.taskId, taskSteps);
    }
    return tasks.map((task) => {
      const taskSteps = stepsByTask.get(task.id) ?? [];
      const completedAt = taskSteps.length > 0 && taskSteps.every((step) => step.completedAt !== null)
        ? Math.max(...taskSteps.map((step) => step.completedAt!))
        : null;
      return { ...task, completedAt, steps: taskSteps };
    });
  }

  private stepResponse(
    reader: RecordsReader,
    task: TodoTaskRow,
    step: TodoStepRow,
    localDate: string,
  ): TodoStepResponse {
    const completion = reader
      .select({ completedAt: todoStepCompletions.completedAt })
      .from(todoStepCompletions)
      .where(this.completionCondition(step.id, task.repeatMode, localDate))
      .get();
    return { ...step, completedAt: completion?.completedAt ?? null };
  }

  private completionScope(repeatMode: TaskRepeatMode, localDate: string) {
    return repeatMode === "ONCE"
      ? { scope: "ONCE" as const, localDate: null }
      : { scope: "DAILY" as const, localDate };
  }

  private completionCondition(stepId: number, repeatMode: TaskRepeatMode, localDate: string) {
    return repeatMode === "ONCE"
      ? and(
        eq(todoStepCompletions.stepId, stepId),
        eq(todoStepCompletions.scope, "ONCE"),
        isNull(todoStepCompletions.localDate),
      )
      : and(
        eq(todoStepCompletions.stepId, stepId),
        eq(todoStepCompletions.scope, "DAILY"),
        eq(todoStepCompletions.localDate, localDate),
      );
  }

  private clearCompletionScope(
    tx: RecordsTransaction,
    taskId: number,
    repeatMode: TaskRepeatMode,
    localDate: string,
  ) {
    const stepIds = tx.select({ id: todoSteps.id }).from(todoSteps).where(eq(todoSteps.taskId, taskId)).all()
      .map((step) => step.id);
    if (stepIds.length === 0) return;
    const scope = repeatMode === "ONCE"
      ? and(eq(todoStepCompletions.scope, "ONCE"), isNull(todoStepCompletions.localDate))
      : and(eq(todoStepCompletions.scope, "DAILY"), eq(todoStepCompletions.localDate, localDate));
    tx.delete(todoStepCompletions)
      .where(and(inArray(todoStepCompletions.stepId, stepIds), scope))
      .run();
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

  private reconcileTaskCompletion(
    tx: RecordsTransaction,
    before: TodoTaskResponse,
    after: TodoTaskResponse,
    localDate: string,
    now: number,
  ) {
    if ((before.completedAt !== null) === (after.completedAt !== null)) return;
    this.writeAudit(tx, {
      action: after.completedAt !== null ? "TASK_COMPLETED" : "TASK_REOPENED",
      entityType: "TASK",
      entityId: after.id,
      before: taskSnapshot(before, localDate),
      after: taskSnapshot(after, localDate),
      createdAt: now,
    });
    if (after.completedAt !== null) this.reconcileDailyPlanCompletion(tx, after.id, localDate, now);
  }

  private transitionDailyPlan(
    localDate: string,
    input: DailyPlanTransitionInput,
    transition: (tx: RecordsTransaction, plan: DailyPlanRow) => Partial<DailyPlanRow>,
  ) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      const patch = transition(tx, plan);
      tx.update(dailyPlans).set({ ...patch, revision: plan.revision + 1, updatedAt: this.now() })
        .where(eq(dailyPlans.id, plan.id)).run();
    });
    return this.getDailyPlan(localDate)!;
  }

  private requireDailyPlan(tx: RecordsTransaction, localDate: string) {
    const plan = tx.select().from(dailyPlans).where(eq(dailyPlans.localDate, localDate)).get();
    if (!plan) throw new ApiError(404, "Daily plan not found.");
    return plan;
  }

  private requireDailyPlanById(id: number) {
    const plan = this.db.select().from(dailyPlans).where(eq(dailyPlans.id, id)).get();
    if (!plan) throw new ApiError(404, "Daily plan not found.");
    return plan;
  }

  private requireRevision(plan: DailyPlanRow, expectedRevision: number) {
    if (plan.revision !== expectedRevision) throw new ApiError(409, "Daily plan has changed. Refresh and try again.");
  }

  private requireOpenTask(tx: RecordsTransaction, id: number, label: string, localDate: string) {
    const task = this.requireTask(tx, id);
    const response = this.attachSteps(tx, [task], localDate)[0]!;
    if (response.completedAt !== null) throw new ApiError(409, `${label} must be open.`);
    return task;
  }

  private requireTaskOutsidePlan(tx: RecordsTransaction, taskId: number, localDate: string, message: string) {
    const plan = tx.select().from(dailyPlans).where(eq(dailyPlans.localDate, localDate)).get();
    if (plan && plan.state !== "ENDED" && (plan.primaryTaskId === taskId || plan.secondaryTaskId === taskId)) {
      throw new ApiError(409, message);
    }
  }

  private requireReplaceableSlot(
    tx: RecordsTransaction,
    currentId: number | null,
    replacementId: number | null,
    label: string,
    localDate: string,
  ) {
    if (currentId === replacementId) return;
    if (currentId !== null) {
      const current = this.requireTask(tx, currentId);
      if (this.attachSteps(tx, [current], localDate)[0]!.completedAt !== null) {
        throw new ApiError(409, `A completed ${label.toLowerCase()} cannot be replaced.`);
      }
    }
    if (replacementId !== null) this.requireOpenTask(tx, replacementId, label, localDate);
  }

  private isOpenCommitment(
    tx: RecordsTransaction,
    taskId: number | null,
    primaryTaskId: number | null,
    secondaryTaskId: number | null,
    localDate: string,
  ) {
    if (taskId === null || (taskId !== primaryTaskId && taskId !== secondaryTaskId)) return false;
    const task = tx.select().from(todoTasks).where(eq(todoTasks.id, taskId)).get();
    return task !== undefined && this.attachSteps(tx, [task], localDate)[0]!.completedAt === null;
  }

  private firstOpenCommitment(
    tx: RecordsTransaction,
    primaryTaskId: number | null,
    secondaryTaskId: number | null,
    localDate: string,
  ) {
    if (this.isOpenCommitment(tx, primaryTaskId, primaryTaskId, secondaryTaskId, localDate)) return primaryTaskId;
    if (this.isOpenCommitment(tx, secondaryTaskId, primaryTaskId, secondaryTaskId, localDate)) return secondaryTaskId;
    return null;
  }

  private reconcileDailyPlanCompletion(
    tx: RecordsTransaction,
    taskId: number,
    localDate: string,
    occurredAt: number,
  ) {
    const plan = tx.select().from(dailyPlans)
      .where(and(
        eq(dailyPlans.localDate, localDate),
        inArray(dailyPlans.state, ["READY", "ACTIVE"]),
        or(eq(dailyPlans.primaryTaskId, taskId), eq(dailyPlans.secondaryTaskId, taskId)),
      ))
      .get();
    if (!plan) return;
    const nextTaskId = this.firstOpenCommitment(tx, plan.primaryTaskId, plan.secondaryTaskId, localDate);
    if (nextTaskId === null) {
      tx.update(dailyPlans).set({
        state: "ENOUGH",
        currentTaskId: null,
        nextReviewAt: null,
        revision: plan.revision + 1,
        updatedAt: occurredAt,
      }).where(eq(dailyPlans.id, plan.id)).run();
    } else if (plan.state === "ACTIVE" && plan.currentTaskId === taskId) {
      tx.update(dailyPlans).set({
        currentTaskId: nextTaskId,
        nextReviewAt: occurredAt + ReviewIntervalMs,
        revision: plan.revision + 1,
        updatedAt: occurredAt,
      }).where(eq(dailyPlans.id, plan.id)).run();
    }
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

function itemSnapshot(
  item: Pick<RecordItemRow, "id" | "name" | "description" | "mode" | "valueType" | "defaultValue" | "minValue" | "maxValue" | "stepValue" | "unit" | "goalValue" | "goalPeriod" | "priority" | "archived">,
): ItemAuditSnapshot {
  return item;
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

function taskSnapshot(
  task: { id: number; title: string; repeatMode: TaskRepeatMode; completedAt: number | null },
  localDate: string,
): TaskAuditSnapshot {
  return {
    id: task.id,
    title: task.title,
    repeatMode: task.repeatMode,
    localDate,
    completedAt: task.completedAt,
  };
}

function dailyPlanSnapshot(
  plan: Pick<DailyPlanRow, "id" | "localDate" | "firstMove" | "primaryTaskId" | "secondaryTaskId" | "currentTaskId" | "state" | "completedRounds" | "nextReviewAt">,
): DailyPlanAuditSnapshot {
  return {
    id: plan.id,
    localDate: plan.localDate,
    firstMove: plan.firstMove,
    primaryTaskId: plan.primaryTaskId,
    secondaryTaskId: plan.secondaryTaskId,
    currentTaskId: plan.currentTaskId,
    state: plan.state,
    completedRounds: plan.completedRounds,
    nextReviewAt: plan.nextReviewAt,
  };
}

function stepSnapshot(
  step: Pick<TodoStepResponse, "id" | "taskId" | "title" | "completedAt">,
  task: Pick<TodoTaskRow, "title" | "repeatMode">,
  localDate: string,
): StepAuditSnapshot {
  return {
    id: step.id,
    taskId: step.taskId,
    taskTitle: task.title,
    repeatMode: task.repeatMode,
    localDate,
    title: step.title,
    completedAt: step.completedAt,
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
