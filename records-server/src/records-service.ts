import { isDeepStrictEqual } from "node:util";
import { and, asc, count, desc, eq, gte, inArray, lt, lte, ne, sql } from "drizzle-orm";
import type {
  DailyFocusCreateInput,
  DailyFocusReviewInput,
  DailyFocusSelectionInput,
  DailyFocusTransitionInput,
  DailyFocusUpdateInput,
  GoalEntryInput,
  GoalInput,
  GoalSubgoalCompletionInput,
  RecordEntryInput,
  RecordItemInput,
  RecordMode,
  TemplateWriteInput,
  TodayTaskCompletionInput,
  TodayTaskCreateInput,
  TodayTaskFromTaskInput,
  TodayTaskFromTemplateInput,
} from "./domain.js";
import { ApiError, isMeasurementValueOnGrid } from "./domain.js";
import type { RecordsDatabase } from "./database.js";
import {
  dailyFocus,
  goalEntries,
  goals,
  goalSubgoals,
  recordAuditLogs,
  recordEntries,
  recordItems,
  taskTemplateSteps,
  taskTemplates,
  todayTaskSteps,
  todayTasks,
} from "./schema.js";

const AuditLogPageSize = 100;
const ReviewIntervalMs = 60 * 60 * 1_000;
const TargetRounds = 4;

type RecordItemRow = typeof recordItems.$inferSelect;
type RecordEntryRow = typeof recordEntries.$inferSelect;
type GoalRow = typeof goals.$inferSelect;
type GoalSubgoalRow = typeof goalSubgoals.$inferSelect;
type GoalEntryRow = typeof goalEntries.$inferSelect;
type TemplateRow = typeof taskTemplates.$inferSelect;
type TodayTaskRow = typeof todayTasks.$inferSelect;
type TodayTaskStepRow = typeof todayTaskSteps.$inferSelect;
type DailyFocusRow = typeof dailyFocus.$inferSelect;
type RecordsTransaction = Parameters<Parameters<RecordsDatabase["transaction"]>[0]>[0];
type RecordsReader = Pick<RecordsTransaction, "select">;

interface TemplateResponse extends TemplateRow {
  steps: Array<typeof taskTemplateSteps.$inferSelect>;
}

interface TodayTaskResponse extends TodayTaskRow {
  steps: TodayTaskStepRow[];
}

interface GoalResponse extends GoalRow {
  currentValue: number;
  subgoals: GoalSubgoalRow[];
}

type DailyFocusResponse = DailyFocusRow;

type AuditAction =
  | "ITEM_CREATED"
  | "ITEM_UPDATED"
  | "ITEM_ARCHIVED"
  | "ITEM_RESTORED"
  | "ITEM_DELETED"
  | "ENTRY_CREATED"
  | "ENTRY_DELETED"
  | "GOAL_CREATED"
  | "GOAL_UPDATED"
  | "GOAL_ARCHIVED"
  | "GOAL_RESTORED"
  | "GOAL_DELETED"
  | "GOAL_ENTRY_CREATED"
  | "GOAL_SUBGOAL_COMPLETED"
  | "GOAL_SUBGOAL_REOPENED"
  | "TEMPLATE_CREATED"
  | "TEMPLATE_UPDATED"
  | "TEMPLATE_ARCHIVED"
  | "TEMPLATE_RESTORED"
  | "TEMPLATE_DELETED"
  | "TODAY_TASK_CREATED"
  | "TODAY_TASK_COMPLETED"
  | "TODAY_TASK_REOPENED"
  | "TODAY_TASK_STEP_COMPLETED"
  | "TODAY_TASK_STEP_REOPENED"
  | "FOCUS_CREATED"
  | "FOCUS_UPDATED"
  | "FOCUS_ENDED";

interface AuditWrite {
  action: AuditAction;
  entityType: "ITEM" | "ENTRY" | "GOAL" | "GOAL_ENTRY" | "GOAL_SUBGOAL" | "TEMPLATE" | "TODAY_TASK" | "TODAY_TASK_STEP" | "FOCUS";
  entityId: number;
  before?: unknown;
  after?: unknown;
  createdAt: number;
}

export class RecordsService {
  constructor(
    private readonly db: RecordsDatabase,
    private readonly now: () => number = Date.now,
  ) {}

  listItems(archived: boolean) {
    return this.db
      .select()
      .from(recordItems)
      .where(eq(recordItems.archived, archived))
      .orderBy(sql`${recordItems.name} COLLATE NOCASE`)
      .all();
  }

