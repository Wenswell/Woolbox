export const VALUE_TYPES = ["NUMBER", "DURATION"] as const;
export const GOAL_PERIODS = ["DAY", "DAYS_7"] as const;
export const RECORD_MODES = ["ACCUMULATE", "MEASUREMENT"] as const;
export const DURATION_UNITS = ["秒", "分", "小时"] as const;
export const DAILY_PLAN_STATES = ["READY", "ACTIVE", "ENOUGH", "ENDED"] as const;
export const DAILY_PLAN_COMPLETION_REASONS = ["ROUNDS", "COMMITMENTS"] as const;
export const TASK_REPEAT_MODES = ["ONCE", "DAILY"] as const;

export type ValueType = (typeof VALUE_TYPES)[number];
export type GoalPeriod = (typeof GOAL_PERIODS)[number];
export type RecordMode = (typeof RECORD_MODES)[number];
export type DailyPlanState = (typeof DAILY_PLAN_STATES)[number];
export type DailyPlanCompletionReason = (typeof DAILY_PLAN_COMPLETION_REASONS)[number];
export type TaskRepeatMode = (typeof TASK_REPEAT_MODES)[number];

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

export type StepTitleInput = TaskTitleInput;

export interface DatedStepInput extends StepTitleInput {
  localDate: string;
}

export interface TaskWriteInput extends TaskTitleInput {
  repeatMode: TaskRepeatMode;
  localDate: string;
}

export interface TaskCreateInput extends TaskTitleInput {
  repeatMode: TaskRepeatMode;
  localDate: string;
  steps: StepTitleInput[];
}

export interface StepCompletionInput {
  completed: boolean;
  localDate: string;
}

export interface StepOrderInput {
  ids: number[];
  localDate: string;
}

export interface DailyPlanCreateInput {
  localDate: string;
  firstMove: string;
  primaryTaskId: number;
  secondaryTaskId: number | null;
}

export interface DailyPlanUpdateInput {
  firstMove: string;
  primaryTaskId: number;
  secondaryTaskId: number | null;
  expectedRevision: number;
  occurredAt: number;
}

export interface DailyPlanTransitionInput {
  expectedRevision: number;
  occurredAt: number;
}

export interface DailyPlanReviewInput extends DailyPlanTransitionInput {
  currentTaskId: number;
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
  requireRule(input.windows === undefined, "Manual time windows are not supported.");
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

  requireRule(name.length > 0, "Name is required.");
  requireRule(unit.length > 0, "Unit is required.");
  requirePositive(stepValue, "Step");

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
  };
}

export function parseEntryInput(value: unknown): RecordEntryInput {
  const input = objectInput(value);
  const entry = {
    itemId: positiveIntegerField(input, "itemId"),
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
    localDate: stringField(input, "localDate"),
    localMinute: integerField(input, "localMinute"),
    value: numberField(input, "value"),
  };
  requireRule(/^\d{4}-\d{2}-\d{2}$/.test(entry.localDate) && isRealDate(entry.localDate), "Local date is invalid.");
  requireRule(entry.localMinute >= 0 && entry.localMinute <= 1439, "Local minute is invalid.");
  return entry;
}

export function parseTaskInput(value: unknown): TaskTitleInput {
  const input = objectInput(value);
  requireRule(input.plannedDate === undefined, "Task dates are not supported.");
  const title = stringField(input, "title").trim();
  requireRule(title.length > 0, "Title is required.");
  return { title };
}

export function parseStepTitleInput(value: unknown): StepTitleInput {
  const input = objectInput(value);
  requireRule(input.windows === undefined, "Manual time windows are not supported.");
  const title = stringField(input, "title").trim();
  requireRule(title.length > 0, "Subtask titles are required.");
  return { title };
}

export function parseTaskWriteInput(value: unknown): TaskWriteInput {
  const input = objectInput(value);
  return {
    ...parseTaskInput(input),
    repeatMode: enumField(input, "repeatMode", TASK_REPEAT_MODES),
    localDate: parseIsoDate(input.localDate, "Local date"),
  };
}

export function parseTaskCreateInput(value: unknown): TaskCreateInput {
  const input = objectInput(value);
  const { title } = parseTaskInput(input);
  const repeatMode = enumField(input, "repeatMode", TASK_REPEAT_MODES);
  const localDate = parseIsoDate(input.localDate, "Local date");
  requireRule(Array.isArray(input.steps) && input.steps.length > 0, "At least one subtask is required.");
  const steps = input.steps.map(parseStepTitleInput);
  return { title, repeatMode, localDate, steps };
}

export function parseDatedStepInput(value: unknown): DatedStepInput {
  const input = objectInput(value);
  return {
    ...parseStepTitleInput(input),
    localDate: parseIsoDate(input.localDate, "Local date"),
  };
}

export function parseStepCompletionInput(value: unknown): StepCompletionInput {
  const input = objectInput(value);
  requireRule(typeof input.completed === "boolean", "Completed must be a boolean.");
  return {
    completed: input.completed,
    localDate: parseIsoDate(input.localDate, "Local date"),
  };
}

export function parseStepOrderInput(value: unknown): StepOrderInput {
  const input = objectInput(value);
  return {
    ids: parseIds(input),
    localDate: parseIsoDate(input.localDate, "Local date"),
  };
}

export function parseDailyPlanCreateInput(value: unknown): DailyPlanCreateInput {
  const input = objectInput(value);
  const result = {
    localDate: parseIsoDate(input.localDate, "Local date"),
    firstMove: requiredTrimmedString(input, "firstMove", "First move is required."),
    primaryTaskId: positiveIntegerField(input, "primaryTaskId"),
    secondaryTaskId: nullablePositiveIntegerField(input, "secondaryTaskId"),
  };
  requireRule(result.primaryTaskId !== result.secondaryTaskId, "Primary and secondary tasks must be different.");
  return result;
}

export function parseDailyPlanUpdateInput(value: unknown): DailyPlanUpdateInput {
  const input = objectInput(value);
  const result = {
    firstMove: requiredTrimmedString(input, "firstMove", "First move is required."),
    primaryTaskId: positiveIntegerField(input, "primaryTaskId"),
    secondaryTaskId: nullablePositiveIntegerField(input, "secondaryTaskId"),
    expectedRevision: nonNegativeIntegerField(input, "expectedRevision"),
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
  };
  requireRule(result.primaryTaskId !== result.secondaryTaskId, "Primary and secondary tasks must be different.");
  return result;
}

export function parseDailyPlanTransitionInput(value: unknown): DailyPlanTransitionInput {
  const input = objectInput(value);
  return {
    expectedRevision: nonNegativeIntegerField(input, "expectedRevision"),
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
  };
}

export function parseDailyPlanReviewInput(value: unknown): DailyPlanReviewInput {
  const input = objectInput(value);
  return {
    ...parseDailyPlanTransitionInput(input),
    currentTaskId: positiveIntegerField(input, "currentTaskId"),
  };
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

function nonNegativeIntegerField(input: Record<string, unknown>, field: string): number {
  const value = integerField(input, field);
  requireRule(value >= 0, `${field} must not be negative.`);
  return value;
}

function nullablePositiveIntegerField(input: Record<string, unknown>, field: string): number | null {
  return input[field] === null ? null : positiveIntegerField(input, field);
}

function requiredTrimmedString(input: Record<string, unknown>, field: string, message: string): string {
  const value = stringField(input, field).trim();
  requireRule(value.length > 0, message);
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
