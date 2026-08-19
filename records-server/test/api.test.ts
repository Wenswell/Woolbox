import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import { buildApp } from "../src/app.js";

const TestDate = "2026-07-20";
const Yesterday = "2026-07-19";

describe("Records API", () => {
  let app: FastifyInstance;
  let directory: string;

  beforeEach(() => {
    directory = mkdtempSync(join(tmpdir(), "records-server-test-"));
    app = buildApp({ databaseFile: join(directory, "records.sqlite"), now: () => 1_700_000_000_000 });
  });

  afterEach(async () => {
    await app.close();
    rmSync(directory, { recursive: true, force: true });
  });

  it("serves record item CRUD with validation and audit logs", async () => {
    const created = await createItem(itemInput({ name: "  Water  ", description: " drink ", unit: " ml " }));
    expect(created.statusCode).toBe(201);
    expect(created.json().item).toMatchObject({ id: 1, name: "Water", description: "drink", mode: "ACCUMULATE", unit: "ml", archived: false });

    const updated = await app.inject({ method: "PUT", url: "/api/items/1", payload: itemInput({ name: "Hydration" }) });
    expect(updated.statusCode).toBe(200);
    expect(updated.json().item.name).toBe("Hydration");
    expect((await getAuditLogs()).map((log) => log.action)).toEqual(["ITEM_UPDATED", "ITEM_CREATED"]);

    expect((await createItem(itemInput({ name: "hydration" }))).statusCode).toBe(409);
    expect((await createItem(itemInput({ name: " " }))).statusCode).toBe(400);
    expect((await createItem(itemInput({ stepValue: 0 }))).statusCode).toBe(400);
    expect((await archiveItems([1], true)).statusCode).toBe(200);
    expect((await getItems(true))[0]).toMatchObject({ id: 1, archived: true });
    expect((await app.inject({ method: "DELETE", url: "/api/items/1" })).statusCode).toBe(200);
  });

  it("validates, filters, and deletes accumulate and measurement entries", async () => {
    await createItem(itemInput({ name: "Water", unit: "ml" }));
    await createItem(itemInput({ name: "Temperature", mode: "MEASUREMENT", defaultValue: null, minValue: -10, maxValue: 10, stepValue: 0.5, unit: "C" }));
    expect((await createEntry(entryInput({ itemId: 1, localDate: Yesterday, value: 2 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 2, occurredAt: 20, value: -5.5 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 2, value: -5.25 }))).statusCode).toBe(400);
    expect((await createEntry(entryInput({ itemId: 1, value: 0 }))).statusCode).toBe(400);

    const accumulate = await app.inject({ method: "GET", url: `/api/entries?startDate=${Yesterday}&endDate=${TestDate}&mode=ACCUMULATE` });
    expect(accumulate.json().entries).toEqual([expect.objectContaining({ itemId: 1, value: 2 })]);
    expect((await app.inject({ method: "GET", url: "/api/entries/latest" })).json().entries)
      .toEqual([expect.objectContaining({ itemId: 2, value: -5.5 })]);
    expect((await app.inject({ method: "POST", url: "/api/entries/batch-delete", payload: { ids: [1, 3] } })).statusCode).toBe(404);
    expect(await getEntries()).toHaveLength(2);
  });

  it("tracks a shared goal pool without consuming progress", async () => {
    const created = await app.inject({
      method: "POST",
      url: "/api/goals",
      payload: goalInput(),
    });
    expect(created.statusCode).toBe(201);
    expect(created.json().goal).toMatchObject({
      title: "Build the kit",
      currentValue: 0,
      subgoals: [
        { title: "Phone", targetValue: 5_000, completedAt: null },
        { title: "Bike", targetValue: 8_000, completedAt: null },
      ],
    });

    expect((await app.inject({
      method: "POST",
      url: "/api/goal-entries",
      payload: { goalId: 1, occurredAt: 10, localDate: TestDate, localMinute: 600, value: 6_000 },
    })).statusCode).toBe(201);
    expect((await app.inject({
      method: "PUT",
      url: "/api/goal-subgoals/2/completion",
      payload: { completed: true, occurredAt: 20, localDate: TestDate },
    })).statusCode).toBe(409);
    const completed = await app.inject({
      method: "PUT",
      url: "/api/goal-subgoals/1/completion",
      payload: { completed: true, occurredAt: 20, localDate: TestDate },
    });
    expect(completed.statusCode).toBe(200);
    expect(completed.json().goal).toMatchObject({ currentValue: 6_000, subgoals: [{ completedAt: 20 }, { completedAt: null }] });
    expect((await getOverview()).goals[0]).toMatchObject({
      currentValue: 6_000,
      subgoals: [{ completedAt: 20 }, { completedAt: null }],
    });
    await app.inject({
      method: "POST",
      url: "/api/goal-entries",
      payload: { goalId: 1, occurredAt: 25, localDate: TestDate, localMinute: 601, value: 2_000 },
    });
    expect((await app.inject({
      method: "PUT",
      url: "/api/goal-subgoals/2/completion",
      payload: { completed: true, occurredAt: 30, localDate: TestDate },
    })).statusCode).toBe(200);
    expect((await app.inject({
      method: "POST",
      url: "/api/goal-entries",
      payload: { goalId: 1, occurredAt: 31, localDate: TestDate, localMinute: 602, value: 1 },
    })).statusCode).toBe(400);
    expect((await app.inject({ method: "POST", url: "/api/goals", payload: goalInput() })).statusCode).toBe(201);
    expect((await app.inject({
      method: "PUT",
      url: "/api/goal-subgoals/2/completion",
      payload: { completed: false, occurredAt: 32, localDate: TestDate },
    })).statusCode).toBe(400);
    expect((await app.inject({
      method: "POST",
      url: "/api/goal-entries",
      payload: { goalId: 1, occurredAt: 30, localDate: Yesterday, localMinute: 600, value: 1 },
    })).statusCode).toBe(400);
  });

  it("returns Today tasks, manual recommendations, and immutable template snapshots", async () => {
    await createItem(itemInput());
    await createEntry(entryInput({ value: 2 }));
    await createTemplate({ title: "Exercise", cadence: "DAILY", steps: [] });
    await createTemplate({ title: "Laundry", cadence: "MANUAL", steps: ["Wash", "Dry"] });

    let overview = await getOverview();
    expect(overview).toMatchObject({
      items: [{ name: "Water" }],
      templates: [{ title: "Exercise", steps: [] }, { title: "Laundry" }],
      todayTasks: [],
      dailySuggestions: [{ title: "Exercise" }],
      unfinishedTasks: [],
      dailyFocus: null,
      accumulateEntries: [{ value: 2 }],
    });

    let today = (await createTaskFromTemplate(2)).json().today;
    expect(today.todayTasks[0]).toMatchObject({ title: "Laundry", templateId: 2, completedAt: null, steps: [{ title: "Wash" }, { title: "Dry" }] });
    await app.inject({ method: "PUT", url: "/api/templates/2", payload: templateInput({ title: "Changed", steps: ["New"] }) });
    today = await getToday();
    expect(today.todayTasks[0]).toMatchObject({ title: "Laundry", steps: [{ title: "Wash" }, { title: "Dry" }] });

    await createTaskFromTemplate(1);
    overview = await getOverview();
    expect(overview.dailySuggestions).toEqual([]);
    expect((await archiveTemplates([2], true)).statusCode).toBe(200);
    expect((await app.inject({ method: "DELETE", url: "/api/templates/2" })).statusCode).toBe(200);
    expect((await getToday()).todayTasks[0].templateId).toBeNull();
  });

  it("tracks title tasks and partial checklists with task revisions", async () => {
    let today = (await createTask({ title: "Laundry", steps: ["Wash", "Dry"] })).json().today;
    const task = today.todayTasks[0];
    today = (await setTaskStep(task.steps[0].id, true, 0, 1_000)).json().today;
    expect(today.todayTasks[0]).toMatchObject({ completedAt: null, revision: 1, steps: [{ completedAt: 1_000 }, { completedAt: null }] });
    today = (await setTaskStep(task.steps[1].id, true, 1, 2_000)).json().today;
    expect(today.todayTasks[0]).toMatchObject({ completedAt: 2_000, revision: 2 });
    expect((await setTaskStep(task.steps[0].id, false, 0, 3_000)).statusCode).toBe(409);

    today = (await setTask(task.id, false, 2, 4_000)).json().today;
    expect(today.todayTasks[0]).toMatchObject({ completedAt: null, revision: 3, steps: [{ completedAt: null }, { completedAt: null }] });
    today = (await createTask({ title: "Call", steps: [] })).json().today;
    const titleTask = today.todayTasks[1];
    expect((await setTask(titleTask.id, true, 0, 5_000)).json().today.todayTasks[1]).toMatchObject({ completedAt: 5_000, revision: 1 });

    const actions = (await getAuditLogs()).map((log) => log.action);
    expect(actions).toEqual(expect.arrayContaining(["TODAY_TASK_CREATED", "TODAY_TASK_COMPLETED", "TODAY_TASK_REOPENED", "TODAY_TASK_STEP_COMPLETED"]));
  });

  it("offers yesterday's unfinished task once and copies only remaining checklist steps", async () => {
    let today = (await createTask({ localDate: Yesterday, title: "Laundry", steps: ["Wash", "Dry"] })).json().today;
    const source = today.todayTasks[0];
    await setTaskStep(source.steps[0].id, true, 0, 1_000);

    today = await getToday();
    expect(today.unfinishedTasks[0]).toMatchObject({ id: source.id, steps: [{ completedAt: 1_000 }, { completedAt: null }] });
    const continued = await app.inject({ method: "POST", url: "/api/today-tasks/from-task", payload: { localDate: TestDate, sourceTaskId: source.id } });
    expect(continued.statusCode).toBe(201);
    expect(continued.json().today.todayTasks[0]).toMatchObject({ sourceTaskId: source.id, title: "Laundry", steps: [{ title: "Dry", completedAt: null }] });
    expect(continued.json().today.unfinishedTasks).toEqual([]);
    expect((await app.inject({ method: "POST", url: "/api/today-tasks/from-task", payload: { localDate: TestDate, sourceTaskId: source.id } })).statusCode).toBe(409);
  });

  it("counts only Focus reviews and reaches enough with unrelated tasks still open", async () => {
    await createTask({ title: "Core", steps: [] });
    await createTask({ title: "Backup", steps: [] });
    await createTask({ title: "Laundry", steps: [] });
    let focus = await createFocus(1, 2);
    focus = (await focusTransition("start", { expectedRevision: 0, occurredAt: 1_000 })).json().dailyFocus;
    expect(focus).toMatchObject({ state: "ACTIVE", currentTaskId: 1, completedRounds: 0, revision: 1 });

    const sideResult = await setTask(3, true, 0, 2_000);
    expect(sideResult.json().today.dailyFocus).toMatchObject({ state: "ACTIVE", currentTaskId: 1, revision: 1 });
    await createTask({ title: "Errand", steps: [] });

    for (let round = 1; round <= 4; round += 1) {
      const response = await focusTransition("review-now", { expectedRevision: focus.revision, occurredAt: 3_000 + round, currentTaskId: 1 });
      expect(response.statusCode).toBe(200);
      focus = response.json().dailyFocus;
      expect(focus.completedRounds).toBe(round);
    }
    expect(focus).toMatchObject({ state: "PAUSED", pauseReason: "TARGET_REACHED", currentTaskId: null, revision: 5 });
    expect((await getToday()).todayTasks.find((task: { title: string }) => task.title === "Errand").completedAt).toBeNull();

    focus = (await focusTransition("continue", { expectedRevision: 5, occurredAt: 9_000, primaryTaskId: 4, secondaryTaskId: null })).json().dailyFocus;
    expect(focus).toMatchObject({ state: "ACTIVE", primaryTaskId: 4, currentTaskId: 4, completedRounds: 4, revision: 6 });
  });

  it("pauses when selected Focus is cleared and never resumes from task reopening", async () => {
    await createTask({ title: "Core", steps: [] });
    await createTask({ title: "Backup", steps: [] });
    let focus = await createFocus(1, 2);
    focus = (await focusTransition("start", { expectedRevision: 0, occurredAt: 1_000 })).json().dailyFocus;

    let today = (await setTask(1, true, 0, 2_000)).json().today;
    expect(today.dailyFocus).toMatchObject({ state: "ACTIVE", currentTaskId: 2, revision: 2 });
    today = (await setTask(2, true, 0, 3_000)).json().today;
    expect(today.dailyFocus).toMatchObject({ state: "PAUSED", pauseReason: "FOCUS_CLEARED", currentTaskId: null, revision: 3 });

    today = (await setTask(1, false, 1, 4_000)).json().today;
    expect(today.dailyFocus).toMatchObject({ state: "PAUSED", pauseReason: "FOCUS_CLEARED", revision: 3 });
    focus = (await focusTransition("refocus", { expectedRevision: 3, occurredAt: 5_000, primaryTaskId: 1, secondaryTaskId: null })).json().dailyFocus;
    expect(focus).toMatchObject({ state: "ACTIVE", currentTaskId: 1, completedRounds: 0, revision: 4 });
  });

  it("requires every selected Focus task to remain open before starting", async () => {
    await createTask({ title: "Core", steps: [] });
    await createTask({ title: "Backup", steps: [] });
    await createFocus(1, 2);
    await setTask(2, true, 0, 1_000);

    expect((await focusTransition("start", { expectedRevision: 0, occurredAt: 2_000 })).statusCode).toBe(409);
    expect((await getToday()).dailyFocus).toMatchObject({ state: "READY", revision: 0 });
  });

  it("rejects invalid input and stale Focus commands without partial writes", async () => {
    expect((await createTemplate({ title: " ", steps: [] })).statusCode).toBe(400);
    expect((await createTemplate({ title: "Task", steps: [" "] })).statusCode).toBe(400);
    expect((await createTask({ title: " ", steps: [] })).statusCode).toBe(400);
    await createTask({ title: "Core", steps: [] });
    await createFocus(1, null);
    expect((await focusTransition("start", { expectedRevision: 1, occurredAt: 1_000 })).statusCode).toBe(409);
    expect((await getToday()).dailyFocus).toMatchObject({ state: "READY", revision: 0 });
  });

  async function createItem(payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/items", payload });
  }

  async function getItems(archived: boolean): Promise<Array<Record<string, unknown>>> {
    return (await app.inject({ method: "GET", url: `/api/items?archived=${archived}` })).json().items;
  }

  async function archiveItems(ids: number[], archived: boolean) {
    return app.inject({ method: "POST", url: "/api/items/batch-archive", payload: { ids, archived } });
  }

  async function createEntry(payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/entries", payload });
  }

  async function getEntries(): Promise<Array<Record<string, unknown>>> {
    return (await app.inject({ method: "GET", url: `/api/entries?startDate=${Yesterday}&endDate=${TestDate}` })).json().entries;
  }

  async function createTemplate(patch: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/templates", payload: templateInput(patch) });
  }

  async function archiveTemplates(ids: number[], archived: boolean) {
    return app.inject({ method: "POST", url: "/api/templates/batch-archive", payload: { ids, archived } });
  }

  async function createTask(patch: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/today-tasks", payload: taskInput(patch) });
  }

  async function createTaskFromTemplate(templateId: number) {
    return app.inject({ method: "POST", url: "/api/today-tasks/from-template", payload: { localDate: TestDate, templateId } });
  }

  async function getToday(localDate = TestDate) {
    const response = await app.inject({ method: "GET", url: `/api/today/${localDate}` });
    expect(response.statusCode).toBe(200);
    return response.json().today;
  }

  async function getOverview() {
    const response = await app.inject({ method: "GET", url: `/api/overview?startDate=${TestDate}&endDate=${TestDate}&planDate=${TestDate}` });
    expect(response.statusCode).toBe(200);
    return response.json();
  }

  async function setTask(id: number, completed: boolean, expectedRevision: number, occurredAt: number) {
    return app.inject({ method: "PUT", url: `/api/today-tasks/${id}/completion`, payload: { completed, expectedRevision, occurredAt } });
  }

  async function setTaskStep(id: number, completed: boolean, expectedRevision: number, occurredAt: number) {
    return app.inject({ method: "PUT", url: `/api/today-task-steps/${id}/completion`, payload: { completed, expectedRevision, occurredAt } });
  }

  async function createFocus(primaryTaskId: number, secondaryTaskId: number | null) {
    const response = await app.inject({ method: "POST", url: "/api/daily-focus", payload: { localDate: TestDate, firstMove: "Open the work", primaryTaskId, secondaryTaskId } });
    expect(response.statusCode).toBe(201);
    return response.json().dailyFocus;
  }

  async function focusTransition(command: string, payload: Record<string, unknown>) {
    return app.inject({ method: "PUT", url: `/api/daily-focus/${TestDate}/${command}`, payload });
  }

  async function getAuditLogs(): Promise<Array<Record<string, unknown>>> {
    return (await app.inject({ method: "GET", url: "/api/audit-logs" })).json().logs;
  }
});

