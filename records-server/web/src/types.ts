export type ValueType = "NUMBER" | "DURATION";
export type RecordMode = "ACCUMULATE" | "MEASUREMENT";
export type GoalPeriod = "DAY" | "DAYS_7";
export type TemplateCadence = "MANUAL" | "DAILY";
export type DailyFocusState = "READY" | "ACTIVE" | "PAUSED" | "ENDED";
export type DailyFocusPauseReason = "FOCUS_CLEARED" | "TARGET_REACHED";

export interface RecordItem { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; createdAt: number; updatedAt: number }
export interface RecordEntry { id: number; itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number; createdAt: number; itemName: string; itemMode: RecordMode; itemUnit: string; itemValueType: ValueType }
export type ItemInput = Omit<RecordItem, "id" | "archived" | "createdAt" | "updatedAt">;

export interface GoalSubgoal { id: number; goalId: number; title: string; targetValue: number; position: number; completedAt: number | null; createdAt: number; updatedAt: number }
export interface Goal { id: number; title: string; description: string; unit: string; defaultValue: number | null; stepValue: number; startDate: string; endDate: string; archived: boolean; createdAt: number; updatedAt: number; currentValue: number; subgoals: GoalSubgoal[] }
export interface GoalInput { title: string; description: string; unit: string; defaultValue: number | null; stepValue: number; startDate: string; endDate: string; subgoals: Array<{ id: number | null; title: string; targetValue: number }> }

export interface TaskTemplateStep { id: number; templateId: number; title: string; position: number; createdAt: number; updatedAt: number }
export interface TaskTemplate { id: number; title: string; cadence: TemplateCadence; archived: boolean; createdAt: number; updatedAt: number; steps: TaskTemplateStep[] }
export interface TemplateWriteInput { title: string; cadence: TemplateCadence; steps: Array<{ title: string }> }

export interface TodayTaskStep { id: number; taskId: number; title: string; position: number; completedAt: number | null; createdAt: number; updatedAt: number }
export interface TodayTask { id: number; localDate: string; templateId: number | null; sourceTaskId: number | null; title: string; position: number; completedAt: number | null; revision: number; createdAt: number; updatedAt: number; steps: TodayTaskStep[] }
export interface DailyFocus { id: number; localDate: string; firstMove: string; firstMoveCompletedAt: number | null; primaryTaskId: number; secondaryTaskId: number | null; currentTaskId: number | null; state: DailyFocusState; pauseReason: DailyFocusPauseReason | null; completedRounds: number; nextReviewAt: number | null; revision: number; endedAt: number | null; createdAt: number; updatedAt: number }
export interface TodayState { todayTasks: TodayTask[]; dailySuggestions: TaskTemplate[]; unfinishedTasks: TodayTask[]; dailyFocus: DailyFocus | null }

export type AuditAction = "ITEM_CREATED" | "ITEM_UPDATED" | "ITEM_ARCHIVED" | "ITEM_RESTORED" | "ITEM_DELETED" | "ENTRY_CREATED" | "ENTRY_DELETED" | "GOAL_CREATED" | "GOAL_UPDATED" | "GOAL_ARCHIVED" | "GOAL_RESTORED" | "GOAL_DELETED" | "GOAL_ENTRY_CREATED" | "GOAL_SUBGOAL_COMPLETED" | "GOAL_SUBGOAL_REOPENED" | "TEMPLATE_CREATED" | "TEMPLATE_UPDATED" | "TEMPLATE_ARCHIVED" | "TEMPLATE_RESTORED" | "TEMPLATE_DELETED" | "TODAY_TASK_CREATED" | "TODAY_TASK_COMPLETED" | "TODAY_TASK_REOPENED" | "TODAY_TASK_STEP_COMPLETED" | "TODAY_TASK_STEP_REOPENED" | "FOCUS_CREATED" | "FOCUS_UPDATED" | "FOCUS_ENDED";
export interface ItemAuditSnapshot { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; entryCount?: number }
export interface EntryAuditSnapshot { id: number; itemId: number; itemName: string; occurredAt: number; localDate: string; localMinute: number; value: number }
export interface GoalAuditSnapshot { id: number; title: string; description: string; unit: string; defaultValue: number | null; stepValue: number; startDate: string; endDate: string; archived: boolean; currentValue: number; subgoals: Array<{ id: number; title: string; targetValue: number; position: number; completedAt: number | null }> }
export interface GoalEntryAuditSnapshot { id: number; goalId: number; occurredAt: number; localDate: string; localMinute: number; value: number }
export interface GoalSubgoalAuditSnapshot { id: number; goalId: number; title: string; targetValue: number; position: number; completedAt: number | null }
export interface TemplateAuditSnapshot { id: number; title: string; cadence: TemplateCadence; archived: boolean; steps: string[] }
export interface TodayTaskAuditSnapshot { id: number; localDate: string; templateId: number | null; sourceTaskId: number | null; position: number; title: string; completedAt: number | null; steps: Array<{ id: number; title: string; completedAt: number | null }> }
export interface TodayTaskStepAuditSnapshot { id: number; taskId: number; taskTitle: string; title: string; completedAt: number | null }
export interface DailyFocusAuditSnapshot { id: number; localDate: string; firstMove: string; primaryTaskId: number; secondaryTaskId: number | null; currentTaskId: number | null; state: DailyFocusState; pauseReason: DailyFocusPauseReason | null; completedRounds: number; nextReviewAt: number | null }
export type AuditSnapshot = ItemAuditSnapshot | EntryAuditSnapshot | GoalAuditSnapshot | GoalEntryAuditSnapshot | GoalSubgoalAuditSnapshot | TemplateAuditSnapshot | TodayTaskAuditSnapshot | TodayTaskStepAuditSnapshot | DailyFocusAuditSnapshot;
export interface RecordAuditLog { id: number; action: AuditAction; entityType: "ITEM" | "ENTRY" | "GOAL" | "GOAL_ENTRY" | "GOAL_SUBGOAL" | "TEMPLATE" | "TODAY_TASK" | "TODAY_TASK_STEP" | "FOCUS"; entityId: number; before: AuditSnapshot | null; after: AuditSnapshot | null; createdAt: number }
export interface AuditLogPage { logs: RecordAuditLog[]; nextBeforeId: number | null }
