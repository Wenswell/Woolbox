import type {
  EntryAuditSnapshot,
  ItemAuditSnapshot,
  RecordAuditLog,
  StepAuditSnapshot,
  TaskAuditSnapshot,
} from "../types";
import { minute } from "./format";

export function auditSubject(log: RecordAuditLog): string {
  const snapshot = log.after ?? log.before;
  if (!snapshot) {
    return `${log.entityType} #${log.entityId}`;
  }
  if ("name" in snapshot) {
    return `Record item: ${snapshot.name}`;
  }
  if ("itemName" in snapshot) {
    return `Entry: ${snapshot.itemName}`;
  }
  return "taskTitle" in snapshot
    ? `Subtask: ${snapshot.taskTitle}`
    : `Task: ${snapshot.title}`;
}

export function actionLabel(action: RecordAuditLog["action"]): string {
  return {
    ITEM_CREATED: "Created item",
    ITEM_UPDATED: "Updated item",
    ITEM_ARCHIVED: "Archived item",
    ITEM_RESTORED: "Restored item",
    ITEM_DELETED: "Deleted item",
    ENTRY_CREATED: "Created entry",
    ENTRY_DELETED: "Deleted entry",
    TASK_CREATED: "Created task",
    TASK_UPDATED: "Updated task",
    TASK_COMPLETED: "Completed task",
    TASK_REOPENED: "Reopened task",
    TASK_DELETED: "Deleted task",
    STEP_CREATED: "Created subtask",
    STEP_UPDATED: "Updated subtask",
    STEP_COMPLETED: "Completed subtask",
    STEP_REOPENED: "Reopened subtask",
    STEP_DELETED: "Deleted subtask",
  }[action];
}

export function actionTone(
  action: RecordAuditLog["action"],
): "neutral" | "accent" | "danger" | "warning" | "muted" {
  if (action.endsWith("_DELETED") || action.endsWith("_ARCHIVED")) return "danger";
  if (action.endsWith("_CREATED") || action.endsWith("_RESTORED") || action.endsWith("_COMPLETED")) {
    return "accent";
  }
  if (action.endsWith("_REOPENED")) return "warning";
  return "neutral";
}

export function auditChanges(log: RecordAuditLog): string {
  const before = log.before;
  const after = log.after;

  if (log.action === "ITEM_CREATED" && after && "name" in after) {
    return itemDescription(after);
  }
  if (log.action === "ITEM_DELETED" && before && "name" in before) {
    return itemDescription(before);
  }
  if (log.action === "ENTRY_CREATED" && after && isEntrySnapshot(after)) {
    return entryDescription(after);
  }
  if (log.action === "ENTRY_DELETED" && before && isEntrySnapshot(before)) {
    return entryDescription(before);
  }
  if (log.action === "TASK_CREATED" && after && isTaskSnapshot(after)) {
    return taskDescription(after);
  }
  if (log.action === "TASK_DELETED" && before && isTaskSnapshot(before)) {
    return taskDescription(before);
  }
  if (log.action === "STEP_CREATED" && after && isStepSnapshot(after)) {
    return stepDescription(after);
  }
  if (log.action === "STEP_DELETED" && before && isStepSnapshot(before)) {
    return stepDescription(before);
  }
  if (
    (log.action === "TASK_COMPLETED" || log.action === "TASK_REOPENED") &&
    after &&
    isTaskSnapshot(after)
  ) {
    return after.completedAt === null ? "Status: Open" : "Status: Completed";
  }
  if (
    (log.action === "STEP_COMPLETED" || log.action === "STEP_REOPENED") &&
    after &&
    isStepSnapshot(after)
  ) {
    return after.completedAt === null ? "Status: Open" : "Status: Completed";
  }
  if (
    log.action === "TASK_UPDATED" &&
    before &&
    after &&
    isTaskSnapshot(before) &&
    isTaskSnapshot(after)
  ) {
    return fieldChange("Title", before.title, after.title) ?? "No visible field changes.";
  }
  if (
    log.action === "STEP_UPDATED" &&
    before &&
    after &&
    isStepSnapshot(before) &&
    isStepSnapshot(after)
  ) {
    return fieldChange("Title", before.title, after.title) ?? "No visible field changes.";
  }
  if (log.action === "ITEM_ARCHIVED") {
    return "Status: Active to Archived";
  }
  if (log.action === "ITEM_RESTORED") {
    return "Status: Archived to Active";
  }
  if (!before || !after || !("name" in before) || !("name" in after)) {
    return "No details.";
  }

  const changes = [
    fieldChange("Name", before.name, after.name),
    fieldChange("Description", before.description, after.description),
    fieldChange("Mode", before.mode, after.mode),
    fieldChange("Type", before.valueType, after.valueType),
    fieldChange("Default", before.defaultValue, after.defaultValue),
    fieldChange("Minimum", before.minValue, after.minValue),
    fieldChange("Maximum", before.maxValue, after.maxValue),
    fieldChange("Step", before.stepValue, after.stepValue),
    fieldChange("Unit", before.unit, after.unit),
    fieldChange("Goal", itemGoal(before), itemGoal(after)),
    fieldChange("Priority", before.priority, after.priority),
    fieldChange("Suggested windows", itemWindows(before), itemWindows(after)),
  ].filter((value): value is string => value !== null);

  return changes.join("; ") || "No visible field changes.";
}

function itemDescription(item: ItemAuditSnapshot): string {
  return item.mode === "MEASUREMENT"
    ? `${item.name}; ${item.minValue}–${item.maxValue} ${item.unit}; step ${item.stepValue}`
    : `${item.name}; ${itemGoal(item)}; step ${item.stepValue} ${item.unit}`;
}

function entryDescription(entry: EntryAuditSnapshot): string {
  return `${entry.itemName}; ${entry.localDate} ${minute(entry.localMinute)}; ${entry.value}`;
}

function taskDescription(task: TaskAuditSnapshot): string {
  return task.stepCount === undefined
    ? task.title
    : `${task.title}; ${task.stepCount} subtasks`;
}

function stepDescription(step: StepAuditSnapshot): string {
  return `${step.taskTitle}; ${step.title}`;
}

function isEntrySnapshot(
  snapshot: NonNullable<RecordAuditLog["before"]>,
): snapshot is EntryAuditSnapshot {
  return "itemName" in snapshot;
}

function isTaskSnapshot(
  snapshot: NonNullable<RecordAuditLog["before"]>,
): snapshot is TaskAuditSnapshot {
  return "title" in snapshot && !("taskTitle" in snapshot) && !("name" in snapshot);
}

function isStepSnapshot(
  snapshot: NonNullable<RecordAuditLog["before"]>,
): snapshot is StepAuditSnapshot {
  return "taskTitle" in snapshot;
}

function itemGoal(item: ItemAuditSnapshot): string {
  return item.goalValue === null
    ? "No target"
    : `Target ${item.goalValue} ${item.unit} / ${item.goalPeriod === "DAY" ? "day" : "7 days"}`;
}

function itemWindows(item: ItemAuditSnapshot): string {
  return (
    item.windows
      .map((window) => `${minute(window.startMinute)}-${minute(window.endMinute)}`)
      .join(", ") || "None"
  );
}

function fieldChange(label: string, before: unknown, after: unknown): string | null {
  return String(before) === String(after)
    ? null
    : `${label}: ${String(before)} to ${String(after)}`;
}
