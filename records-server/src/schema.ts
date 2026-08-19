import { sql } from "drizzle-orm";
import { type AnySQLiteColumn, index, integer, real, sqliteTable, text, uniqueIndex } from "drizzle-orm/sqlite-core";

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

export const todayTasks = sqliteTable(
  "today_tasks",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    localDate: text("local_date").notNull(),
    templateId: integer("template_id")
      .references(() => taskTemplates.id, { onDelete: "set null" }),
    sourceTaskId: integer("source_task_id")
      .references((): AnySQLiteColumn => todayTasks.id, { onDelete: "set null" }),
    title: text("title").notNull(),
    position: integer("position").notNull(),
    completedAt: integer("completed_at"),
    revision: integer("revision").notNull(),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [
    index("today_tasks_date_position_index").on(table.localDate, table.position),
    uniqueIndex("today_tasks_date_source_unique").on(table.localDate, table.sourceTaskId),
  ],
);

export const todayTaskSteps = sqliteTable(
  "today_task_steps",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    taskId: integer("task_id")
      .notNull()
      .references(() => todayTasks.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    position: integer("position").notNull(),
    completedAt: integer("completed_at"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [index("today_task_steps_task_position_index").on(table.taskId, table.position)],
);

export const dailyFocus = sqliteTable(
  "daily_focus",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    localDate: text("local_date").notNull(),
    firstMove: text("first_move").notNull(),
    firstMoveCompletedAt: integer("first_move_completed_at"),
    primaryTaskId: integer("primary_task_id")
      .notNull()
      .references(() => todayTasks.id, { onDelete: "restrict" }),
    secondaryTaskId: integer("secondary_task_id")
      .references(() => todayTasks.id, { onDelete: "restrict" }),
    currentTaskId: integer("current_task_id")
      .references(() => todayTasks.id, { onDelete: "restrict" }),
    state: text("state", { enum: ["READY", "ACTIVE", "PAUSED", "ENDED"] }).notNull(),
    pauseReason: text("pause_reason", { enum: ["FOCUS_CLEARED", "TARGET_REACHED"] }),
    completedRounds: integer("completed_rounds").notNull(),
    nextReviewAt: integer("next_review_at"),
    revision: integer("revision").notNull(),
    endedAt: integer("ended_at"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [uniqueIndex("daily_focus_local_date_unique").on(table.localDate)],
);

export const recordAuditLogs = sqliteTable(
  "record_audit_logs",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    action: text("action").notNull(),
    entityType: text("entity_type", { enum: ["ITEM", "ENTRY", "TEMPLATE", "TEMPLATE_STEP", "TODAY_TASK", "TODAY_TASK_STEP", "FOCUS"] }).notNull(),
    entityId: integer("entity_id").notNull(),
    beforeJson: text("before_json"),
    afterJson: text("after_json"),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [index("record_audit_logs_created_at_index").on(table.createdAt)],
);
