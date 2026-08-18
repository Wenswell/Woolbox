import { isDeepStrictEqual } from "node:util";
import { and, asc, count, desc, eq, gte, inArray, lt, lte, ne, sql } from "drizzle-orm";
import type {
  CommitmentStepCompletionInput,
  DailyPlanAddCommitmentInput,
  DailyPlanCreateInput,
  DailyPlanReviewInput,
  DailyPlanTransitionInput,
  DailyPlanUpdateInput,
  RecordEntryInput,
  RecordItemInput,
  RecordMode,
  TemplateWriteInput,
} from "./domain.js";
import { ApiError, isMeasurementValueOnGrid } from "./domain.js";
import type { RecordsDatabase } from "./database.js";
import {
  dailyPlans,
  recordAuditLogs,
  recordEntries,
  recordItems,
  taskTemplateSteps,
  taskTemplates,
  todayCommitmentSteps,
  todayCommitments,
} from "./schema.js";

const AuditLogPageSize = 100;
const ReviewIntervalMs = 60 * 60 * 1_000;
const TargetRounds = 4;

type RecordItemRow = typeof recordItems.$inferSelect;
type RecordEntryRow = typeof recordEntries.$inferSelect;
type TemplateRow = typeof taskTemplates.$inferSelect;
type DailyPlanRow = typeof dailyPlans.$inferSelect;
type CommitmentRow = typeof todayCommitments.$inferSelect;
type CommitmentStepRow = typeof todayCommitmentSteps.$inferSelect;
type RecordsTransaction = Parameters<Parameters<RecordsDatabase["transaction"]>[0]>[0];
type RecordsReader = Pick<RecordsTransaction, "select">;

interface TemplateResponse extends TemplateRow {
  steps: Array<typeof taskTemplateSteps.$inferSelect>;
}

interface CommitmentResponse extends CommitmentRow {
  completedAt: number | null;
  steps: CommitmentStepRow[];
}

interface DailyPlanResponse extends DailyPlanRow {
  commitments: CommitmentResponse[];
}

type AuditAction =
  | "ITEM_CREATED"
  | "ITEM_UPDATED"
  | "ITEM_ARCHIVED"
  | "ITEM_RESTORED"
  | "ITEM_DELETED"
  | "ENTRY_CREATED"
  | "ENTRY_DELETED"
  | "TEMPLATE_CREATED"
  | "TEMPLATE_UPDATED"
  | "TEMPLATE_ARCHIVED"
  | "TEMPLATE_RESTORED"
  | "TEMPLATE_DELETED"
  | "COMMITMENT_CREATED"
  | "COMMITMENT_COMPLETED"
  | "COMMITMENT_REOPENED"
  | "COMMITMENT_STEP_COMPLETED"
  | "COMMITMENT_STEP_REOPENED"
  | "DAILY_PLAN_CREATED"
  | "DAILY_PLAN_UPDATED"
  | "DAILY_PLAN_ENDED";

interface AuditWrite {
  action: AuditAction;
  entityType: "ITEM" | "ENTRY" | "TEMPLATE" | "COMMITMENT" | "COMMITMENT_STEP" | "DAILY_PLAN";
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
      templates: this.listTemplates(false),
      dailyPlan: this.getDailyPlan(planDate),
      accumulateEntries: this.listEntries(startDate, endDate, undefined, "ACCUMULATE"),
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
      tx.insert(taskTemplateSteps).values(input.steps.map((step, position) => ({
        templateId: template.id,
        title: step.title,
        position,
        createdAt: now,
        updatedAt: now,
      }))).run();
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
      tx.insert(taskTemplateSteps).values(input.steps.map((step, position) => ({
        templateId: id,
        title: step.title,
        position,
        createdAt: now,
        updatedAt: now,
      }))).run();
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

  getDailyPlan(localDate: string): DailyPlanResponse | null {
    const row = this.db.select().from(dailyPlans).where(eq(dailyPlans.localDate, localDate)).get();
    return row ? this.attachCommitments(this.db, row) : null;
  }

