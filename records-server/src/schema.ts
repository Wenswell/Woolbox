import { sql } from "drizzle-orm";
import { index, integer, real, sqliteTable, text, uniqueIndex } from "drizzle-orm/sqlite-core";

export const recordItems = sqliteTable(
  "record_items",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    name: text("name").notNull(),
    description: text("description").notNull(),
    mode: text("mode", { enum: ["ACCUMULATE", "MEASUREMENT"] }).notNull().default("ACCUMULATE"),
    valueType: text("value_type", { enum: ["NUMBER", "DURATION"] }).notNull(),
    defaultValue: real("default_value"),
    minValue: real("min_value"),
    maxValue: real("max_value"),
    stepValue: real("step_value").notNull(),
    unit: text("unit").notNull(),
    goalValue: real("goal_value"),
    goalPeriod: text("goal_period", { enum: ["DAY", "DAYS_7"] }),
    priority: integer("priority").notNull(),
    archived: integer("archived", { mode: "boolean" }).notNull().default(false),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [uniqueIndex("record_items_name_unique").on(sql`${table.name} COLLATE NOCASE`)],
);

export const recordEntries = sqliteTable(
  "record_entries",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    itemId: integer("item_id")
      .notNull()
      .references(() => recordItems.id, { onDelete: "cascade" }),
    occurredAt: integer("occurred_at").notNull(),
    localDate: text("local_date").notNull(),
    localMinute: integer("local_minute").notNull(),
    value: real("value").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [
    index("record_entries_item_id_index").on(table.itemId),
    index("record_entries_item_occurred_at_index").on(table.itemId, table.occurredAt),
    index("record_entries_local_date_minute_index").on(table.localDate, table.localMinute),
  ],
);

export const taskTemplates = sqliteTable(
  "task_templates",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    title: text("title").notNull(),
    cadence: text("cadence", { enum: ["MANUAL", "DAILY"] }).notNull(),
    archived: integer("archived", { mode: "boolean" }).notNull().default(false),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [index("task_templates_cadence_archived_index").on(table.cadence, table.archived)],
);

export const taskTemplateSteps = sqliteTable(
  "task_template_steps",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    templateId: integer("template_id")
      .notNull()
      .references(() => taskTemplates.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    position: integer("position").notNull(),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [
    index("task_template_steps_template_position_index").on(table.templateId, table.position),
  ],
);

export const dailyPlans = sqliteTable(
  "daily_plans",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    localDate: text("local_date").notNull(),
    firstMove: text("first_move").notNull(),
    firstMoveCompletedAt: integer("first_move_completed_at"),
    currentCommitmentId: integer("current_commitment_id"),
    state: text("state", { enum: ["READY", "ACTIVE", "PAUSED", "ENDED"] }).notNull(),
    pauseReason: text("pause_reason", { enum: ["COMMITMENTS_COMPLETED", "TARGET_REACHED"] }),
    completedRounds: integer("completed_rounds").notNull(),
    nextReviewAt: integer("next_review_at"),
    revision: integer("revision").notNull(),
    endedAt: integer("ended_at"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [uniqueIndex("daily_plans_local_date_unique").on(table.localDate)],
);

export const todayCommitments = sqliteTable(
  "today_commitments",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    planId: integer("plan_id")
      .notNull()
      .references(() => dailyPlans.id, { onDelete: "cascade" }),
    templateId: integer("template_id")
      .references(() => taskTemplates.id, { onDelete: "set null" }),
    role: text("role", { enum: ["PRIMARY", "SECONDARY", "ADDITIONAL"] }).notNull(),
    position: integer("position").notNull(),
    title: text("title").notNull(),
    cadence: text("cadence", { enum: ["MANUAL", "DAILY"] }).notNull(),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [index("today_commitments_plan_position_index").on(table.planId, table.position)],
);

export const todayCommitmentSteps = sqliteTable(
  "today_commitment_steps",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    commitmentId: integer("commitment_id")
      .notNull()
      .references(() => todayCommitments.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    position: integer("position").notNull(),
    completedAt: integer("completed_at"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [index("today_commitment_steps_commitment_position_index").on(table.commitmentId, table.position)],
);

export const recordAuditLogs = sqliteTable(
  "record_audit_logs",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    action: text("action").notNull(),
    entityType: text("entity_type", { enum: ["ITEM", "ENTRY", "TEMPLATE", "TEMPLATE_STEP", "COMMITMENT", "COMMITMENT_STEP", "DAILY_PLAN"] }).notNull(),
    entityId: integer("entity_id").notNull(),
    beforeJson: text("before_json"),
    afterJson: text("after_json"),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [index("record_audit_logs_created_at_index").on(table.createdAt)],
);
