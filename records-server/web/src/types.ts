export type ValueType = "NUMBER" | "DURATION";
export type RecordMode = "ACCUMULATE" | "MEASUREMENT";
export type GoalPeriod = "DAY" | "DAYS_7";
export type TemplateCadence = "MANUAL" | "DAILY";
export type DailyPlanState = "READY" | "ACTIVE" | "PAUSED" | "ENDED";
export type DailyPlanPauseReason = "COMMITMENTS_COMPLETED" | "TARGET_REACHED";
export type CommitmentRole = "PRIMARY" | "SECONDARY" | "ADDITIONAL";

export interface RecordItem { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; createdAt: number; updatedAt: number }
export interface RecordEntry { id: number; itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number; createdAt: number; itemName: string; itemMode: RecordMode; itemUnit: string; itemValueType: ValueType }
export type ItemInput = Omit<RecordItem, "id" | "archived" | "createdAt" | "updatedAt">;

export interface TaskTemplateStep { id: number; templateId: number; title: string; position: number; createdAt: number; updatedAt: number }
export interface TaskTemplate { id: number; title: string; cadence: TemplateCadence; archived: boolean; createdAt: number; updatedAt: number; steps: TaskTemplateStep[] }
export interface TemplateWriteInput { title: string; cadence: TemplateCadence; steps: Array<{ title: string }> }

export interface TodayCommitmentStep { id: number; commitmentId: number; title: string; position: number; completedAt: number | null; createdAt: number; updatedAt: number }
export interface TodayCommitment { id: number; planId: number; templateId: number | null; role: CommitmentRole; position: number; title: string; cadence: TemplateCadence; completedAt: number | null; createdAt: number; updatedAt: number; steps: TodayCommitmentStep[] }
export interface DailyPlan { id: number; localDate: string; firstMove: string; firstMoveCompletedAt: number | null; currentCommitmentId: number | null; state: DailyPlanState; pauseReason: DailyPlanPauseReason | null; completedRounds: number; nextReviewAt: number | null; revision: number; endedAt: number | null; createdAt: number; updatedAt: number; commitments: TodayCommitment[] }

export type AuditAction = "ITEM_CREATED" | "ITEM_UPDATED" | "ITEM_ARCHIVED" | "ITEM_RESTORED" | "ITEM_DELETED" | "ENTRY_CREATED" | "ENTRY_DELETED" | "TEMPLATE_CREATED" | "TEMPLATE_UPDATED" | "TEMPLATE_ARCHIVED" | "TEMPLATE_RESTORED" | "TEMPLATE_DELETED" | "COMMITMENT_CREATED" | "COMMITMENT_COMPLETED" | "COMMITMENT_REOPENED" | "COMMITMENT_STEP_COMPLETED" | "COMMITMENT_STEP_REOPENED" | "DAILY_PLAN_CREATED" | "DAILY_PLAN_UPDATED" | "DAILY_PLAN_ENDED";
export interface ItemAuditSnapshot { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; entryCount?: number }
export interface EntryAuditSnapshot { id: number; itemId: number; itemName: string; occurredAt: number; localDate: string; localMinute: number; value: number }
export interface TemplateAuditSnapshot { id: number; title: string; cadence: TemplateCadence; archived: boolean; steps: string[] }
export interface CommitmentAuditSnapshot { id: number; planId: number; templateId: number | null; role: CommitmentRole; position: number; title: string; cadence: TemplateCadence; completedAt: number | null; steps: Array<{ id: number; title: string; completedAt: number | null }> }
export interface CommitmentStepAuditSnapshot { id: number; commitmentId: number; commitmentTitle: string; title: string; completedAt: number | null }
export interface DailyPlanAuditSnapshot { id: number; localDate: string; firstMove: string; currentCommitmentId: number | null; state: DailyPlanState; pauseReason: DailyPlanPauseReason | null; completedRounds: number; nextReviewAt: number | null; commitmentIds: number[] }
export type AuditSnapshot = ItemAuditSnapshot | EntryAuditSnapshot | TemplateAuditSnapshot | CommitmentAuditSnapshot | CommitmentStepAuditSnapshot | DailyPlanAuditSnapshot;
export interface RecordAuditLog { id: number; action: AuditAction; entityType: "ITEM" | "ENTRY" | "TEMPLATE" | "COMMITMENT" | "COMMITMENT_STEP" | "DAILY_PLAN"; entityId: number; before: AuditSnapshot | null; after: AuditSnapshot | null; createdAt: number }
export interface AuditLogPage { logs: RecordAuditLog[]; nextBeforeId: number | null }
