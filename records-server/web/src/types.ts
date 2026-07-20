export type ValueType = "NUMBER" | "DURATION";
export type GoalPeriod = "DAY" | "WEEK";
export interface RecordWindow { id: number; itemId: number; startMinute: number; endMinute: number }
export interface RecordItem { id: number; name: string; description: string; valueType: ValueType; defaultValue: number | null; stepValue: number; unit: string; goalValue: number | null; goalPeriod: GoalPeriod | null; priority: number; archived: boolean; createdAt: number; updatedAt: number; windows: RecordWindow[] }
export interface RecordEntry { id: number; itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number; createdAt: number; itemName: string; itemUnit: string; itemValueType: ValueType }
export type ItemInput = Omit<RecordItem, "id" | "archived" | "createdAt" | "updatedAt" | "windows"> & { windows: Array<Pick<RecordWindow, "startMinute" | "endMinute">> };
export type EntryInput = Pick<RecordEntry, "itemId" | "occurredAt" | "localDate" | "localMinute" | "value">;
