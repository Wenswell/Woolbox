export const VALUE_TYPES = ["NUMBER", "DURATION"] as const;
export const GOAL_PERIODS = ["DAY", "DAYS_7"] as const;
export const RECORD_MODES = ["ACCUMULATE", "MEASUREMENT"] as const;
export const DURATION_UNITS = ["秒", "分", "小时"] as const;
export const TEMPLATE_CADENCES = ["MANUAL", "DAILY"] as const;

export type ValueType = (typeof VALUE_TYPES)[number];
export type GoalPeriod = (typeof GOAL_PERIODS)[number];
export type RecordMode = (typeof RECORD_MODES)[number];
export type TemplateCadence = (typeof TEMPLATE_CADENCES)[number];

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

export interface GoalSubgoalInput {
  id: number | null;
  title: string;
  targetValue: number;
}

export interface GoalInput {
  title: string;
  description: string;
  unit: string;
  defaultValue: number | null;
  stepValue: number;
  startDate: string;
  endDate: string;
  subgoals: GoalSubgoalInput[];
}

export interface GoalEntryInput {
  goalId: number;
  occurredAt: number;
  localDate: string;
  localMinute: number;
  value: number;
}

export interface GoalSubgoalCompletionInput {
  completed: boolean;
  occurredAt: number;
  localDate: string;
}

export interface StepTitleInput {
  title: string;
}

export interface TemplateWriteInput {
  title: string;
  cadence: TemplateCadence;
  steps: StepTitleInput[];
}

export interface TodayTaskCompletionInput {
  completed: boolean;
  expectedRevision: number;
  occurredAt: number;
}

export interface TodayTaskCreateInput {
  localDate: string;
  title: string;
  steps: StepTitleInput[];
}

export interface TodayTaskFromTemplateInput {
  localDate: string;
  templateId: number;
}

export interface TodayTaskFromTaskInput {
  localDate: string;
  sourceTaskId: number;
}

export interface DailyCheckinWakeInput {
  localDate: string;
  occurredAt: number;
}

export interface HourlyCheckinInput extends DailyCheckinWakeInput {
  hour: number;
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

export function parseGoalInput(value: unknown): GoalInput {
  const input = objectInput(value);
  const title = requiredTrimmedString(input, "title", "Title is required.");
  const description = stringField(input, "description").trim();
  const unit = requiredTrimmedString(input, "unit", "Unit is required.");
  const defaultValue = nullableNumberField(input, "defaultValue");
  const stepValue = numberField(input, "stepValue");
  const startDate = parseIsoDate(input.startDate, "Start date");
  const endDate = parseIsoDate(input.endDate, "End date");
  requirePositive(defaultValue, "Default value");
  requirePositive(stepValue, "Step");
  requireRule(startDate <= endDate, "Start date must not be after end date.");
  requireRule(Array.isArray(input.subgoals) && input.subgoals.length > 0, "Subgoals must be a non-empty array.");
  const subgoals = input.subgoals.map((candidate) => {
    const subgoal = objectInput(candidate);
    const id = subgoal.id === null ? null : parseId(subgoal.id, "Subgoal ID");
    const targetValue = numberField(subgoal, "targetValue");
    requirePositive(targetValue, "Subgoal target");
    return {
      id,
      title: requiredTrimmedString(subgoal, "title", "Subgoal title is required."),
      targetValue,
    };
  });
  const ids = subgoals.flatMap((subgoal) => subgoal.id === null ? [] : [subgoal.id]);
  requireRule(new Set(ids).size === ids.length, "Subgoal IDs must be unique.");
  return { title, description, unit, defaultValue, stepValue, startDate, endDate, subgoals };
}

export function parseGoalEntryInput(value: unknown): GoalEntryInput {
  const input = objectInput(value);
  const entry = {
    goalId: positiveIntegerField(input, "goalId"),
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
    localDate: parseIsoDate(input.localDate, "Local date"),
    localMinute: integerField(input, "localMinute"),
    value: numberField(input, "value"),
  };
  requireRule(entry.localMinute >= 0 && entry.localMinute <= 1439, "Local minute is invalid.");
  requirePositive(entry.value, "Value");
  return entry;
}

export function parseGoalSubgoalCompletionInput(value: unknown): GoalSubgoalCompletionInput {
  const input = objectInput(value);
  requireRule(typeof input.completed === "boolean", "Completed must be a boolean.");
  return {
    completed: input.completed,
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
    localDate: parseIsoDate(input.localDate, "Local date"),
  };
}

export function parseStepTitleInput(value: unknown): StepTitleInput {
  const input = objectInput(value);
  requireRule(input.windows === undefined, "Manual time windows are not supported.");
  const title = stringField(input, "title").trim();
  requireRule(title.length > 0, "Subtask titles are required.");
  return { title };
}

export function parseTemplateWriteInput(value: unknown): TemplateWriteInput {
  const input = objectInput(value);
  const title = requiredTrimmedString(input, "title", "Title is required.");
  const cadence = enumField(input, "cadence", TEMPLATE_CADENCES);
  requireRule(Array.isArray(input.steps), "Steps must be an array.");
  const steps = input.steps.map(parseStepTitleInput);
  requireRule(steps.length > 0, "At least one subtask is required.");
  return { title, cadence, steps };
}

export function parseTodayTaskCompletionInput(value: unknown): TodayTaskCompletionInput {
  const input = objectInput(value);
  requireRule(typeof input.completed === "boolean", "Completed must be a boolean.");
  return {
    completed: input.completed,
    expectedRevision: nonNegativeIntegerField(input, "expectedRevision"),
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
  };
}

export function parseTodayTaskCreateInput(value: unknown): TodayTaskCreateInput {
  const input = objectInput(value);
  requireRule(Array.isArray(input.steps), "Steps must be an array.");
  const steps = input.steps.map(parseStepTitleInput);
  requireRule(steps.length > 0, "At least one subtask is required.");
  return {
    localDate: parseIsoDate(input.localDate, "Local date"),
    title: requiredTrimmedString(input, "title", "Title is required."),
    steps,
  };
}

export function parseDailyCheckinWakeInput(value: unknown): DailyCheckinWakeInput {
  const input = objectInput(value);
  return {
    localDate: parseIsoDate(input.localDate, "Local date"),
    occurredAt: nonNegativeIntegerField(input, "occurredAt"),
  };
}

export function parseHourlyCheckinInput(value: unknown): HourlyCheckinInput {
  const input = objectInput(value);
  const hour = integerField(input, "hour");
  requireRule(hour >= 0 && hour <= 23, "Hour must be between 0 and 23.");
  return { ...parseDailyCheckinWakeInput(input), hour };
}

export function parseTodayTaskFromTemplateInput(value: unknown): TodayTaskFromTemplateInput {
  const input = objectInput(value);
  return {
    localDate: parseIsoDate(input.localDate, "Local date"),
    templateId: positiveIntegerField(input, "templateId"),
  };
}

export function parseTodayTaskFromTaskInput(value: unknown): TodayTaskFromTaskInput {
  const input = objectInput(value);
  return {
    localDate: parseIsoDate(input.localDate, "Local date"),
    sourceTaskId: positiveIntegerField(input, "sourceTaskId"),
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
