import type {
  DailyFocusAuditSnapshot,
  EntryAuditSnapshot,
  GoalAuditSnapshot,
  GoalEntryAuditSnapshot,
  GoalSubgoalAuditSnapshot,
  ItemAuditSnapshot,
  RecordAuditLog,
  TemplateAuditSnapshot,
  TodayTaskAuditSnapshot,
  TodayTaskStepAuditSnapshot,
} from "../types";
import { minute } from "./format";

export function auditSubject(log: RecordAuditLog): string {
  const snapshot = log.after ?? log.before;
  if (!snapshot) return `${log.entityType} #${log.entityId}`;
  if (log.entityType === "ITEM" && isItemSnapshot(snapshot)) return `Record item: ${snapshot.name}`;
  if (log.entityType === "ENTRY" && isEntrySnapshot(snapshot)) return `Entry: ${snapshot.itemName}`;
  if (log.entityType === "GOAL" && isGoalSnapshot(snapshot)) return `Goal: ${snapshot.title}`;
  if (log.entityType === "GOAL_ENTRY" && isGoalEntrySnapshot(snapshot)) return `Goal progress: Goal #${snapshot.goalId}`;
  if (log.entityType === "GOAL_SUBGOAL" && isGoalSubgoalSnapshot(snapshot)) return `Subgoal: ${snapshot.title}`;
  if (log.entityType === "TEMPLATE" && isTemplateSnapshot(snapshot)) return `Template: ${snapshot.title}`;
  if (log.entityType === "TODAY_TASK" && isTodayTaskSnapshot(snapshot)) return `Today task: ${snapshot.title}`;
  if (log.entityType === "TODAY_TASK_STEP" && isTodayTaskStepSnapshot(snapshot)) return `Step: ${snapshot.taskTitle}`;
  if (log.entityType === "FOCUS" && isDailyFocusSnapshot(snapshot)) return `Focus: ${snapshot.localDate}`;
  return `${log.entityType} #${log.entityId}`;
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
    GOAL_CREATED: "Created goal",
    GOAL_UPDATED: "Updated goal",
    GOAL_ARCHIVED: "Archived goal",
    GOAL_RESTORED: "Restored goal",
    GOAL_DELETED: "Deleted goal",
    GOAL_ENTRY_CREATED: "Recorded goal progress",
    GOAL_SUBGOAL_COMPLETED: "Completed subgoal",
    GOAL_SUBGOAL_REOPENED: "Reopened subgoal",
    TEMPLATE_CREATED: "Created template",
    TEMPLATE_UPDATED: "Updated template",
    TEMPLATE_ARCHIVED: "Archived template",
    TEMPLATE_RESTORED: "Restored template",
    TEMPLATE_DELETED: "Deleted template",
    TODAY_TASK_CREATED: "Added Today task",
    TODAY_TASK_COMPLETED: "Completed task",
    TODAY_TASK_REOPENED: "Reopened task",
    TODAY_TASK_STEP_COMPLETED: "Completed step",
    TODAY_TASK_STEP_REOPENED: "Reopened step",
    FOCUS_CREATED: "Created Focus",
    FOCUS_UPDATED: "Updated Focus",
    FOCUS_ENDED: "Ended Focus",
  }[action];
}

export function actionTone(
  action: RecordAuditLog["action"],
): "neutral" | "accent" | "danger" | "warning" | "muted" {
  if (action.endsWith("_DELETED") || action.endsWith("_ARCHIVED")) return "danger";
  if (action.endsWith("_CREATED") || action.endsWith("_RESTORED") || action.endsWith("_COMPLETED")) return "accent";
  if (action.endsWith("_REOPENED")) return "warning";
  return "neutral";
}