  createDailyPlan(input: DailyPlanCreateInput) {
    const id = this.db.transaction((tx) => {
      if (tx.select({ id: dailyPlans.id }).from(dailyPlans).where(eq(dailyPlans.localDate, input.localDate)).get()) {
        throw new ApiError(409, "A plan already exists for this date.");
      }
      this.requireActiveTemplate(tx, input.primaryTemplateId);
      if (input.secondaryTemplateId !== null) this.requireActiveTemplate(tx, input.secondaryTemplateId);
      const now = this.now();
      const plan = tx.insert(dailyPlans).values({
        localDate: input.localDate,
        firstMove: input.firstMove,
        firstMoveCompletedAt: null,
        currentCommitmentId: null,
        state: "READY",
        pauseReason: null,
        completedRounds: 0,
        nextReviewAt: null,
        revision: 0,
        endedAt: null,
        createdAt: now,
        updatedAt: now,
      }).returning().get();
      this.createCommitmentSnapshot(tx, plan.id, input.primaryTemplateId, "PRIMARY", 0, now);
      if (input.secondaryTemplateId !== null) {
        this.createCommitmentSnapshot(tx, plan.id, input.secondaryTemplateId, "SECONDARY", 1, now);
      }
      const created = this.attachCommitments(tx, plan);
      this.writeAudit(tx, {
        action: "DAILY_PLAN_CREATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        after: dailyPlanSnapshot(created),
        createdAt: now,
      });
      return plan.id;
    });
    return this.requireDailyPlanById(id);
  }

