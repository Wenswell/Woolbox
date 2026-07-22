export type ValueType = "NUMBER" | "DURATION";
export type RecordMode = "ACCUMULATE" | "MEASUREMENT";
export type GoalPeriod = "DAY" | "WEEK";
export interface RecordWindow { id: number; itemId: number; startMinute: number; endMinute: number }
export interface RecordItem { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; createdAt: number; updatedAt: number; windows: RecordWindow[] }
export interface RecordEntry { id: number; itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number; createdAt: number; itemName: string; itemMode: RecordMode; itemUnit: string; itemValueType: ValueType }
export type ItemInput = Omit<RecordItem, "id" | "archived" | "createdAt" | "updatedAt" | "windows"> & { windows: Array<Pick<RecordWindow, "startMinute" | "endMinute">> };
export type AuditAction = "ITEM_CREATED" | "ITEM_UPDATED" | "ITEM_ARCHIVED" | "ITEM_RESTORED" | "ITEM_DELETED" | "ENTRY_CREATED" | "ENTRY_UPDATED" | "ENTRY_DELETED";
export interface ItemAuditSnapshot { id: number; name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; windows: Array<Pick<RecordWindow, "startMinute" | "endMinute">>; entryCount?: number }
export interface EntryAuditSnapshot { id: number; itemId: number; itemName: string; occurredAt: number; localDate: string; localMinute: number; value: number }
export interface RecordAuditLog { id: number; action: AuditAction; entityType: "ITEM" | "ENTRY"; entityId: number; before: ItemAuditSnapshot | EntryAuditSnapshot | null; after: ItemAuditSnapshot | EntryAuditSnapshot | null; createdAt: number }
export interface AuditLogPage { logs: RecordAuditLog[]; nextBeforeId: number | null }