export function auditChanges(log: RecordAuditLog): string {
  const before = log.before;
  const after = log.after;

  if ((log.action === "ITEM_CREATED" || log.action === "ITEM_DELETED") && isItemSnapshot(after ?? before)) {
    return itemDescription((after ?? before) as ItemAuditSnapshot);
  }
  if ((log.action === "ENTRY_CREATED" || log.action === "ENTRY_DELETED") && isEntrySnapshot(after ?? before)) {
    return entryDescription((after ?? before) as EntryAuditSnapshot);
  }
  if ((log.action === "GOAL_CREATED" || log.action === "GOAL_DELETED") && isGoalSnapshot(after ?? before)) {
    return goalDescription((after ?? before) as GoalAuditSnapshot);
  }
  if (log.action === "GOAL_UPDATED" && isGoalSnapshot(before) && isGoalSnapshot(after)) {
    return [
      fieldChange("Title", before.title, after.title),
      fieldChange("Description", before.description, after.description),
      fieldChange("Unit", before.unit, after.unit),
      fieldChange("Default", before.defaultValue, after.defaultValue),
      fieldChange("Step", before.stepValue, after.stepValue),
      fieldChange("Start date", before.startDate, after.startDate),
      fieldChange("End date", before.endDate, after.endDate),
      fieldChange("Subgoals", goalSubgoals(before), goalSubgoals(after)),
    ].filter((value): value is string => value !== null).join("; ") || "No visible field changes.";
  }
  if (log.action === "GOAL_ARCHIVED") return "Status: Active to Archived";
  if (log.action === "GOAL_RESTORED") return "Status: Archived to Active";
  if (log.action === "GOAL_ENTRY_CREATED" && isGoalEntrySnapshot(after)) {
    return `${after.localDate} ${minute(after.localMinute)}; +${after.value}`;
  }
  if ((log.action === "GOAL_SUBGOAL_COMPLETED" || log.action === "GOAL_SUBGOAL_REOPENED") && isGoalSubgoalSnapshot(after ?? before)) {
    const subgoal = (after ?? before) as GoalSubgoalAuditSnapshot;
    return `${subgoal.title}; target ${subgoal.targetValue}; ${subgoal.completedAt === null ? "Open" : "Completed"}`;
  }
  if ((log.action === "TEMPLATE_CREATED" || log.action === "TEMPLATE_DELETED") && isTemplateSnapshot(after ?? before)) {
    return templateDescription((after ?? before) as TemplateAuditSnapshot);
  }
  if (log.action === "TEMPLATE_UPDATED" && isTemplateSnapshot(before) && isTemplateSnapshot(after)) {
    return [
      fieldChange("Title", before.title, after.title),
      fieldChange("Cadence", before.cadence, after.cadence),
      fieldChange("Steps", before.steps.join(" · "), after.steps.join(" · ")),
    ].filter((value): value is string => value !== null).join("; ") || "No visible field changes.";
  }
  if (log.action === "ITEM_ARCHIVED" || log.action === "TEMPLATE_ARCHIVED") return "Status: Active to Archived";
  if (log.action === "ITEM_RESTORED" || log.action === "TEMPLATE_RESTORED") return "Status: Archived to Active";
  if (log.entityType === "TODAY_TASK" && isTodayTaskSnapshot(after ?? before)) {
    const task = (after ?? before) as TodayTaskAuditSnapshot;
    return `${task.title}; ${task.steps.length} steps; ${task.completedAt === null ? "Open" : "Completed"}`;
  }
  if (log.entityType === "TODAY_TASK_STEP" && isTodayTaskStepSnapshot(after ?? before)) {
    const step = (after ?? before) as TodayTaskStepAuditSnapshot;
    return `${step.taskTitle}; ${step.title}; ${step.completedAt === null ? "Open" : "Completed"}`;
  }
  if (log.action === "FOCUS_CREATED" && isDailyFocusSnapshot(after)) return dailyFocusDescription(after);
  if (log.action === "FOCUS_ENDED" && isDailyFocusSnapshot(after)) return `Status: ${after.state}`;
  if (log.action === "FOCUS_UPDATED" && isDailyFocusSnapshot(before) && isDailyFocusSnapshot(after)) {
    return [
      fieldChange("First move", before.firstMove, after.firstMove),
      fieldChange("Primary task", before.primaryTaskId, after.primaryTaskId),
      fieldChange("Secondary task", before.secondaryTaskId, after.secondaryTaskId),
      fieldChange("Current task", before.currentTaskId, after.currentTaskId),
      fieldChange("Status", before.state, after.state),
      fieldChange("Pause reason", before.pauseReason, after.pauseReason),
      fieldChange("Completed rounds", before.completedRounds, after.completedRounds),
      fieldChange("Next review", before.nextReviewAt, after.nextReviewAt),
    ].filter((value): value is string => value !== null).join("; ") || "No visible field changes.";
  }
  if (isItemSnapshot(before) && isItemSnapshot(after)) {
    return [
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
    ].filter((value): value is string => value !== null).join("; ") || "No visible field changes.";
  }
  return "No details.";
}