  updateDailyPlan(localDate: string, input: DailyPlanUpdateInput) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      if (plan.state !== "READY") throw new ApiError(409, "Only a ready plan can change its initial commitments.");
      if (this.commitmentsForPlan(tx, plan.id).some((commitment) => commitment.completedAt !== null)) {
        throw new ApiError(409, "A plan with completed commitment steps cannot change its initial commitments.");
      }
      this.requireActiveTemplate(tx, input.primaryTemplateId);
      if (input.secondaryTemplateId !== null) this.requireActiveTemplate(tx, input.secondaryTemplateId);
      const before = this.attachCommitments(tx, plan);
      const initial = before.commitments.filter((commitment) => commitment.role !== "ADDITIONAL");
      const same = input.firstMove === plan.firstMove
        && initial[0]?.templateId === input.primaryTemplateId
        && (initial[1]?.templateId ?? null) === input.secondaryTemplateId
        && before.commitments.length === initial.length;
      if (same) return;
      const now = this.now();
      tx.delete(todayCommitments).where(eq(todayCommitments.planId, plan.id)).run();
      this.createCommitmentSnapshot(tx, plan.id, input.primaryTemplateId, "PRIMARY", 0, now);
      if (input.secondaryTemplateId !== null) {
        this.createCommitmentSnapshot(tx, plan.id, input.secondaryTemplateId, "SECONDARY", 1, now);
      }
      tx.update(dailyPlans).set({
        firstMove: input.firstMove,
        revision: plan.revision + 1,
        updatedAt: now,
      }).where(eq(dailyPlans.id, plan.id)).run();
      const updated = this.attachCommitments(tx, {
        ...plan,
        firstMove: input.firstMove,
        revision: plan.revision + 1,
        updatedAt: now,
      });
      this.writeAudit(tx, {
        action: "DAILY_PLAN_UPDATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(before),
        after: dailyPlanSnapshot(updated),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
  }

  startDailyPlan(localDate: string, input: DailyPlanTransitionInput) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "READY") throw new ApiError(409, "Only ready plans can start.");
      const currentCommitmentId = this.firstOpenCommitmentId(tx, plan.id);
      return {
        firstMoveCompletedAt: input.occurredAt,
        currentCommitmentId,
        state: currentCommitmentId === null ? "PAUSED" as const : "ACTIVE" as const,
        pauseReason: currentCommitmentId === null ? "COMMITMENTS_COMPLETED" as const : null,
        nextReviewAt: currentCommitmentId === null ? null : input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  reviewDailyPlan(localDate: string, input: DailyPlanReviewInput) {
    return this.completeDailyPlanRound(localDate, input, true);
  }

  reviewDailyPlanNow(localDate: string, input: DailyPlanReviewInput) {
    return this.completeDailyPlanRound(localDate, input, false);
  }

  switchDailyPlanCommitment(localDate: string, input: DailyPlanReviewInput) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "ACTIVE") throw new ApiError(409, "Only active plans can switch the current commitment.");
      if (input.currentCommitmentId === plan.currentCommitmentId) {
        throw new ApiError(409, "Choose another unfinished commitment.");
      }
      this.requireOpenCommitment(tx, plan.id, input.currentCommitmentId);
      return {
        currentCommitmentId: input.currentCommitmentId,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  continueDailyPlan(localDate: string, input: DailyPlanReviewInput) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "PAUSED" || plan.pauseReason !== "TARGET_REACHED") {
        throw new ApiError(409, "Another round is available only after reaching the round target.");
      }
      this.requireOpenCommitment(tx, plan.id, input.currentCommitmentId);
      return {
        currentCommitmentId: input.currentCommitmentId,
        state: "ACTIVE" as const,
        pauseReason: null,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  addDailyPlanCommitment(localDate: string, input: DailyPlanAddCommitmentInput) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      if (plan.state !== "PAUSED") throw new ApiError(409, "Commitments can be added only while the plan is paused.");
      const openCommitmentId = this.firstOpenCommitmentId(tx, plan.id);
      if (plan.pauseReason === "TARGET_REACHED" && openCommitmentId !== null) {
        throw new ApiError(409, "Continue an unfinished commitment before adding another one.");
      }
      this.requireActiveTemplate(tx, input.templateId);
      const before = this.attachCommitments(tx, plan);
      const now = this.now();
      const position = before.commitments.reduce((max, commitment) => Math.max(max, commitment.position), -1) + 1;
      const commitmentId = this.createCommitmentSnapshot(tx, plan.id, input.templateId, "ADDITIONAL", position, now);
      const updatedRow: DailyPlanRow = {
        ...plan,
        currentCommitmentId: commitmentId,
        state: "ACTIVE",
        pauseReason: null,
        nextReviewAt: input.occurredAt + ReviewIntervalMs,
        revision: plan.revision + 1,
        updatedAt: now,
      };
      tx.update(dailyPlans).set({
        currentCommitmentId: commitmentId,
        state: "ACTIVE",
        pauseReason: null,
        nextReviewAt: updatedRow.nextReviewAt,
        revision: updatedRow.revision,
        updatedAt: now,
      }).where(eq(dailyPlans.id, plan.id)).run();
      const commitment = this.commitmentsForPlan(tx, plan.id).find((candidate) => candidate.id === commitmentId)!;
      this.writeAudit(tx, {
        action: "COMMITMENT_CREATED",
        entityType: "COMMITMENT",
        entityId: commitmentId,
        after: commitmentSnapshot(commitment),
        createdAt: now,
      });
      this.writeAudit(tx, {
        action: "DAILY_PLAN_UPDATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(before),
        after: dailyPlanSnapshot(this.attachCommitments(tx, updatedRow)),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
  }

  resetDailyPlan(localDate: string, input: DailyPlanTransitionInput) {
    return this.transitionDailyPlan(localDate, input, (_tx, plan) => {
      if (plan.state === "READY") throw new ApiError(409, "The plan is already ready.");
      return {
        firstMoveCompletedAt: null,
        currentCommitmentId: null,
        state: "READY" as const,
        pauseReason: null,
        completedRounds: 0,
        nextReviewAt: null,
        endedAt: null,
      };
    });
  }

  endDailyPlan(localDate: string, input: DailyPlanTransitionInput) {
    return this.transitionDailyPlan(localDate, input, (_tx, plan) => {
      if (plan.state === "ENDED") throw new ApiError(409, "The plan has already ended.");
      return {
        currentCommitmentId: null,
        state: "ENDED" as const,
        pauseReason: null,
        nextReviewAt: null,
        endedAt: input.occurredAt,
      };
    }, "DAILY_PLAN_ENDED");
  }

  setCommitmentStepCompleted(id: number, input: CommitmentStepCompletionInput) {
    const localDate = this.db.transaction((tx) => {
      const step = tx.select().from(todayCommitmentSteps).where(eq(todayCommitmentSteps.id, id)).get();
      if (!step) throw new ApiError(404, "Commitment step not found.");
      const commitment = tx.select().from(todayCommitments).where(eq(todayCommitments.id, step.commitmentId)).get();
      if (!commitment) throw new ApiError(404, "Commitment not found.");
      const plan = tx.select().from(dailyPlans).where(eq(dailyPlans.id, commitment.planId)).get();
      if (!plan) throw new ApiError(404, "Daily plan not found.");
      this.requireRevision(plan, input.expectedRevision);
      if ((step.completedAt !== null) === input.completed) return plan.localDate;

      const beforePlan = this.attachCommitments(tx, plan);
      const beforeCommitment = beforePlan.commitments.find((candidate) => candidate.id === commitment.id)!;
      const now = this.now();
      const completedAt = input.completed ? input.occurredAt : null;
      tx.update(todayCommitmentSteps).set({ completedAt, updatedAt: now })
        .where(eq(todayCommitmentSteps.id, id)).run();
      tx.update(todayCommitments).set({ updatedAt: now }).where(eq(todayCommitments.id, commitment.id)).run();

      const afterCommitment = this.commitmentsForPlan(tx, plan.id).find((candidate) => candidate.id === commitment.id)!;
      const patch: Partial<DailyPlanRow> = {};
      if (plan.state !== "ENDED" && beforeCommitment.completedAt === null && afterCommitment.completedAt !== null) {
        const nextCommitmentId = this.firstOpenCommitmentId(tx, plan.id);
        if (nextCommitmentId === null && (plan.state === "READY" || plan.state === "ACTIVE")) {
          Object.assign(patch, {
            currentCommitmentId: null,
            state: "PAUSED",
            pauseReason: "COMMITMENTS_COMPLETED",
            nextReviewAt: null,
          });
        } else if (plan.state === "ACTIVE" && plan.currentCommitmentId === commitment.id) {
          Object.assign(patch, {
            currentCommitmentId: nextCommitmentId,
            nextReviewAt: input.occurredAt + ReviewIntervalMs,
          });
        }
      }
      if (
        plan.state !== "ENDED"
        && beforeCommitment.completedAt !== null
        && afterCommitment.completedAt === null
        && plan.state === "PAUSED"
        && plan.pauseReason === "COMMITMENTS_COMPLETED"
      ) {
        const active = plan.firstMoveCompletedAt !== null;
        Object.assign(patch, {
          state: active ? "ACTIVE" : "READY",
          pauseReason: null,
          currentCommitmentId: active ? commitment.id : null,
          nextReviewAt: active ? input.occurredAt + ReviewIntervalMs : null,
        });
      }

      const updatedRow: DailyPlanRow = {
        ...plan,
        ...patch,
        revision: plan.revision + 1,
        updatedAt: now,
      };
      tx.update(dailyPlans).set({ ...patch, revision: updatedRow.revision, updatedAt: now })
        .where(eq(dailyPlans.id, plan.id)).run();
      this.writeAudit(tx, {
        action: input.completed ? "COMMITMENT_STEP_COMPLETED" : "COMMITMENT_STEP_REOPENED",
        entityType: "COMMITMENT_STEP",
        entityId: id,
        before: commitmentStepSnapshot(step, commitment),
        after: commitmentStepSnapshot({ ...step, completedAt }, commitment),
        createdAt: now,
      });
      if ((beforeCommitment.completedAt !== null) !== (afterCommitment.completedAt !== null)) {
        this.writeAudit(tx, {
          action: afterCommitment.completedAt === null ? "COMMITMENT_REOPENED" : "COMMITMENT_COMPLETED",
          entityType: "COMMITMENT",
          entityId: commitment.id,
          before: commitmentSnapshot(beforeCommitment),
          after: commitmentSnapshot(afterCommitment),
          createdAt: now,
        });
      }
      this.writeAudit(tx, {
        action: "DAILY_PLAN_UPDATED",
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(beforePlan),
        after: dailyPlanSnapshot(this.attachCommitments(tx, updatedRow)),
        createdAt: now,
      });
      return plan.localDate;
    });
    return this.getDailyPlan(localDate)!;
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

  private completeDailyPlanRound(localDate: string, input: DailyPlanReviewInput, requireDue: boolean) {
    return this.transitionDailyPlan(localDate, input, (tx, plan) => {
      if (plan.state !== "ACTIVE" || plan.nextReviewAt === null) {
        throw new ApiError(409, "Only active plans can be reviewed.");
      }
      if (requireDue && input.occurredAt < plan.nextReviewAt) throw new ApiError(409, "The next review is not due yet.");
      this.requireOpenCommitment(tx, plan.id, input.currentCommitmentId);
      const completedRounds = plan.completedRounds + 1;
      const targetReached = completedRounds >= TargetRounds;
      return {
        completedRounds,
        currentCommitmentId: targetReached ? null : input.currentCommitmentId,
        state: targetReached ? "PAUSED" as const : "ACTIVE" as const,
        pauseReason: targetReached ? "TARGET_REACHED" as const : null,
        nextReviewAt: targetReached ? null : input.occurredAt + ReviewIntervalMs,
      };
    });
  }

  private transitionDailyPlan(
    localDate: string,
    input: DailyPlanTransitionInput,
    transition: (tx: RecordsTransaction, plan: DailyPlanRow) => Partial<DailyPlanRow>,
    action: "DAILY_PLAN_UPDATED" | "DAILY_PLAN_ENDED" = "DAILY_PLAN_UPDATED",
  ) {
    this.db.transaction((tx) => {
      const plan = this.requireDailyPlan(tx, localDate);
      this.requireRevision(plan, input.expectedRevision);
      const before = this.attachCommitments(tx, plan);
      const patch = transition(tx, plan);
      const now = this.now();
      const updated = { ...plan, ...patch, revision: plan.revision + 1, updatedAt: now };
      tx.update(dailyPlans).set({ ...patch, revision: updated.revision, updatedAt: now })
        .where(eq(dailyPlans.id, plan.id)).run();
      this.writeAudit(tx, {
        action,
        entityType: "DAILY_PLAN",
        entityId: plan.id,
        before: dailyPlanSnapshot(before),
        after: dailyPlanSnapshot(this.attachCommitments(tx, updated)),
        createdAt: now,
      });
    });
    return this.getDailyPlan(localDate)!;
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

  private attachCommitments(reader: RecordsReader, plan: DailyPlanRow): DailyPlanResponse {
    return { ...plan, commitments: this.commitmentsForPlan(reader, plan.id) };
  }

  private commitmentsForPlan(reader: RecordsReader, planId: number): CommitmentResponse[] {
    const commitments = reader.select().from(todayCommitments)
      .where(eq(todayCommitments.planId, planId))
      .orderBy(asc(todayCommitments.position), asc(todayCommitments.id))
      .all();
    if (commitments.length === 0) return [];
    const steps = reader.select().from(todayCommitmentSteps)
      .where(inArray(todayCommitmentSteps.commitmentId, commitments.map((commitment) => commitment.id)))
      .orderBy(asc(todayCommitmentSteps.commitmentId), asc(todayCommitmentSteps.position), asc(todayCommitmentSteps.id))
      .all();
    const byCommitment = new Map<number, CommitmentStepRow[]>();
    for (const step of steps) {
      const values = byCommitment.get(step.commitmentId) ?? [];
      values.push(step);
      byCommitment.set(step.commitmentId, values);
    }
    return commitments.map((commitment) => {
      const commitmentSteps = byCommitment.get(commitment.id) ?? [];
      const completedAt = commitmentSteps.length > 0 && commitmentSteps.every((step) => step.completedAt !== null)
        ? Math.max(...commitmentSteps.map((step) => step.completedAt!))
        : null;
      return { ...commitment, completedAt, steps: commitmentSteps };
    });
  }

  private createCommitmentSnapshot(
    tx: RecordsTransaction,
    planId: number,
    templateId: number,
    role: "PRIMARY" | "SECONDARY" | "ADDITIONAL",
    position: number,
    now: number,
  ) {
    const template = this.requireActiveTemplate(tx, templateId);
    const templateSteps = tx.select().from(taskTemplateSteps)
      .where(eq(taskTemplateSteps.templateId, templateId))
      .orderBy(asc(taskTemplateSteps.position), asc(taskTemplateSteps.id))
      .all();
    const commitment = tx.insert(todayCommitments).values({
      planId,
      templateId,
      role,
      position,
      title: template.title,
      cadence: template.cadence,
      createdAt: now,
      updatedAt: now,
    }).returning({ id: todayCommitments.id }).get();
    tx.insert(todayCommitmentSteps).values(templateSteps.map((step) => ({
      commitmentId: commitment.id,
      title: step.title,
      position: step.position,
      completedAt: null,
      createdAt: now,
      updatedAt: now,
    }))).run();
    return commitment.id;
  }

  private firstOpenCommitmentId(reader: RecordsReader, planId: number) {
    return this.commitmentsForPlan(reader, planId).find((commitment) => commitment.completedAt === null)?.id ?? null;
  }

  private requireOpenCommitment(reader: RecordsReader, planId: number, commitmentId: number) {
    const commitment = this.commitmentsForPlan(reader, planId).find((candidate) => candidate.id === commitmentId);
    if (!commitment || commitment.completedAt !== null) {
      throw new ApiError(409, "Current commitment must be unfinished and belong to this plan.");
    }
    return commitment;
  }

  private requireTemplate(reader: RecordsReader, id: number) {
    const template = reader.select().from(taskTemplates).where(eq(taskTemplates.id, id)).get();
    if (!template) throw new ApiError(404, "Template not found.");
    return template;
  }

  private requireActiveTemplate(reader: RecordsReader, id: number) {
    const template = this.requireTemplate(reader, id);
    if (template.archived) throw new ApiError(409, "Archived templates cannot create commitments.");
    return template;
  }

  private requireDailyPlan(tx: RecordsTransaction, localDate: string) {
    const plan = tx.select().from(dailyPlans).where(eq(dailyPlans.localDate, localDate)).get();
    if (!plan) throw new ApiError(404, "Daily plan not found.");
    return plan;
  }

  private requireDailyPlanById(id: number) {
    const plan = this.db.select().from(dailyPlans).where(eq(dailyPlans.id, id)).get();
    if (!plan) throw new ApiError(404, "Daily plan not found.");
    return this.attachCommitments(this.db, plan);
  }

  private requireRevision(plan: DailyPlanRow, expectedRevision: number) {
    if (plan.revision !== expectedRevision) throw new ApiError(409, "Daily plan has changed. Refresh and try again.");
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

function commitmentSnapshot(commitment: CommitmentResponse) {
  return {
    id: commitment.id,
    planId: commitment.planId,
    templateId: commitment.templateId,
    role: commitment.role,
    position: commitment.position,
    title: commitment.title,
    cadence: commitment.cadence,
    completedAt: commitment.completedAt,
    steps: commitment.steps.map((step) => ({ id: step.id, title: step.title, completedAt: step.completedAt })),
  };
}

function commitmentStepSnapshot(step: CommitmentStepRow, commitment: CommitmentRow) {
  return {
    id: step.id,
    commitmentId: commitment.id,
    commitmentTitle: commitment.title,
    title: step.title,
    completedAt: step.completedAt,
  };
}

function dailyPlanSnapshot(plan: DailyPlanResponse) {
  return {
    id: plan.id,
    localDate: plan.localDate,
    firstMove: plan.firstMove,
    currentCommitmentId: plan.currentCommitmentId,
    state: plan.state,
    pauseReason: plan.pauseReason,
    completedRounds: plan.completedRounds,
    nextReviewAt: plan.nextReviewAt,
    commitmentIds: plan.commitments.map((commitment) => commitment.id),
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
