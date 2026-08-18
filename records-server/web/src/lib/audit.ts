import type {
  CommitmentAuditSnapshot,
  CommitmentStepAuditSnapshot,
  DailyPlanAuditSnapshot,
  EntryAuditSnapshot,
  ItemAuditSnapshot,
  RecordAuditLog,
  TemplateAuditSnapshot,
} from "../types";
import { minute } from "./format";

export function auditSubject(log: RecordAuditLog): string {
  const snapshot = log.after ?? log.before;
  if (!snapshot) return `${log.entityType} #${log.entityId}`;
  if (log.entityType === "ITEM" && isItemSnapshot(snapshot)) return `Record item: ${snapshot.name}`;
  if (log.entityType === "ENTRY" && isEntrySnapshot(snapshot)) return `Entry: ${snapshot.itemName}`;
  if (log.entityType === "TEMPLATE" && isTemplateSnapshot(snapshot)) return `Template: ${snapshot.title}`;
  if (log.entityType === "COMMITMENT" && isCommitmentSnapshot(snapshot)) return `Commitment: ${snapshot.title}`;
  if (log.entityType === "COMMITMENT_STEP" && isCommitmentStepSnapshot(snapshot)) return `Step: ${snapshot.commitmentTitle}`;
  if (log.entityType === "DAILY_PLAN" && isDailyPlanSnapshot(snapshot)) return `Daily plan: ${snapshot.localDate}`;
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
    TEMPLATE_CREATED: "Created template",
    TEMPLATE_UPDATED: "Updated template",
    TEMPLATE_ARCHIVED: "Archived template",
    TEMPLATE_RESTORED: "Restored template",
    TEMPLATE_DELETED: "Deleted template",
    COMMITMENT_CREATED: "Added commitment",
    COMMITMENT_COMPLETED: "Completed commitment",
    COMMITMENT_REOPENED: "Reopened commitment",
    COMMITMENT_STEP_COMPLETED: "Completed step",
    COMMITMENT_STEP_REOPENED: "Reopened step",
    DAILY_PLAN_CREATED: "Created daily plan",
    DAILY_PLAN_UPDATED: "Updated daily plan",
    DAILY_PLAN_ENDED: "Ended daily plan",
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
  if (log.entityType === "COMMITMENT" && isCommitmentSnapshot(after ?? before)) {
    const commitment = (after ?? before) as CommitmentAuditSnapshot;
    return `${commitment.title}; ${commitment.steps.length} steps; ${commitment.completedAt === null ? "Open" : "Completed"}`;
  }
  if (log.entityType === "COMMITMENT_STEP" && isCommitmentStepSnapshot(after ?? before)) {
    const step = (after ?? before) as CommitmentStepAuditSnapshot;
    return `${step.commitmentTitle}; ${step.title}; ${step.completedAt === null ? "Open" : "Completed"}`;
  }
  if (log.action === "DAILY_PLAN_CREATED" && isDailyPlanSnapshot(after)) return dailyPlanDescription(after);
  if (log.action === "DAILY_PLAN_ENDED" && isDailyPlanSnapshot(after)) return `Status: ${after.state}`;
  if (log.action === "DAILY_PLAN_UPDATED" && isDailyPlanSnapshot(before) && isDailyPlanSnapshot(after)) {
    return [
      fieldChange("First move", before.firstMove, after.firstMove),
      fieldChange("Current commitment", before.currentCommitmentId, after.currentCommitmentId),
      fieldChange("Status", before.state, after.state),
      fieldChange("Pause reason", before.pauseReason, after.pauseReason),
      fieldChange("Completed rounds", before.completedRounds, after.completedRounds),
      fieldChange("Next review", before.nextReviewAt, after.nextReviewAt),
      fieldChange("Commitments", before.commitmentIds.join(", "), after.commitmentIds.join(", ")),
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

function templateDescription(template: TemplateAuditSnapshot): string {
  return `${template.title}; ${template.cadence === "DAILY" ? "Daily" : "Manual"}; ${template.steps.length} steps`;
}

function dailyPlanDescription(plan: DailyPlanAuditSnapshot): string {
  return `${plan.localDate}; first move: ${plan.firstMove}; ${plan.commitmentIds.length} commitments`;
}

function isItemSnapshot(snapshot: unknown): snapshot is ItemAuditSnapshot {
  return isObject(snapshot) && "name" in snapshot && "mode" in snapshot;
}

function isEntrySnapshot(snapshot: unknown): snapshot is EntryAuditSnapshot {
  return isObject(snapshot) && "itemName" in snapshot;
}

function isTemplateSnapshot(snapshot: unknown): snapshot is TemplateAuditSnapshot {
  return isObject(snapshot) && "title" in snapshot && "cadence" in snapshot && "archived" in snapshot;
}

function isCommitmentSnapshot(snapshot: unknown): snapshot is CommitmentAuditSnapshot {
  return isObject(snapshot) && "title" in snapshot && "role" in snapshot;
}

function isCommitmentStepSnapshot(snapshot: unknown): snapshot is CommitmentStepAuditSnapshot {
  return isObject(snapshot) && "commitmentTitle" in snapshot;
}

function isDailyPlanSnapshot(snapshot: unknown): snapshot is DailyPlanAuditSnapshot {
  return isObject(snapshot) && "localDate" in snapshot && "firstMove" in snapshot;
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