function itemDescription(item: ItemAuditSnapshot): string {
  return item.mode === "MEASUREMENT"
    ? `${item.name}; ${item.minValue}-${item.maxValue} ${item.unit}; step ${item.stepValue}`
    : `${item.name}; ${itemGoal(item)}; step ${item.stepValue} ${item.unit}`;
}

function entryDescription(entry: EntryAuditSnapshot): string {
  return `${entry.itemName}; ${entry.localDate} ${minute(entry.localMinute)}; ${entry.value}`;
}

function goalDescription(goal: GoalAuditSnapshot): string {
  return `${goal.title}; ${goal.startDate} to ${goal.endDate}; ${goal.subgoals.length} subgoals`;
}

function goalSubgoals(goal: GoalAuditSnapshot): string {
  return goal.subgoals.map((subgoal) => `${subgoal.title}: ${subgoal.targetValue}`).join(" · ");
}

function templateDescription(template: TemplateAuditSnapshot): string {
  return `${template.title}; ${template.cadence === "DAILY" ? "Daily" : "Manual"}; ${template.steps.length} steps`;
}

function dailyFocusDescription(focus: DailyFocusAuditSnapshot): string {
  return `${focus.localDate}; first move: ${focus.firstMove}; primary task #${focus.primaryTaskId}`;
}

function isItemSnapshot(snapshot: unknown): snapshot is ItemAuditSnapshot {
  return isObject(snapshot) && "name" in snapshot && "mode" in snapshot;
}

function isEntrySnapshot(snapshot: unknown): snapshot is EntryAuditSnapshot {
  return isObject(snapshot) && "itemName" in snapshot;
}

function isGoalSnapshot(snapshot: unknown): snapshot is GoalAuditSnapshot {
  return isObject(snapshot) && "title" in snapshot && "startDate" in snapshot && "endDate" in snapshot && "subgoals" in snapshot;
}

function isGoalEntrySnapshot(snapshot: unknown): snapshot is GoalEntryAuditSnapshot {
  return isObject(snapshot) && "goalId" in snapshot && "localDate" in snapshot && "localMinute" in snapshot && "value" in snapshot && !("title" in snapshot);
}

function isGoalSubgoalSnapshot(snapshot: unknown): snapshot is GoalSubgoalAuditSnapshot {
  return isObject(snapshot) && "goalId" in snapshot && "title" in snapshot && "targetValue" in snapshot && "completedAt" in snapshot;
}

function isTemplateSnapshot(snapshot: unknown): snapshot is TemplateAuditSnapshot {
  return isObject(snapshot) && "title" in snapshot && "cadence" in snapshot && "archived" in snapshot;
}

function isTodayTaskSnapshot(snapshot: unknown): snapshot is TodayTaskAuditSnapshot {
  return isObject(snapshot) && "title" in snapshot && "localDate" in snapshot && "steps" in snapshot;
}

function isTodayTaskStepSnapshot(snapshot: unknown): snapshot is TodayTaskStepAuditSnapshot {
  return isObject(snapshot) && "taskTitle" in snapshot;
}

function isDailyFocusSnapshot(snapshot: unknown): snapshot is DailyFocusAuditSnapshot {
  return isObject(snapshot) && "localDate" in snapshot && "firstMove" in snapshot && "primaryTaskId" in snapshot;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object";
}

function itemGoal(item: ItemAuditSnapshot): string {
  return item.goalValue === null ? "No target" : `Target ${item.goalValue} ${item.unit} / ${item.goalPeriod === "DAY" ? "day" : "7 days"}`;
}

function fieldChange(label: string, before: unknown, after: unknown): string | null {
  return String(before) === String(after) ? null : `${label}: ${String(before)} to ${String(after)}`;
}
