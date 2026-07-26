export const VALUE_TYPES = ["NUMBER", "DURATION"] as const;
export const GOAL_PERIODS = ["DAY", "DAYS_7"] as const;
export const RECORD_MODES = ["ACCUMULATE", "MEASUREMENT"] as const;
export const TASK_HORIZONS = ["TODAY", "LATER"] as const;
export const DURATION_UNITS = ["秒", "分", "小时"] as const;

export type ValueType = (typeof VALUE_TYPES)[number];
export type GoalPeriod = (typeof GOAL_PERIODS)[number];
export type RecordMode = (typeof RECORD_MODES)[number];
export type TaskHorizon = (typeof TASK_HORIZONS)[number];

export interface RecordWindowInput {
  startMinute: number;
  endMinute: number;
}
export interface RecordItemInput {
  name: string;
  description: string;
  mode: RecordMode;
  valueType: ValueType;
  defaultValue: number | null;
  minValue: number | null;
  maxValue: number | null;
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

export interface TaskTitleInput {
  title: string;
}

export interface TaskWriteInput extends TaskTitleInput {
  horizon: TaskHorizon;
  windows: RecordWindowInput[];
}

export interface TaskCreateInput extends TaskWriteInput {
  steps: string[];
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
  const mode = enumField(input, "mode", RECORD_MODES);
  const valueType = enumField(input, "valueType", VALUE_TYPES);
  const defaultValue = nullableNumberField(input, "defaultValue");
  const minValue = nullableNumberField(input, "minValue");
  const maxValue = nullableNumberField(input, "maxValue");
  const stepValue = numberField(input, "stepValue");
  const goalValue = nullableNumberField(input, "goalValue");
  const goalPeriod = nullableEnumField(input, "goalPeriod", GOAL_PERIODS);
  const priority = integerField(input, "priority");
  const windowsValue = input.windows;

  requireRule(name.length > 0, "Name is required.");
  requireRule(unit.length > 0, "Unit is required.");
  requirePositive(stepValue, "Step");
  const windows = parseWindowsInput(windowsValue);

  if (mode === "ACCUMULATE") {
    requirePositive(defaultValue, "Default value");
    requirePositive(goalValue, "Goal");
    requireRule(goalValue === null || goalPeriod !== null, "Goal period is required when a goal is set.");
    requireRule(valueType !== "DURATION" || DURATION_UNITS.includes(unit as never), "Duration unit must be 秒, 分, or 小时.");
    requireRule(minValue === null && maxValue === null, "Accumulate items do not support measurement bounds.");
  } else {
    requireRule(valueType === "NUMBER", "Measurements must use Number.");
    requireRule(defaultValue === null, "Measurements do not support default values.");
    requireMeasurementGrid(minValue, maxValue, stepValue);
    requireRule(goalValue === null && goalPeriod === null, "Measurements do not support goals.");
    requireRule(windows.length === 0, "Measurements do not support suggested time windows.");
  }

  return {
    name,
    description,
    mode,
    valueType,
    defaultValue,
    minValue,
    maxValue,
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
  return entry;
}

export function parseTaskHorizon(value: unknown): TaskHorizon {
  requireRule(typeof value === "string" && TASK_HORIZONS.includes(value as TaskHorizon), "Horizon is invalid.");
  return value as TaskHorizon;
}

export function parseTaskInput(value: unknown): TaskTitleInput {
  const title = stringField(objectInput(value), "title").trim();
  requireRule(title.length > 0, "Title is required.");
  return { title };
}

export function parseTaskWriteInput(value: unknown): TaskWriteInput {
  const input = objectInput(value);
  const { title } = parseTaskInput(input);
  return {
    title,
    horizon: parseTaskHorizon(input.horizon),
    // Tasks predate suggested windows, so an absent list means none.
    windows: parseWindowsInput(input.windows, true),
  };
}

export function parseTaskCreateInput(value: unknown): TaskCreateInput {
  const input = objectInput(value);
  const { title, horizon, windows } = parseTaskWriteInput(input);
  requireRule(Array.isArray(input.steps) && input.steps.length > 0, "At least one subtask is required.");
  const steps = input.steps.map((value) => {
    requireRule(typeof value === "string", "Subtask titles must be strings.");
    const step = value.trim();
    requireRule(step.length > 0, "Subtask titles are required.");
    return step;
  });
  return { title, horizon, windows, steps };
}

export function parseCompleted(value: unknown): boolean {
  const input = objectInput(value);
  requireRule(typeof input.completed === "boolean", "Completed must be a boolean.");
  return input.completed;
}

export function parseOptionalRecordMode(value: unknown): RecordMode | undefined {
  if (value === undefined) return undefined;
  requireRule(typeof value === "string" && RECORD_MODES.includes(value as RecordMode), "Record mode is invalid.");
  return value as RecordMode;
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

export const MINUTES_PER_DAY = 24 * 60;
export const MAX_WINDOW_COVERAGE_MINUTES = 12 * 60;

/**
 * Suggested windows must stay a hint about *now*, so their union has to leave
 * most of the day uncovered. Overlapping windows are counted once.
 */
export function windowCoverageMinutes(windows: RecordWindowInput[]): number {
  const segments: Array<[number, number]> = [];
  for (const { startMinute, endMinute } of windows) {
    if (startMinute < endMinute) {
      segments.push([startMinute, endMinute]);
    } else {
      segments.push([startMinute, MINUTES_PER_DAY]);
      segments.push([0, endMinute]);
    }
  }
  segments.sort((left, right) => left[0] - right[0]);
  let covered = 0;
  let cursor = 0;
  for (const [start, end] of segments) {
    if (end <= cursor) continue;
    covered += end - Math.max(start, cursor);
    cursor = end;
  }
  return covered;
}

function parseWindowsInput(value: unknown, optional = false): RecordWindowInput[] {
  if (optional && value === undefined) return [];
  requireRule(Array.isArray(value), "Windows must be an array.");
  const windows = value.map((window) => parseWindowInput(window));
  const unique = new Set(windows.map(({ startMinute, endMinute }) => `${startMinute}:${endMinute}`));
  requireRule(unique.size === windows.length, "Suggested time windows must be unique.");
  requireRule(
    windowCoverageMinutes(windows) < MAX_WINDOW_COVERAGE_MINUTES,
    "Suggested times must cover less than 12 hours.",
  );
  return windows;
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

export function isMeasurementValueOnGrid(value: number, minValue: number, maxValue: number, stepValue: number): boolean {
  if (!Number.isFinite(value) || value < minValue || value > maxValue) return false;
  const position = (value - minValue) / stepValue;
  return Math.abs(position - Math.round(position)) <= 1e-9 * Math.max(1, Math.abs(position));
}

function requireMeasurementGrid(minValue: number | null, maxValue: number | null, stepValue: number): void {
  requireRule(minValue !== null && maxValue !== null, "Measurement minimum and maximum are required.");
  requireRule(minValue < maxValue, "Measurement minimum must be less than maximum.");
  const steps = (maxValue - minValue) / stepValue;
  requireRule(
    steps > 0 && Math.abs(steps - Math.round(steps)) <= 1e-9 * Math.max(1, Math.abs(steps)),
    "Measurement range must divide evenly by step.",
  );
}

function isRealDate(value: string): boolean {
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

function requireRule(condition: unknown, message: string): asserts condition {
  if (!condition) throw new ApiError(400, message);
}
