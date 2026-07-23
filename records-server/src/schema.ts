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
    goalPeriod: text("goal_period", { enum: ["DAY", "WEEK"] }),
    priority: integer("priority").notNull(),
    archived: integer("archived", { mode: "boolean" }).notNull().default(false),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [uniqueIndex("record_items_name_unique").on(sql`${table.name} COLLATE NOCASE`)],
);

export const recordWindows = sqliteTable(
  "record_windows",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    itemId: integer("item_id")
      .notNull()
      .references(() => recordItems.id, { onDelete: "cascade" }),
    startMinute: integer("start_minute").notNull(),
    endMinute: integer("end_minute").notNull(),
  },
  (table) => [index("record_windows_item_id_index").on(table.itemId)],
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

export const todoTasks = sqliteTable(
  "todo_tasks",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    title: text("title").notNull(),
    completedAt: integer("completed_at"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [index("todo_tasks_created_at_index").on(table.createdAt)],
);

export const todoSteps = sqliteTable(
  "todo_steps",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    taskId: integer("task_id")
      .notNull()
      .references(() => todoTasks.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    completedAt: integer("completed_at"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [index("todo_steps_task_created_at_index").on(table.taskId, table.createdAt)],
);

export const recordAuditLogs = sqliteTable(
  "record_audit_logs",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    action: text("action").notNull(),
    entityType: text("entity_type", { enum: ["ITEM", "ENTRY", "TASK", "STEP"] }).notNull(),
    entityId: integer("entity_id").notNull(),
    beforeJson: text("before_json"),
    afterJson: text("after_json"),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [index("record_audit_logs_created_at_index").on(table.createdAt)],
);
