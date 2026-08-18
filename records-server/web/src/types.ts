export type ValueType = "NUMBER" | "DURATION";
export type RecordMode = "ACCUMULATE" | "MEASUREMENT";
export type GoalPeriod = "DAY" | "DAYS_7";
export type TaskRepeatMode = "ONCE" | "DAILY";
export type DailyPlanState = "READY" | "ACTIVE" | "ENOUGH" | "ENDED";
export interface RecordItem { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; createdAt: number; updatedAt: number }
export interface RecordEntry { id: number; itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number; createdAt: number; itemName: string; itemMode: RecordMode; itemUnit: string; itemValueType: ValueType }
export type ItemInput = Omit<RecordItem, "id" | "archived" | "createdAt" | "updatedAt">;
export interface TodoStep { id: number; taskId: number; title: string; position: number; completedAt: number | null; createdAt: number; updatedAt: number }
export interface TodoTask { id: number; title: string; repeatMode: TaskRepeatMode; completedAt: number | null; createdAt: number; updatedAt: number; steps: TodoStep[] }
export type TaskCreateInput = { title: string; repeatMode: TaskRepeatMode; localDate: string; steps: Array<{ title: string }> };
export type TaskWriteInput = { title: string; repeatMode: TaskRepeatMode; localDate: string };
export interface DailyPlan { id: number; localDate: string; firstMove: string; firstMoveCompletedAt: number | null; primaryTaskId: number | null; secondaryTaskId: number | null; currentTaskId: number | null; state: DailyPlanState; completedRounds: number; nextReviewAt: number | null; revision: number; endedAt: number | null; createdAt: number; updatedAt: number }
export type AuditAction = "ITEM_CREATED" | "ITEM_UPDATED" | "ITEM_ARCHIVED" | "ITEM_RESTORED" | "ITEM_DELETED" | "ENTRY_CREATED" | "ENTRY_DELETED" | "TASK_CREATED" | "TASK_UPDATED" | "TASK_COMPLETED" | "TASK_REOPENED" | "TASK_DELETED" | "TASK_STEPS_REORDERED" | "STEP_CREATED" | "STEP_UPDATED" | "STEP_COMPLETED" | "STEP_REOPENED" | "STEP_DELETED" | "DAILY_PLAN_CREATED" | "DAILY_PLAN_UPDATED" | "DAILY_PLAN_ENDED";
export interface ItemAuditSnapshot { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; entryCount?: number }
export interface EntryAuditSnapshot { id: number; itemId: number; itemName: string; occurredAt: number; localDate: string; localMinute: number; value: number }
export interface TaskAuditSnapshot { id: number; title: string; repeatMode: TaskRepeatMode; localDate: string; completedAt: number | null; stepCount?: number; stepIds?: number[] }
export interface StepAuditSnapshot { id: number; taskId: number; taskTitle: string; repeatMode: TaskRepeatMode; localDate: string; title: string; completedAt: number | null }
export interface DailyPlanAuditSnapshot { id: number; localDate: string; firstMove: string; primaryTaskId: number | null; secondaryTaskId: number | null; currentTaskId: number | null; state: string; completedRounds: number; nextReviewAt: number | null }
export interface RecordAuditLog { id: number; action: AuditAction; entityType: "ITEM" | "ENTRY" | "TASK" | "STEP" | "DAILY_PLAN"; entityId: number; before: ItemAuditSnapshot | EntryAuditSnapshot | TaskAuditSnapshot | StepAuditSnapshot | DailyPlanAuditSnapshot | null; after: ItemAuditSnapshot | EntryAuditSnapshot | TaskAuditSnapshot | StepAuditSnapshot | DailyPlanAuditSnapshot | null; createdAt: number }
export interface AuditLogPage { logs: RecordAuditLog[]; nextBeforeId: number | null }
