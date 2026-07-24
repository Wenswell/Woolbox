export type ValueType = "NUMBER" | "DURATION";
export type RecordMode = "ACCUMULATE" | "MEASUREMENT";
export type GoalPeriod = "DAY" | "WEEK";
export interface RecordWindow { id: number; itemId: number; startMinute: number; endMinute: number }
export interface RecordItem { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; createdAt: number; updatedAt: number; windows: RecordWindow[] }
export interface RecordEntry { id: number; itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number; createdAt: number; itemName: string; itemMode: RecordMode; itemUnit: string; itemValueType: ValueType }
export type ItemInput = Omit<RecordItem, "id" | "archived" | "createdAt" | "updatedAt" | "windows"> & { windows: Array<Pick<RecordWindow, "startMinute" | "endMinute">> };
export type AuditAction = "ITEM_CREATED" | "ITEM_UPDATED" | "ITEM_ARCHIVED" | "ITEM_RESTORED" | "ITEM_DELETED" | "ENTRY_CREATED" | "ENTRY_DELETED" | "TASK_CREATED" | "TASK_UPDATED" | "TASK_COMPLETED" | "TASK_REOPENED" | "TASK_DELETED" | "STEP_CREATED" | "STEP_UPDATED" | "STEP_COMPLETED" | "STEP_REOPENED" | "STEP_DELETED";
export interface ItemAuditSnapshot { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; minValue: number | null; maxValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; windows: Array<Pick<RecordWindow, "startMinute" | "endMinute">>; entryCount?: number }
export interface EntryAuditSnapshot { id: number; itemId: number; itemName: string; occurredAt: number; localDate: string; localMinute: number; value: number }
export interface TaskAuditSnapshot { id: number; title: string; horizon: string; completedAt: number | null; stepCount?: number }
export interface StepAuditSnapshot { id: number; taskId: number; taskTitle: string; title: string; completedAt: number | null }
export interface RecordAuditLog { id: number; action: AuditAction; entityType: "ITEM" | "ENTRY" | "TASK" | "STEP"; entityId: number; before: ItemAuditSnapshot | EntryAuditSnapshot | TaskAuditSnapshot | StepAuditSnapshot | null; after: ItemAuditSnapshot | EntryAuditSnapshot | TaskAuditSnapshot | StepAuditSnapshot | null; createdAt: number }
export interface AuditLogPage { logs: RecordAuditLog[]; nextBeforeId: number | null }