  overview(startDate: string, endDate: string, planDate: string) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    return {
      items: this.listItems(false),
      goals: this.listGoals(false),
      templates: this.listTemplates(false),
      ...this.getToday(planDate),
      accumulateEntries: this.listEntries(startDate, endDate, undefined, "ACCUMULATE"),
      latestMeasurementEntries: this.listLatestEntries(),
    };
  }

  listGoals(archived: boolean) {
    const rows = this.db.select().from(goals)
      .where(eq(goals.archived, archived))
      .orderBy(asc(goals.endDate), sql`${goals.title} COLLATE NOCASE`).all();
    return this.attachGoalState(this.db, rows);
  }

  getGoal(id: number) {
    const row = this.db.select().from(goals).where(eq(goals.id, id)).get();
    if (!row) throw new ApiError(404, "Goal not found.");
    return this.attachGoalState(this.db, [row])[0]!;
  }

  createGoal(input: GoalInput) {
    const id = this.db.transaction((tx) => {
      const now = this.now();
      const { subgoals: inputs, ...fields } = input;
      const created = tx.insert(goals).values({ ...fields, archived: false, createdAt: now, updatedAt: now })
        .returning({ id: goals.id }).get();
      tx.insert(goalSubgoals).values(inputs.map((subgoal, position) => ({
        goalId: created.id,
        title: subgoal.title,
        targetValue: subgoal.targetValue,
        position,
        completedAt: null,
        createdAt: now,
        updatedAt: now,
      }))).run();
      const response = this.attachGoalState(tx, [{ id: created.id, ...fields, archived: false, createdAt: now, updatedAt: now }])[0]!;
      this.writeAudit(tx, { action: "GOAL_CREATED", entityType: "GOAL", entityId: created.id, after: goalSnapshot(response), createdAt: now });
      return created.id;
    });
    return this.getGoal(id);
  }

  updateGoal(id: number, input: GoalInput) {
    this.db.transaction((tx) => {
      const current = tx.select().from(goals).where(eq(goals.id, id)).get();
      if (!current) throw new ApiError(404, "Goal not found.");
      const before = this.attachGoalState(tx, [current])[0]!;
      const entryCount = tx.select({ value: count() }).from(goalEntries).where(eq(goalEntries.goalId, id)).get()!.value;
      if (entryCount > 0) {
        const sameDefinition = current.unit === input.unit
          && current.stepValue === input.stepValue
          && current.startDate === input.startDate
          && current.endDate === input.endDate
          && isDeepStrictEqual(
            before.subgoals.map((subgoal) => ({ id: subgoal.id, title: subgoal.title, targetValue: subgoal.targetValue })),
            input.subgoals,
          );
        if (!sameDefinition) throw new ApiError(400, "Goal dates, unit, step, and subgoals cannot change after progress is recorded.");
      } else {
        const currentIds = new Set(before.subgoals.map((subgoal) => subgoal.id));
        if (input.subgoals.some((subgoal) => subgoal.id !== null && !currentIds.has(subgoal.id))) {
          throw new ApiError(400, "Subgoal does not belong to this goal.");
        }
      }
      const { subgoals: inputs, ...fields } = input;
      const now = this.now();
      tx.update(goals).set({ ...fields, updatedAt: now }).where(eq(goals.id, id)).run();
      if (entryCount === 0) {
        tx.delete(goalSubgoals).where(eq(goalSubgoals.goalId, id)).run();
        tx.insert(goalSubgoals).values(inputs.map((subgoal, position) => ({
          goalId: id,
          title: subgoal.title,
          targetValue: subgoal.targetValue,
          position,
          completedAt: null,
          createdAt: now,
          updatedAt: now,
        }))).run();
      }
      const after = this.attachGoalState(tx, [{ ...current, ...fields, updatedAt: now }])[0]!;
      if (!isDeepStrictEqual(goalSnapshot(before), goalSnapshot(after))) {
        this.writeAudit(tx, { action: "GOAL_UPDATED", entityType: "GOAL", entityId: id, before: goalSnapshot(before), after: goalSnapshot(after), createdAt: now });
      }
    });
    return this.getGoal(id);
  }

  setGoalsArchived(ids: number[], archived: boolean) {
    this.db.transaction((tx) => {
      const existing = tx.select().from(goals).where(inArray(goals.id, ids)).all();
      if (existing.length !== ids.length) throw new ApiError(404, "Goal not found.");
      const changed = existing.filter((goal) => goal.archived !== archived);
      if (changed.length === 0) return;
      const now = this.now();
      tx.update(goals).set({ archived, updatedAt: now }).where(inArray(goals.id, changed.map((goal) => goal.id))).run();
      for (const goal of changed) {
        const before = this.attachGoalState(tx, [goal])[0]!;
        this.writeAudit(tx, {
          action: archived ? "GOAL_ARCHIVED" : "GOAL_RESTORED",
          entityType: "GOAL",
          entityId: goal.id,
          before: goalSnapshot(before),
          after: goalSnapshot({ ...before, archived }),
          createdAt: now,
        });
      }
    });
    return ids.map((id) => this.getGoal(id));
  }

  deleteArchivedGoal(id: number) {
    return this.db.transaction((tx) => {
      const current = tx.select().from(goals).where(eq(goals.id, id)).get();
      if (!current) throw new ApiError(404, "Goal not found.");
      if (!current.archived) throw new ApiError(400, "Archive the goal before deleting it.");
      const response = this.attachGoalState(tx, [current])[0]!;
      this.writeAudit(tx, { action: "GOAL_DELETED", entityType: "GOAL", entityId: id, before: goalSnapshot(response), createdAt: this.now() });
      tx.delete(goals).where(eq(goals.id, id)).run();
      return response;
    });
  }

  createGoalEntry(input: GoalEntryInput) {
    const id = this.db.transaction((tx) => {
      const goal = tx.select().from(goals).where(eq(goals.id, input.goalId)).get();
      if (!goal) throw new ApiError(404, "Goal not found.");
      if (goal.archived) throw new ApiError(400, "Archived goals cannot record progress.");
      const subgoals = tx.select().from(goalSubgoals).where(eq(goalSubgoals.goalId, goal.id)).all();
      if (subgoals.length > 0 && subgoals.every((subgoal) => subgoal.completedAt !== null)) {
        throw new ApiError(400, "Completed goals cannot record progress.");
      }
      if (input.localDate < goal.startDate || input.localDate > goal.endDate) {
        throw new ApiError(400, "Progress date must be within the goal date range.");
      }
      const now = this.now();
      const created = tx.insert(goalEntries).values({ ...input, createdAt: now }).returning({ id: goalEntries.id }).get();
      this.writeAudit(tx, { action: "GOAL_ENTRY_CREATED", entityType: "GOAL_ENTRY", entityId: created.id, after: { id: created.id, ...input }, createdAt: now });
      return created.id;
    });
    return this.db.select().from(goalEntries).where(eq(goalEntries.id, id)).get()!;
  }

  setGoalSubgoalCompleted(id: number, input: GoalSubgoalCompletionInput) {
    const goalId = this.db.transaction((tx) => {
      const subgoal = tx.select().from(goalSubgoals).where(eq(goalSubgoals.id, id)).get();
      if (!subgoal) throw new ApiError(404, "Subgoal not found.");
      const goal = tx.select().from(goals).where(eq(goals.id, subgoal.goalId)).get();
      if (!goal) throw new ApiError(404, "Goal not found.");
      if (goal.archived) throw new ApiError(400, "Archived goals cannot be changed.");
      const siblings = tx.select().from(goalSubgoals).where(eq(goalSubgoals.goalId, goal.id)).all();
      if (siblings.length > 0 && siblings.every((candidate) => candidate.completedAt !== null)) {
        throw new ApiError(400, "Completed goals cannot be changed.");
      }
      if (input.localDate < goal.startDate || input.localDate > goal.endDate) {
        throw new ApiError(400, "Subgoals can only be changed within the goal date range.");
      }
      if ((subgoal.completedAt !== null) === input.completed) return goal.id;
      const currentValue = this.goalCurrentValue(tx, goal.id);
      if (input.completed && currentValue < subgoal.targetValue) {
        throw new ApiError(409, "Shared progress has not reached this subgoal target.");
      }
      const completedAt = input.completed ? input.occurredAt : null;
      const now = this.now();
      tx.update(goalSubgoals).set({ completedAt, updatedAt: now }).where(eq(goalSubgoals.id, id)).run();
      this.writeAudit(tx, {
        action: input.completed ? "GOAL_SUBGOAL_COMPLETED" : "GOAL_SUBGOAL_REOPENED",
        entityType: "GOAL_SUBGOAL",
        entityId: id,
        before: subgoal,
        after: { ...subgoal, completedAt, updatedAt: now },
        createdAt: now,
      });
      return goal.id;
    });
    return this.getGoal(goalId);
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
      const created = tx.insert(recordItems)
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

      const entryTotal = tx.select({ value: count() })
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
      tx.update(recordItems).set({ ...input, updatedAt: now }).where(eq(recordItems.id, id)).run();
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
    return this.db.transaction((tx) => {
      const current = tx.select().from(recordItems).where(eq(recordItems.id, id)).get();
      if (!current) throw new ApiError(404, "Record item not found.");
      if (!current.archived) throw new ApiError(400, "Archive the record item before deleting it.");
      const entryCount = tx.select({ value: count() })
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
  }

  listEntries(startDate: string, endDate: string, itemId?: number, mode?: RecordMode) {
    if (startDate > endDate) throw new ApiError(400, "Start date must not be after end date.");
    const dateRange = and(gte(recordEntries.localDate, startDate), lte(recordEntries.localDate, endDate));
    const itemCondition = itemId === undefined ? dateRange : and(dateRange, eq(recordEntries.itemId, itemId));
    const condition = mode === undefined ? itemCondition : and(itemCondition, eq(recordItems.mode, mode));
    return this.entryQuery().where(condition).orderBy(desc(recordEntries.occurredAt), desc(recordEntries.id)).all();
  }

  getEntry(id: number) {
    const row = this.entryQuery().where(eq(recordEntries.id, id)).get();
    if (!row) throw new ApiError(404, "Record not found.");
    return row;
  }

  listLatestEntries() {
    const rows = this.db.select().from(recordItems)
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
      const created = tx.insert(recordEntries).values({ ...input, createdAt: now })
        .returning({ id: recordEntries.id }).get();
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
      const existing = tx.select({ entry: recordEntries, item: recordItems })
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

  listTemplates(archived: boolean) {
    const rows = this.db.select().from(taskTemplates)
      .where(eq(taskTemplates.archived, archived))
      .orderBy(sql`CASE WHEN ${taskTemplates.cadence} = 'DAILY' THEN 0 ELSE 1 END`, asc(taskTemplates.createdAt), asc(taskTemplates.id))
      .all();
    return this.attachTemplateSteps(this.db, rows);
  }

  getTemplate(id: number) {
    const row = this.db.select().from(taskTemplates).where(eq(taskTemplates.id, id)).get();
    if (!row) throw new ApiError(404, "Template not found.");
    return this.attachTemplateSteps(this.db, [row])[0]!;
  }

  createTemplate(input: TemplateWriteInput) {
    const id = this.db.transaction((tx) => {
      const now = this.now();
      const template = tx.insert(taskTemplates)
        .values({ title: input.title, cadence: input.cadence, archived: false, createdAt: now, updatedAt: now })
        .returning({ id: taskTemplates.id })
        .get();
      if (input.steps.length > 0) {
        tx.insert(taskTemplateSteps).values(input.steps.map((step, position) => ({
          templateId: template.id,
          title: step.title,
          position,
          createdAt: now,
          updatedAt: now,
        }))).run();
      }
      const created = this.attachTemplateSteps(tx, [
        { id: template.id, title: input.title, cadence: input.cadence, archived: false, createdAt: now, updatedAt: now },
      ])[0]!;
      this.writeAudit(tx, {
        action: "TEMPLATE_CREATED",
        entityType: "TEMPLATE",
        entityId: template.id,
        after: templateSnapshot(created),
        createdAt: now,
      });
      return template.id;
    });
    return this.getTemplate(id);
  }

  updateTemplate(id: number, input: TemplateWriteInput) {
    this.db.transaction((tx) => {
      const current = this.requireTemplate(tx, id);
      const before = this.attachTemplateSteps(tx, [current])[0]!;
      const same = current.title === input.title
        && current.cadence === input.cadence
        && isDeepStrictEqual(before.steps.map((step) => step.title), input.steps.map((step) => step.title));
      if (same) return;
      const now = this.now();
      tx.update(taskTemplates).set({ title: input.title, cadence: input.cadence, updatedAt: now })
        .where(eq(taskTemplates.id, id)).run();
      tx.delete(taskTemplateSteps).where(eq(taskTemplateSteps.templateId, id)).run();
      if (input.steps.length > 0) {
        tx.insert(taskTemplateSteps).values(input.steps.map((step, position) => ({
          templateId: id,
          title: step.title,
          position,
          createdAt: now,
          updatedAt: now,
        }))).run();
      }
      const after = this.attachTemplateSteps(tx, [{ ...current, ...input, updatedAt: now }])[0]!;
      this.writeAudit(tx, {
        action: "TEMPLATE_UPDATED",
        entityType: "TEMPLATE",
        entityId: id,
        before: templateSnapshot(before),
        after: templateSnapshot(after),
        createdAt: now,
      });
    });
    return this.getTemplate(id);
  }

  setTemplatesArchived(ids: number[], archived: boolean) {
    this.db.transaction((tx) => {
      const existing = tx.select().from(taskTemplates).where(inArray(taskTemplates.id, ids)).all();
      if (existing.length !== ids.length) throw new ApiError(404, "Template not found.");
      const changed = existing.filter((template) => template.archived !== archived);
      if (changed.length === 0) return;
      const now = this.now();
      tx.update(taskTemplates).set({ archived, updatedAt: now })
        .where(inArray(taskTemplates.id, changed.map((template) => template.id))).run();
      for (const template of changed) {
        const response = this.attachTemplateSteps(tx, [template])[0]!;
        this.writeAudit(tx, {
          action: archived ? "TEMPLATE_ARCHIVED" : "TEMPLATE_RESTORED",
          entityType: "TEMPLATE",
          entityId: template.id,
          before: templateSnapshot(response),
          after: templateSnapshot({ ...response, archived }),
          createdAt: now,
        });
      }
    });
    return ids.map((id) => this.getTemplate(id));
  }

  deleteArchivedTemplate(id: number) {
    return this.db.transaction((tx) => {
      const template = this.requireTemplate(tx, id);
      if (!template.archived) throw new ApiError(400, "Archive the template before deleting it.");
      const response = this.attachTemplateSteps(tx, [template])[0]!;
      this.writeAudit(tx, {
        action: "TEMPLATE_DELETED",
        entityType: "TEMPLATE",
        entityId: id,
        before: templateSnapshot(response),
        createdAt: this.now(),
      });
      tx.delete(taskTemplates).where(eq(taskTemplates.id, id)).run();
      return response;
    });
  }

  getToday(localDate: string) {
    const tasks = this.listTodayTasks(localDate);
    const usedTemplateIds = new Set(tasks.flatMap((task) => task.templateId === null ? [] : [task.templateId]));
    const copiedSourceIds = new Set(tasks.flatMap((task) => task.sourceTaskId === null ? [] : [task.sourceTaskId]));
    return {
      todayTasks: tasks,
      dailySuggestions: this.listTemplates(false)
        .filter((template) => template.cadence === "DAILY" && !usedTemplateIds.has(template.id)),
      unfinishedTasks: this.listTodayTasks(previousLocalDate(localDate))
        .filter((task) => task.completedAt === null && !copiedSourceIds.has(task.id)),
      dailyFocus: this.getDailyFocus(localDate),
    };
  }

  listTodayTasks(localDate: string) {
    const rows = this.db.select().from(todayTasks)
      .where(eq(todayTasks.localDate, localDate))
      .orderBy(asc(todayTasks.position), asc(todayTasks.id)).all();
    return this.attachTodayTaskSteps(this.db, rows);
  }

  getDailyFocus(localDate: string): DailyFocusResponse | null {
    return this.db.select().from(dailyFocus).where(eq(dailyFocus.localDate, localDate)).get() ?? null;
  }

  createTodayTask(input: TodayTaskCreateInput) {
    this.db.transaction((tx) => {
      this.createTodayTaskSnapshot(tx, input.localDate, null, null, input.title, input.steps, this.now());
    });
    return this.getToday(input.localDate);
  }

  createTodayTaskFromTemplate(input: TodayTaskFromTemplateInput) {
    this.db.transaction((tx) => {
      const template = this.requireActiveTemplate(tx, input.templateId);
      const steps = tx.select().from(taskTemplateSteps).where(eq(taskTemplateSteps.templateId, template.id))
        .orderBy(asc(taskTemplateSteps.position), asc(taskTemplateSteps.id)).all();
      this.createTodayTaskSnapshot(tx, input.localDate, template.id, null, template.title, steps, this.now());
    });
    return this.getToday(input.localDate);
  }

  createTodayTaskFromTask(input: TodayTaskFromTaskInput) {
    this.db.transaction((tx) => {
      const source = this.requireTodayTask(tx, input.sourceTaskId);
      if (source.localDate !== previousLocalDate(input.localDate) || source.completedAt !== null) {
        throw new ApiError(409, "Only an unfinished task from yesterday can be continued.");
      }
      if (tx.select({ id: todayTasks.id }).from(todayTasks)
        .where(and(eq(todayTasks.localDate, input.localDate), eq(todayTasks.sourceTaskId, source.id))).get()) {
        throw new ApiError(409, "This task has already been continued today.");
      }
      const steps = this.attachTodayTaskSteps(tx, [source])[0]!.steps
        .filter((step) => step.completedAt === null);
      this.createTodayTaskSnapshot(tx, input.localDate, source.templateId, source.id, source.title, steps, this.now());
    });
    return this.getToday(input.localDate);
  }

  setTodayTaskCompleted(id: number, input: TodayTaskCompletionInput) {
    const localDate = this.db.transaction((tx) => {
      const task = this.requireTodayTask(tx, id);
      this.requireTaskRevision(task, input.expectedRevision);
      if ((task.completedAt !== null) === input.completed) return task.localDate;
      const before = this.attachTodayTaskSteps(tx, [task])[0]!;
      const now = this.now();
      const completedAt = input.completed ? input.occurredAt : null;
      for (const step of before.steps.filter((candidate) => (candidate.completedAt !== null) !== input.completed)) {
        tx.update(todayTaskSteps).set({ completedAt, updatedAt: now }).where(eq(todayTaskSteps.id, step.id)).run();
        this.writeAudit(tx, {
          action: input.completed ? "TODAY_TASK_STEP_COMPLETED" : "TODAY_TASK_STEP_REOPENED",
          entityType: "TODAY_TASK_STEP",
          entityId: step.id,
          before: todayTaskStepSnapshot(step, task),
          after: todayTaskStepSnapshot({ ...step, completedAt }, task),
          createdAt: now,
        });
      }
      const updated = { ...task, completedAt, revision: task.revision + 1, updatedAt: now };
      tx.update(todayTasks).set({ completedAt, revision: updated.revision, updatedAt: now })
        .where(eq(todayTasks.id, task.id)).run();
      const after = this.attachTodayTaskSteps(tx, [updated])[0]!;
      this.writeTaskCompletionAudit(tx, before, after, now);
      this.reconcileFocusAfterTaskCompletion(tx, before, after, now);
      return task.localDate;
    });
    return this.getToday(localDate);
  }

  setTodayTaskStepCompleted(id: number, input: TodayTaskCompletionInput) {
    const localDate = this.db.transaction((tx) => {
      const step = tx.select().from(todayTaskSteps).where(eq(todayTaskSteps.id, id)).get();
      if (!step) throw new ApiError(404, "Today task step not found.");
      const task = this.requireTodayTask(tx, step.taskId);
      this.requireTaskRevision(task, input.expectedRevision);
      if ((step.completedAt !== null) === input.completed) return task.localDate;
      const before = this.attachTodayTaskSteps(tx, [task])[0]!;
      const now = this.now();
      const completedAt = input.completed ? input.occurredAt : null;
      tx.update(todayTaskSteps).set({ completedAt, updatedAt: now }).where(eq(todayTaskSteps.id, id)).run();
      const steps = this.attachTodayTaskSteps(tx, [task])[0]!.steps;
      const taskCompletedAt = steps.length > 0 && steps.every((candidate) => candidate.completedAt !== null)
        ? Math.max(...steps.map((candidate) => candidate.completedAt!))
        : null;
      const updated = { ...task, completedAt: taskCompletedAt, revision: task.revision + 1, updatedAt: now };
      tx.update(todayTasks).set({ completedAt: taskCompletedAt, revision: updated.revision, updatedAt: now })
        .where(eq(todayTasks.id, task.id)).run();
      const after = this.attachTodayTaskSteps(tx, [updated])[0]!;
      this.writeAudit(tx, {
        action: input.completed ? "TODAY_TASK_STEP_COMPLETED" : "TODAY_TASK_STEP_REOPENED",
        entityType: "TODAY_TASK_STEP",
        entityId: id,
        before: todayTaskStepSnapshot(step, task),
        after: todayTaskStepSnapshot({ ...step, completedAt }, task),
        createdAt: now,
      });
      if ((before.completedAt !== null) !== (after.completedAt !== null)) {
        this.writeTaskCompletionAudit(tx, before, after, now);
        this.reconcileFocusAfterTaskCompletion(tx, before, after, now);
      }
      return task.localDate;
    });
    return this.getToday(localDate);
  }

  createDailyFocus(input: DailyFocusCreateInput) {
    const id = this.db.transaction((tx) => {
      if (tx.select({ id: dailyFocus.id }).from(dailyFocus).where(eq(dailyFocus.localDate, input.localDate)).get()) {
        throw new ApiError(409, "Focus already exists for this date.");
      }
      this.requireFocusSelection(tx, input.localDate, input.primaryTaskId, input.secondaryTaskId);
      const now = this.now();
      const created = tx.insert(dailyFocus).values({
        localDate: input.localDate,
        firstMove: input.firstMove,
        firstMoveCompletedAt: null,
        primaryTaskId: input.primaryTaskId,
        secondaryTaskId: input.secondaryTaskId,
        currentTaskId: null,
        state: "READY",
        pauseReason: null,
        completedRounds: 0,
        nextReviewAt: null,
        revision: 0,
        endedAt: null,
        createdAt: now,
        updatedAt: now,
      }).returning().get();
      this.writeAudit(tx, {
        action: "FOCUS_CREATED",
        entityType: "FOCUS",
        entityId: created.id,
        after: dailyFocusSnapshot(created),
        createdAt: now,
      });
      return created.id;
    });
    return this.requireDailyFocusById(id);
  }

  updateDailyFocus(localDate: string, input: DailyFocusUpdateInput) {
    return this.transitionDailyFocus(localDate, input, (tx, focus) => {
      if (focus.state !== "READY") throw new ApiError(409, "Only ready Focus can change its initial tasks.");
      this.requireFocusSelection(tx, localDate, input.primaryTaskId, input.secondaryTaskId);
      return { firstMove: input.firstMove, primaryTaskId: input.primaryTaskId, secondaryTaskId: input.secondaryTaskId };
    });
  }

  startDailyFocus(localDate: string, input: DailyFocusTransitionInput) {
    return this.transitionDailyFocus(localDate, input, (tx, focus) => {
      if (focus.state !== "READY") throw new ApiError(409, "Only ready Focus can start.");
      this.requireFocusSelection(tx, localDate, focus.primaryTaskId, focus.secondaryTaskId);
      return {
        firstMoveCompletedAt: input.occurredAt,
        currentTaskId: focus.primaryTaskId,
        state: "ACTIVE" as const,
        pauseReason: null,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  reviewDailyFocus(localDate: string, input: DailyFocusReviewInput) {
    return this.completeFocusRound(localDate, input, true);
  }

  reviewDailyFocusNow(localDate: string, input: DailyFocusReviewInput) {
    return this.completeFocusRound(localDate, input, false);
  }

  switchDailyFocusTask(localDate: string, input: DailyFocusReviewInput) {
    return this.transitionDailyFocus(localDate, input, (tx, focus) => {
      if (focus.state !== "ACTIVE") throw new ApiError(409, "Only active Focus can switch tasks.");
      if (input.currentTaskId === focus.currentTaskId) throw new ApiError(409, "Choose another Focus task.");
      this.requireSelectedOpenFocusTask(tx, focus, input.currentTaskId);
      return { currentTaskId: input.currentTaskId, nextReviewAt: input.occurredAt + ReviewIntervalMs };
    });
  }

  refocusDailyFocus(localDate: string, input: DailyFocusSelectionInput) {
    return this.resumeDailyFocus(localDate, input, "FOCUS_CLEARED");
  }

  continueDailyFocus(localDate: string, input: DailyFocusSelectionInput) {
    return this.resumeDailyFocus(localDate, input, "TARGET_REACHED");
  }

  resetDailyFocus(localDate: string, input: DailyFocusTransitionInput) {
    return this.transitionDailyFocus(localDate, input, (_tx, focus) => {
      if (focus.state === "READY") throw new ApiError(409, "Focus is already ready.");
      return {
        firstMoveCompletedAt: null,
        currentTaskId: null,
        state: "READY" as const,
        pauseReason: null,
        completedRounds: 0,
        nextReviewAt: null,
        endedAt: null,
      };
    });
  }

  endDailyFocus(localDate: string, input: DailyFocusTransitionInput) {
    return this.transitionDailyFocus(localDate, input, (_tx, focus) => {
      if (focus.state === "ENDED") throw new ApiError(409, "Focus has already ended.");
      return {
        currentTaskId: null,
        state: "ENDED" as const,
        pauseReason: null,
        nextReviewAt: null,
        endedAt: input.occurredAt,
      };
    }, "FOCUS_ENDED");
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

  private completeFocusRound(localDate: string, input: DailyFocusReviewInput, requireDue: boolean) {
    return this.transitionDailyFocus(localDate, input, (tx, focus) => {
      if (focus.state !== "ACTIVE" || focus.nextReviewAt === null) {
        throw new ApiError(409, "Only active Focus can be reviewed.");
      }
      if (requireDue && input.occurredAt < focus.nextReviewAt) throw new ApiError(409, "The next review is not due yet.");
      this.requireSelectedOpenFocusTask(tx, focus, input.currentTaskId);
      const completedRounds = focus.completedRounds + 1;
      const targetReached = completedRounds >= TargetRounds;
      return {
        completedRounds,
        currentTaskId: targetReached ? null : input.currentTaskId,
        state: targetReached ? "PAUSED" as const : "ACTIVE" as const,
        pauseReason: targetReached ? "TARGET_REACHED" as const : null,
        nextReviewAt: targetReached ? null : input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  private resumeDailyFocus(
    localDate: string,
    input: DailyFocusSelectionInput,
    pauseReason: "FOCUS_CLEARED" | "TARGET_REACHED",
  ) {
    return this.transitionDailyFocus(localDate, input, (tx, focus) => {
      if (focus.state !== "PAUSED" || focus.pauseReason !== pauseReason) {
        throw new ApiError(409, pauseReason === "FOCUS_CLEARED"
          ? "New Focus can be selected only after the current Focus is cleared."
          : "Another round is available only after reaching the round target.");
      }
      this.requireFocusSelection(tx, localDate, input.primaryTaskId, input.secondaryTaskId);
      return {
        primaryTaskId: input.primaryTaskId,
        secondaryTaskId: input.secondaryTaskId,
        currentTaskId: input.primaryTaskId,
        state: "ACTIVE" as const,
        pauseReason: null,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  private transitionDailyFocus(
    localDate: string,
    input: DailyFocusTransitionInput,
    transition: (tx: RecordsTransaction, focus: DailyFocusRow) => Partial<DailyFocusRow>,
    action: "FOCUS_UPDATED" | "FOCUS_ENDED" = "FOCUS_UPDATED",
  ) {
    this.db.transaction((tx) => {
      const focus = this.requireDailyFocus(tx, localDate);
      this.requireFocusRevision(focus, input.expectedRevision);
      const patch = transition(tx, focus);
      const now = this.now();
      const updated = { ...focus, ...patch, revision: focus.revision + 1, updatedAt: now };
      tx.update(dailyFocus).set({ ...patch, revision: updated.revision, updatedAt: now })
        .where(eq(dailyFocus.id, focus.id)).run();
      this.writeAudit(tx, {
        action,
        entityType: "FOCUS",
        entityId: focus.id,
        before: dailyFocusSnapshot(focus),
        after: dailyFocusSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getDailyFocus(localDate)!;
  }

  private attachTemplateSteps(reader: RecordsReader, templates: TemplateRow[]): TemplateResponse[] {
    if (templates.length === 0) return [];
    const steps = reader.select().from(taskTemplateSteps)
      .where(inArray(taskTemplateSteps.templateId, templates.map((template) => template.id)))
      .orderBy(asc(taskTemplateSteps.templateId), asc(taskTemplateSteps.position), asc(taskTemplateSteps.id))
      .all();
    const byTemplate = new Map<number, typeof steps>();
    for (const step of steps) {
      const values = byTemplate.get(step.templateId) ?? [];
      values.push(step);
      byTemplate.set(step.templateId, values);
    }
    return templates.map((template) => ({ ...template, steps: byTemplate.get(template.id) ?? [] }));
  }

  private attachTodayTaskSteps(reader: RecordsReader, tasks: TodayTaskRow[]): TodayTaskResponse[] {
    if (tasks.length === 0) return [];
    const steps = reader.select().from(todayTaskSteps)
      .where(inArray(todayTaskSteps.taskId, tasks.map((task) => task.id)))
      .orderBy(asc(todayTaskSteps.taskId), asc(todayTaskSteps.position), asc(todayTaskSteps.id)).all();
    const byTask = new Map<number, TodayTaskStepRow[]>();
    for (const step of steps) {
      const values = byTask.get(step.taskId) ?? [];
      values.push(step);
      byTask.set(step.taskId, values);
    }
    return tasks.map((task) => ({ ...task, steps: byTask.get(task.id) ?? [] }));
  }

  private createTodayTaskSnapshot(
    tx: RecordsTransaction,
    localDate: string,
    templateId: number | null,
    sourceTaskId: number | null,
    title: string,
    steps: Array<{ title: string }>,
    now: number,
  ) {
    const last = tx.select({ position: todayTasks.position }).from(todayTasks)
      .where(eq(todayTasks.localDate, localDate)).orderBy(desc(todayTasks.position)).get();
    const task = tx.insert(todayTasks).values({
      localDate,
      templateId,
      sourceTaskId,
      title,
      position: (last?.position ?? -1) + 1,
      completedAt: null,
      revision: 0,
      createdAt: now,
      updatedAt: now,
    }).returning().get();
    if (steps.length > 0) {
      tx.insert(todayTaskSteps).values(steps.map((step, position) => ({
        taskId: task.id,
        title: step.title,
        position,
        completedAt: null,
        createdAt: now,
        updatedAt: now,
      }))).run();
    }
    const response = this.attachTodayTaskSteps(tx, [task])[0]!;
    this.writeAudit(tx, {
      action: "TODAY_TASK_CREATED",
      entityType: "TODAY_TASK",
      entityId: task.id,
      after: todayTaskSnapshot(response),
      createdAt: now,
    });
    return task.id;
  }

  private requireTemplate(reader: RecordsReader, id: number) {
    const template = reader.select().from(taskTemplates).where(eq(taskTemplates.id, id)).get();
    if (!template) throw new ApiError(404, "Template not found.");
    return template;
  }

  private requireActiveTemplate(reader: RecordsReader, id: number) {
    const template = this.requireTemplate(reader, id);
    if (template.archived) throw new ApiError(409, "Archived templates cannot create Today tasks.");
    return template;
  }

  private requireTodayTask(reader: RecordsReader, id: number) {
    const task = reader.select().from(todayTasks).where(eq(todayTasks.id, id)).get();
    if (!task) throw new ApiError(404, "Today task not found.");
    return task;
  }

  private requireOpenTodayTask(reader: RecordsReader, localDate: string, id: number) {
    const task = this.requireTodayTask(reader, id);
    if (task.localDate !== localDate || task.completedAt !== null) {
      throw new ApiError(409, "Focus tasks must be unfinished and belong to this date.");
    }
    return task;
  }

  private requireFocusSelection(reader: RecordsReader, localDate: string, primaryTaskId: number, secondaryTaskId: number | null) {
    this.requireOpenTodayTask(reader, localDate, primaryTaskId);
    if (secondaryTaskId !== null) this.requireOpenTodayTask(reader, localDate, secondaryTaskId);
  }

  private requireSelectedOpenFocusTask(reader: RecordsReader, focus: DailyFocusRow, id: number) {
    if (id !== focus.primaryTaskId && id !== focus.secondaryTaskId) {
      throw new ApiError(409, "Choose an unfinished task selected for Focus.");
    }
    return this.requireOpenTodayTask(reader, focus.localDate, id);
  }

  private requireDailyFocus(reader: RecordsReader, localDate: string) {
    const focus = reader.select().from(dailyFocus).where(eq(dailyFocus.localDate, localDate)).get();
    if (!focus) throw new ApiError(404, "Daily Focus not found.");
    return focus;
  }

  private requireDailyFocusById(id: number) {
    const focus = this.db.select().from(dailyFocus).where(eq(dailyFocus.id, id)).get();
    if (!focus) throw new ApiError(404, "Daily Focus not found.");
    return focus;
  }

  private requireFocusRevision(focus: DailyFocusRow, expectedRevision: number) {
    if (focus.revision !== expectedRevision) throw new ApiError(409, "Focus has changed. Refresh and try again.");
  }

  private requireTaskRevision(task: TodayTaskRow, expectedRevision: number) {
    if (task.revision !== expectedRevision) throw new ApiError(409, "Today task has changed. Refresh and try again.");
  }

  private writeTaskCompletionAudit(
    tx: RecordsTransaction,
    before: TodayTaskResponse,
    after: TodayTaskResponse,
    now: number,
  ) {
    this.writeAudit(tx, {
      action: after.completedAt === null ? "TODAY_TASK_REOPENED" : "TODAY_TASK_COMPLETED",
      entityType: "TODAY_TASK",
      entityId: after.id,
      before: todayTaskSnapshot(before),
      after: todayTaskSnapshot(after),
      createdAt: now,
    });
  }

  private reconcileFocusAfterTaskCompletion(
    tx: RecordsTransaction,
    beforeTask: TodayTaskResponse,
    afterTask: TodayTaskResponse,
    now: number,
  ) {
    if (beforeTask.completedAt !== null || afterTask.completedAt === null) return;
    const focus = tx.select().from(dailyFocus).where(eq(dailyFocus.localDate, afterTask.localDate)).get();
    if (!focus || focus.state !== "ACTIVE") return;
    const selected = [focus.primaryTaskId, focus.secondaryTaskId].filter((id): id is number => id !== null);
    if (!selected.includes(afterTask.id)) return;
    const openTaskId = selected.map((id) => tx.select().from(todayTasks).where(eq(todayTasks.id, id)).get())
      .find((task) => task?.completedAt === null)?.id ?? null;
    if (focus.currentTaskId !== afterTask.id && openTaskId !== null) return;
    const patch: Partial<DailyFocusRow> = openTaskId === null
      ? { currentTaskId: null, state: "PAUSED", pauseReason: "FOCUS_CLEARED", nextReviewAt: null }
      : { currentTaskId: openTaskId };
    const updated = { ...focus, ...patch, revision: focus.revision + 1, updatedAt: now };
    tx.update(dailyFocus).set({ ...patch, revision: updated.revision, updatedAt: now })
      .where(eq(dailyFocus.id, focus.id)).run();
    this.writeAudit(tx, {
      action: "FOCUS_UPDATED",
      entityType: "FOCUS",
      entityId: focus.id,
      before: dailyFocusSnapshot(focus),
      after: dailyFocusSnapshot(updated),
      createdAt: now,
    });
  }

  private entryQuery() {
    return this.db.select({
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
    }).from(recordEntries).innerJoin(recordItems, eq(recordItems.id, recordEntries.itemId));
  }

  private attachGoalState(reader: RecordsReader, rows: GoalRow[]): GoalResponse[] {
    if (rows.length === 0) return [];
    const ids = rows.map((goal) => goal.id);
    const children = reader.select().from(goalSubgoals)
      .where(inArray(goalSubgoals.goalId, ids))
      .orderBy(asc(goalSubgoals.position), asc(goalSubgoals.id)).all();
    return rows.map((goal) => ({
      ...goal,
      currentValue: this.goalCurrentValue(reader, goal.id),
      subgoals: children.filter((subgoal) => subgoal.goalId === goal.id),
    }));
  }

  private goalCurrentValue(reader: RecordsReader, goalId: number): number {
    const result = reader.select({ value: sql<number>`coalesce(sum(${goalEntries.value}), 0)` })
      .from(goalEntries).where(eq(goalEntries.goalId, goalId)).get();
    return Number(result?.value ?? 0);
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
    if (item.mode === "ACCUMULATE" && value <= 0) throw new ApiError(400, "Value must be a positive number.");
    if (item.mode === "MEASUREMENT" && (
      item.minValue === null
      || item.maxValue === null
      || !isMeasurementValueOnGrid(value, item.minValue, item.maxValue, item.stepValue)
    )) {
      throw new ApiError(400, "Value must be within the configured measurement range and step.");
    }
  }
}

function itemSnapshot(
  item: Pick<RecordItemRow, "id" | "name" | "description" | "mode" | "valueType" | "defaultValue" | "minValue" | "maxValue" | "stepValue" | "unit" | "goalValue" | "goalPeriod" | "priority" | "archived">,
) {
  return item;
}

function goalSnapshot(goal: GoalResponse) {
  return {
    id: goal.id,
    title: goal.title,
    description: goal.description,
    unit: goal.unit,
    defaultValue: goal.defaultValue,
    stepValue: goal.stepValue,
    startDate: goal.startDate,
    endDate: goal.endDate,
    archived: goal.archived,
    currentValue: goal.currentValue,
    subgoals: goal.subgoals.map((subgoal) => ({
      id: subgoal.id,
      title: subgoal.title,
      targetValue: subgoal.targetValue,
      position: subgoal.position,
      completedAt: subgoal.completedAt,
    })),
  };
}

function entrySnapshot(entry: RecordEntryRow, itemName: string) {
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

function templateSnapshot(template: TemplateResponse) {
  return {
    id: template.id,
    title: template.title,
    cadence: template.cadence,
    archived: template.archived,
    steps: template.steps.map((step) => step.title),
  };
}

function todayTaskSnapshot(task: TodayTaskResponse) {
  return {
    id: task.id,
    localDate: task.localDate,
    templateId: task.templateId,
    sourceTaskId: task.sourceTaskId,
    position: task.position,
    title: task.title,
    completedAt: task.completedAt,
    steps: task.steps.map((step) => ({ id: step.id, title: step.title, completedAt: step.completedAt })),
  };
}

function todayTaskStepSnapshot(step: TodayTaskStepRow, task: TodayTaskRow) {
  return {
    id: step.id,
    taskId: task.id,
    taskTitle: task.title,
    title: step.title,
    completedAt: step.completedAt,
  };
}

function dailyFocusSnapshot(focus: DailyFocusResponse) {
  return {
    id: focus.id,
    localDate: focus.localDate,
    firstMove: focus.firstMove,
    primaryTaskId: focus.primaryTaskId,
    secondaryTaskId: focus.secondaryTaskId,
    currentTaskId: focus.currentTaskId,
    state: focus.state,
    pauseReason: focus.pauseReason,
    completedRounds: focus.completedRounds,
    nextReviewAt: focus.nextReviewAt,
  };
}

function previousLocalDate(localDate: string) {
  const date = new Date(`${localDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
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