function templateInput(patch: Record<string, unknown> = {}) {
  const input = { title: "Template", cadence: "MANUAL", steps: [] as unknown[], ...patch };
  return { ...input, steps: Array.isArray(input.steps) ? input.steps.map((step) => typeof step === "string" ? { title: step } : step) : input.steps };
}

function taskInput(patch: Record<string, unknown> = {}) {
  const input = { localDate: TestDate, title: "Task", steps: [] as unknown[], ...patch };
  return { ...input, steps: Array.isArray(input.steps) ? input.steps.map((step) => typeof step === "string" ? { title: step } : step) : input.steps };
}

function itemInput(patch: Record<string, unknown> = {}) {
  return { name: "Water", description: "", mode: "ACCUMULATE", valueType: "NUMBER", defaultValue: 1, minValue: null, maxValue: null, stepValue: 1, unit: "times", goalValue: null, goalPeriod: null, priority: 1, ...patch };
}

function entryInput(patch: Record<string, unknown> = {}) {
  return { itemId: 1, occurredAt: 10, localDate: TestDate, localMinute: 600, value: 1, ...patch };
}

function goalInput(patch: Record<string, unknown> = {}) {
  return {
    title: "Build the kit",
    description: "",
    unit: "points",
    defaultValue: 100,
    stepValue: 100,
    startDate: TestDate,
    endDate: "2027-07-20",
    subgoals: [
      { id: null, title: "Phone", targetValue: 5_000 },
      { id: null, title: "Bike", targetValue: 8_000 },
    ],
    ...patch,
  };
}
