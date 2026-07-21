export const VALUE_TYPES = ["NUMBER", "DURATION"] as const;
export const GOAL_PERIODS = ["DAY", "WEEK"] as const;
export const DURATION_UNITS = ["秒", "分", "小时"] as const;

export type ValueType = (typeof VALUE_TYPES)[number];
export type GoalPeriod = (typeof GOAL_PERIODS)[number];

export interface RecordWindowInput {
  startMinute: number;
  endMinute: number;
}
export interface RecordItemInput {
  name: string;
  description: string;
  valueType: ValueType;
  defaultValue: number | null;
  stepValue: number;
  unit: string;
  goalValue: number | null;
  goalPeriod: GoalPeriod | null;
  priority: number;
  windows: RecordWindowInput[];
}

export interface RecordEntryInput {
  itemId: number;
  occurredAt: number;
  localDate: string;
  localMinute: number;
  value: number;
}

export class ApiError extends Error {
  constructor(
    public readonly statusCode: number,
    message: string,
  ) {
    super(message);
  }
}

export function parseItemInput(value: unknown): RecordItemInput {
  const input = objectInput(value);
  const name = stringField(input, "name").trim();
  const description = stringField(input, "description").trim();
  const unit = stringField(input, "unit").trim();
  const valueType = enumField(input, "valueType", VALUE_TYPES);
  const defaultValue = nullableNumberField(input, "defaultValue");
  const stepValue = numberField(input, "stepValue");
  const goalValue = nullableNumberField(input, "goalValue");
  const goalPeriod = nullableEnumField(input, "goalPeriod", GOAL_PERIODS);
  const priority = integerField(input, "priority");
  const windowsValue = input.windows;

  requireRule(name.length > 0, "Name is required.");
  requireRule(unit.length > 0, "Unit is required.");
  requirePositive(defaultValue, "Default value");
  requirePositive(stepValue, "Step");
  requirePositive(goalValue, "Goal");
  requireRule(goalValue === null || goalPeriod !== null, "Goal period is required when a goal is set.");
  requireRule(valueType !== "DURATION" || DURATION_UNITS.includes(unit as never), "Duration unit must be 秒, 分, or 小时.");
  requireRule(Array.isArray(windowsValue), "Windows must be an array.");

  const windows = windowsValue.map((window) => parseWindowInput(window));
  const uniqueWindows = new Set(windows.map(({ startMinute, endMinute }) => `${startMinute}:${endMinute}`));
  requireRule(uniqueWindows.size === windows.length, "Suggested time windows must be unique.");

  return {
    name,
    description,
    valueType,
    defaultValue,
    stepValue,
    unit,
    goalValue,
    goalPeriod: goalValue === null ? null : goalPeriod,
    priority,
    windows,
  };
}

export function parseEntryInput(value: unknown): RecordEntryInput {
  const input = objectInput(value);
  const entry = {
    itemId: positiveIntegerField(input, "itemId"),
    occurredAt: integerField(input, "occurredAt"),
    localDate: stringField(input, "localDate"),
    localMinute: integerField(input, "localMinute"),
    value: numberField(input, "value"),
  };
  requireRule(/^\d{4}-\d{2}-\d{2}$/.test(entry.localDate) && isRealDate(entry.localDate), "Local date is invalid.");
  requireRule(entry.localMinute >= 0 && entry.localMinute <= 1439, "Local minute is invalid.");
  requirePositive(entry.value, "Value");
  return entry;
}

export function parseId(value: unknown, label = "ID"): number {
  const id = typeof value === "string" && /^\d+$/.test(value) ? Number(value) : value;
  requireRule(Number.isSafeInteger(id) && Number(id) > 0, `${label} is invalid.`);
  return Number(id);
}

export function parseOptionalId(value: unknown, label = "ID"): number | undefined {
  return value === undefined ? undefined : parseId(value, label);
}

export function parseIds(value: unknown): number[] {
  const input = objectInput(value);
  requireRule(Array.isArray(input.ids) && input.ids.length > 0, "IDs must be a non-empty array.");
  const ids = input.ids.map((id) => parseId(id));
  requireRule(new Set(ids).size === ids.length, "IDs must be unique.");
  return ids;
}

export function parseArchived(value: unknown): boolean {
  const input = objectInput(value);
  requireRule(typeof input.archived === "boolean", "Archived must be a boolean.");
  return input.archived;
}

export function parseIsoDate(value: unknown, label: string): string {
  requireRule(typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) && isRealDate(value), `${label} is invalid.`);
  return value;
}

function parseWindowInput(value: unknown): RecordWindowInput {
  const input = objectInput(value);
  const startMinute = integerField(input, "startMinute");
  const endMinute = integerField(input, "endMinute");
  requireRule(startMinute >= 0 && startMinute <= 1439, "Start time is invalid.");
  requireRule(endMinute >= 0 && endMinute <= 1439, "End time is invalid.");
  requireRule(startMinute !== endMinute, "Start and end times must differ.");
  return { startMinute, endMinute };
}

function objectInput(value: unknown): Record<string, unknown> {
  requireRule(value !== null && typeof value === "object" && !Array.isArray(value), "Request body must be an object.");
  return value as Record<string, unknown>;
}

function stringField(input: Record<string, unknown>, field: string): string {
  const value = input[field];
  requireRule(typeof value === "string", `${field} must be a string.`);
  return value;
}

function numberField(input: Record<string, unknown>, field: string): number {
  const value = input[field];
  requireRule(typeof value === "number" && Number.isFinite(value), `${field} must be a finite number.`);
  return value;
}

function integerField(input: Record<string, unknown>, field: string): number {
  const value = numberField(input, field);
  requireRule(Number.isSafeInteger(value), `${field} must be an integer.`);
  return value;
}

function positiveIntegerField(input: Record<string, unknown>, field: string): number {
  const value = integerField(input, field);
  requireRule(value > 0, `${field} must be positive.`);
  return value;
}

function nullableNumberField(input: Record<string, unknown>, field: string): number | null {
  return input[field] === null ? null : numberField(input, field);
}

function enumField<T extends string>(input: Record<string, unknown>, field: string, values: readonly T[]): T {
  const value = input[field];
  requireRule(typeof value === "string" && values.includes(value as T), `${field} is invalid.`);
  return value as T;
}

function nullableEnumField<T extends string>(input: Record<string, unknown>, field: string, values: readonly T[]): T | null {
  return input[field] === null ? null : enumField(input, field, values);
}

function requirePositive(value: number | null, label: string): void {
  if (value !== null) requireRule(value > 0, `${label} must be a positive number.`);
}

function isRealDate(value: string): boolean {
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

function requireRule(condition: unknown, message: string): asserts condition {
  if (!condition) throw new ApiError(400, message);
}
