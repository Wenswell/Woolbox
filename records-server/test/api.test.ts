import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import { buildApp } from "../src/app.js";

const TestDate = "2026-07-20";

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

  it("migrates an empty database and serves item CRUD", async () => {
    expect(await getItems(false)).toEqual([]);

    const created = await createItem(itemInput({ name: "  Water  ", description: " drink ", unit: " ml " }));
    expect(created.statusCode).toBe(201);
    expect(created.json().item).toMatchObject({
      id: 1,
      name: "Water",
      description: "drink",
      mode: "ACCUMULATE",
      unit: "ml",
      archived: false,
      createdAt: 1_700_000_000_000,
    });

    const updated = await app.inject({
      method: "PUT",
      url: "/api/items/1",
      payload: itemInput({ name: "Hydration" }),
    });
    expect(updated.statusCode).toBe(200);
    expect(updated.json().item).toMatchObject({ name: "Hydration" });

    expect((await getAuditLogs()).map((log) => log.action)).toEqual(["ITEM_UPDATED", "ITEM_CREATED"]);
    expect((await getAuditLogs())[0]).toMatchObject({
      entityType: "ITEM",
      entityId: 1,
      before: { name: "Water" },
      after: { name: "Hydration" },
    });

    const unchanged = await app.inject({
      method: "PUT",
      url: "/api/items/1",
      payload: itemInput({ name: "Hydration" }),
    });
    expect(unchanged.statusCode).toBe(200);
    expect((await getAuditLogs()).map((log) => log.action)).toEqual(["ITEM_UPDATED", "ITEM_CREATED"]);
  });

  it.each([
    ["empty name", { name: " " }],
    ["empty unit", { unit: " " }],
    ["non-positive default", { defaultValue: 0 }],
    ["non-positive step", { stepValue: -1 }],
    ["non-positive goal", { goalValue: 0, goalPeriod: "DAY" }],
    ["missing goal period", { goalValue: 2, goalPeriod: null }],
    ["invalid duration unit", { valueType: "DURATION", unit: "ml" }],
    ["manual time windows", { windows: [{ startMinute: 60, endMinute: 120 }] }],
    ["measurement duration", { mode: "MEASUREMENT", valueType: "DURATION", unit: "分" }],
    ["measurement goal", { mode: "MEASUREMENT", goalValue: 2, goalPeriod: "DAY" }],
    ["incomplete measurement fields", { mode: "MEASUREMENT" }],
  ])("rejects %s without changing tables", async (_label, patch) => {
    const response = await createItem(itemInput(patch));
    expect(response.statusCode).toBe(400);
    expect(await getItems(false)).toEqual([]);
    expect(await getAuditLogs()).toEqual([]);
  });

  it("returns a client error for malformed JSON without writing", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/api/items",
      headers: { "content-type": "application/json" },
      payload: "{",
    });
    expect(response.statusCode).toBe(400);
    expect(await getItems(false)).toEqual([]);
  });

  it("enforces case-insensitive names across active and archived items", async () => {
    expect((await createItem(itemInput({ name: "Water" }))).statusCode).toBe(201);
    expect((await archiveItems([1], true)).statusCode).toBe(200);

    const duplicate = await createItem(itemInput({ name: "water" }));
    expect(duplicate.statusCode).toBe(409);
    expect(await getItems(false)).toEqual([]);
    expect(await getItems(true)).toHaveLength(1);
  });

  it("rolls back item changes when measurement fields change after entries exist", async () => {
    await createItem(itemInput({ name: "Water", unit: "ml" }));
    await createEntry(entryInput({ itemId: 1 }));

    const response = await app.inject({
      method: "PUT",
      url: "/api/items/1",
      payload: itemInput({
        name: "Changed",
        valueType: "DURATION",
        unit: "分",
      }),
    });
    expect(response.statusCode).toBe(400);
    expect((await getItems(false))[0]).toMatchObject({
      name: "Water",
      valueType: "NUMBER",
      unit: "ml",
    });
  });

  it("archives and restores batches atomically", async () => {
    await createItem(itemInput({ name: "Water" }));
    await createItem(itemInput({ name: "Read" }));

    const missing = await archiveItems([1, 3], true);
    expect(missing.statusCode).toBe(404);
    expect(await getItems(false)).toHaveLength(2);
    expect(await getItems(true)).toEqual([]);

    const archived = await archiveItems([1, 2], true);
    expect(archived.statusCode).toBe(200);
    expect(archived.json().items.every((item: { archived: boolean }) => item.archived)).toBe(true);
    expect(await getItems(true)).toHaveLength(2);

    expect((await archiveItems([2], false)).statusCode).toBe(200);
    expect((await getItems(false)).map((item) => item.name)).toEqual(["Read"]);
    expect((await getAuditLogs()).map((log) => log.action)).toEqual([
      "ITEM_RESTORED",
      "ITEM_ARCHIVED",
      "ITEM_ARCHIVED",
      "ITEM_CREATED",
      "ITEM_CREATED",
    ]);
  });

  it("rejects recording archived items and only permanently deletes archived items", async () => {
    await createItem(itemInput());
    const activeDelete = await app.inject({ method: "DELETE", url: "/api/items/1" });
    expect(activeDelete.statusCode).toBe(400);

    await archiveItems([1], true);
    const create = await createEntry(entryInput({ itemId: 1 }));
    expect(create.statusCode).toBe(400);

    const deleted = await app.inject({ method: "DELETE", url: "/api/items/1" });
    expect(deleted.statusCode).toBe(200);
    expect(deleted.json().item.id).toBe(1);
    expect(await getItems(true)).toEqual([]);
  });

  it("creates, filters by item and mode, and deletes entries", async () => {
    await createItem(itemInput({ name: "Water", unit: "ml" }));
    await createItem(itemInput({
      name: "Temperature",
      mode: "MEASUREMENT",
      defaultValue: null,
      minValue: 0,
      maxValue: 10,
      unit: "°C",
    }));
    await createEntry(entryInput({ itemId: 1, localDate: "2026-07-19", localMinute: 600, value: 2 }));
    await createEntry(entryInput({ itemId: 2, localDate: "2026-07-20", localMinute: 700, value: 3 }));

    const filtered = await app.inject({ method: "GET", url: "/api/entries?startDate=2026-07-19&endDate=2026-07-20&itemId=2" });
    expect(filtered.statusCode).toBe(200);
    expect(filtered.json().entries).toEqual([
      expect.objectContaining({ id: 2, itemId: 2, itemName: "Temperature", localDate: "2026-07-20" }),
    ]);

    const accumulated = await app.inject({ method: "GET", url: "/api/entries?startDate=2026-07-19&endDate=2026-07-20&mode=ACCUMULATE" });
    expect(accumulated.statusCode).toBe(200);
    expect(accumulated.json().entries).toEqual([expect.objectContaining({ id: 1, itemMode: "ACCUMULATE" })]);

    const measurements = await app.inject({ method: "GET", url: "/api/entries?startDate=2026-07-19&endDate=2026-07-20&mode=MEASUREMENT" });
    expect(measurements.statusCode).toBe(200);
    expect(measurements.json().entries).toEqual([expect.objectContaining({ id: 2, itemMode: "MEASUREMENT" })]);

    const invalidMode = await app.inject({ method: "GET", url: "/api/entries?startDate=2026-07-19&endDate=2026-07-20&mode=UNKNOWN" });
    expect(invalidMode.statusCode).toBe(400);

    const deleted = await app.inject({ method: "DELETE", url: "/api/entries/1" });
    expect(deleted.statusCode).toBe(200);
    expect(deleted.json().entry.id).toBe(1);
    expect((await getAuditLogs()).slice(0, 2)).toMatchObject([
      { action: "ENTRY_DELETED", before: { id: 1, itemName: "Water", value: 2 } },
      { action: "ENTRY_CREATED", after: { id: 2, itemName: "Temperature", value: 3 } },
    ]);
  });

  it("validates accumulated entry timestamps and values without writing", async () => {
    await createItem(itemInput());
    for (const patch of [
      { value: 0 },
      { localDate: "2026-02-30" },
      { localMinute: -1 },
      { localMinute: 1440 },
      { itemId: 2 },
    ]) {
      const response = await createEntry(entryInput(patch));
      expect([400, 404]).toContain(response.statusCode);
    }
    expect(await getEntries()).toEqual([]);
  });

  it("accepts only measurement values on the configured signed range and step", async () => {
    const created = await createItem(itemInput({
      name: "Temperature",
      mode: "MEASUREMENT",
      defaultValue: null,
      minValue: -10,
      maxValue: 10,
      stepValue: 0.5,
      unit: "°C",
    }));
    expect(created.statusCode).toBe(201);
    expect(created.json().item).toMatchObject({
      mode: "MEASUREMENT",
      valueType: "NUMBER",
      defaultValue: null,
      minValue: -10,
      maxValue: 10,
      goalValue: null,
    });

    expect((await createEntry(entryInput({ itemId: 1, value: 0 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 1, value: -5.5 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 1, value: -5.25 }))).statusCode).toBe(400);
    expect((await createEntry(entryInput({ itemId: 1, value: 10.5 }))).statusCode).toBe(400);
    expect((await getEntries()).map((entry) => entry.value)).toEqual([-5.5, 0]);

    expect((await createItem(itemInput({ name: "Water" }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 2, value: 1 }))).statusCode).toBe(201);

    const latest = await app.inject({ method: "GET", url: "/api/entries/latest" });
    expect(latest.statusCode).toBe(200);
    expect(latest.json().entries).toEqual([
      expect.objectContaining({ itemId: 1, itemMode: "MEASUREMENT", value: -5.5 }),
    ]);

    const invalid = await createItem(itemInput({
      name: "Invalid measurement",
      mode: "MEASUREMENT",
      valueType: "DURATION",
      unit: "分",
    }));
    expect(invalid.statusCode).toBe(400);
  });

  it("returns the active Records root-page projection in one request", async () => {
    await createItem(itemInput({ name: "Water" }));
    await createItem(itemInput({
      name: "Temperature",
      mode: "MEASUREMENT",
      defaultValue: null,
      minValue: 0,
      maxValue: 10,
      unit: "°C",
    }));
    await createItem(itemInput({
      name: "Weight",
      mode: "MEASUREMENT",
      defaultValue: null,
      minValue: 0,
      maxValue: 300,
      unit: "kg",
    }));
    await createItem(itemInput({ name: "Archived" }));
    await archiveItems([4], true);
    await createTask({ title: "Pack", steps: ["Bag"] });
    await createEntry(entryInput({ itemId: 1, localDate: "2026-07-19", value: 2 }));
    await createEntry(entryInput({ itemId: 2, occurredAt: 20, value: 3 }));
    await createEntry(entryInput({ itemId: 2, occurredAt: 30, value: 4 }));
    await createEntry(entryInput({ itemId: 3, occurredAt: 40, value: 70 }));

    const response = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2026-07-19&endDate=2026-07-20&planDate=2026-07-20",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      items: [
        { id: 2, name: "Temperature", archived: false },
        { id: 1, name: "Water", archived: false },
        { id: 3, name: "Weight", archived: false },
      ],
      tasks: [{ id: 1, title: "Pack", steps: [{ title: "Bag" }] }],
      dailyPlan: null,
      accumulateEntries: [{ itemId: 1, value: 2, itemMode: "ACCUMULATE" }],
      latestMeasurementEntries: [
        { itemId: 2, value: 4, itemMode: "MEASUREMENT" },
        { itemId: 3, value: 70, itemMode: "MEASUREMENT" },
      ],
    });
    expect(response.json().items).not.toContainEqual(expect.objectContaining({ name: "Archived" }));
  });

  it("returns every accumulate entry inside a month-long overview range", async () => {
    // The root page learns habit times and streaks from this range, so the
    // window is the caller's to choose and nothing inside it may be dropped.
    await createItem(itemInput({ name: "Water" }));
    for (const day of ["2026-06-21", "2026-07-05", "2026-07-20"]) {
      await createEntry(entryInput({ itemId: 1, localDate: day, localMinute: 540, value: 2 }));
    }

    const response = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2026-06-21&endDate=2026-07-20&planDate=2026-07-20",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json().accumulateEntries).toMatchObject([
      { localDate: "2026-07-20", localMinute: 540 },
      { localDate: "2026-07-05", localMinute: 540 },
      { localDate: "2026-06-21", localMinute: 540 },
    ]);

    const narrower = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2026-07-01&endDate=2026-07-20&planDate=2026-07-20",
    });
    expect(narrower.json().accumulateEntries).toHaveLength(2);
  });

  it("creates tasks with subtasks atomically and derives completion from them", async () => {
    const created = await createTask({ title: "  Prepare trip  ", steps: [" Book train ", "Pack bag"] });
    expect(created.statusCode).toBe(201);
    expect(created.json().task).toMatchObject({
      id: 1,
      title: "Prepare trip",
      completedAt: null,
      steps: [
        { id: 1, title: "Book train", completedAt: null },
        { id: 2, title: "Pack bag", completedAt: null },
      ],
    });

    expect((await setStepCompleted(1, true)).statusCode).toBe(200);
    expect((await getTasks())[0]).toMatchObject({ completedAt: null });

    const childDerivedComplete = await setStepCompleted(2, true);
    expect(childDerivedComplete.statusCode).toBe(200);
    expect((await getTasks())[0]).toMatchObject({
      completedAt: 1_700_000_000_000,
      steps: [
        { id: 1, completedAt: 1_700_000_000_000 },
        { id: 2, completedAt: 1_700_000_000_000 },
      ],
    });

    await setStepCompleted(1, false);
    expect((await getTasks())[0]).toMatchObject({ completedAt: null });

    const added = await createStep(1, { title: "Check documents" });
    expect(added.statusCode).toBe(201);
    expect((await setStepCompleted(3, true)).statusCode).toBe(200);
    const deleted = await app.inject({ method: "DELETE", url: `/api/steps/1?localDate=${TestDate}` });
    expect(deleted.statusCode).toBe(200);
    expect((await getTasks())[0]).toMatchObject({
      completedAt: 1_700_000_000_000,
      steps: [
        expect.objectContaining({ id: 2, completedAt: 1_700_000_000_000 }),
        expect.objectContaining({ id: 3, completedAt: 1_700_000_000_000 }),
      ],
    });

    const directComplete = await app.inject({ method: "PUT", url: "/api/tasks/1/completion", payload: { completed: true } });
    expect(directComplete.statusCode).toBe(404);

    expect((await app.inject({ method: "DELETE", url: `/api/steps/2?localDate=${TestDate}` })).statusCode).toBe(200);
    expect((await app.inject({ method: "DELETE", url: `/api/steps/3?localDate=${TestDate}` })).statusCode).toBe(400);

    const renamed = await app.inject({
      method: "PUT",
      url: "/api/tasks/1",
      payload: { title: "Prepare journey", repeatMode: "ONCE", localDate: TestDate },
    });
    expect(renamed.statusCode).toBe(200);
    expect(renamed.json().task.title).toBe("Prepare journey");
    expect((await getAuditLogs()).map((log) => log.action)).toContain("TASK_COMPLETED");
    expect((await getAuditLogs()).map((log) => log.action)).toContain("STEP_REOPENED");
  });

  it("rejects empty task titles and does not write partial task data", async () => {
    const task = await createTask({ title: " ", steps: ["Prepare"] });
    expect(task.statusCode).toBe(400);
    const missingSteps = await createTask({ title: "Prepare" });
    expect(missingSteps.statusCode).toBe(400);
    const emptyStep = await createTask({ title: "Prepare", steps: [" "] });
    expect(emptyStep.statusCode).toBe(400);
    expect(await getTasks()).toEqual([]);

    const step = await createStep(1, { title: "Missing parent" });
    expect(step.statusCode).toBe(404);
    expect(await getTasks()).toEqual([]);
    expect(await getAuditLogs()).toEqual([]);
  });

  it("keeps one daily task while completion is isolated by local date", async () => {
    const created = await createTask({
      title: "Daily review",
      repeatMode: "DAILY",
      steps: ["Open notes", "Write summary"],
    });
    expect(created.statusCode).toBe(201);
    expect(created.json().task).toMatchObject({ repeatMode: "DAILY", completedAt: null });

    await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: { localDate: TestDate, firstMove: "Open notes", primaryTaskId: 1, secondaryTaskId: null },
    });
    expect((await setStepCompleted(1, true, "2026-07-21")).statusCode).toBe(200);
    expect((await getDailyPlan(TestDate))?.state).toBe("READY");

    expect((await setStepCompleted(1, true)).statusCode).toBe(200);
    expect((await setStepCompleted(2, true)).statusCode).toBe(200);
    expect(await getTasks()).toMatchObject([{
      id: 1,
      completedAt: 1_700_000_000_000,
      steps: [{ completedAt: 1_700_000_000_000 }, { completedAt: 1_700_000_000_000 }],
    }]);
    expect((await getDailyPlan(TestDate))?.state).toBe("ENOUGH");

    expect(await getTasks("2026-07-21")).toMatchObject([{
      id: 1,
      completedAt: null,
      steps: [{ completedAt: 1_700_000_000_000 }, { completedAt: null }],
    }]);
    expect(await getTasks("2026-07-22")).toMatchObject([{
      id: 1,
      completedAt: null,
      steps: [{ completedAt: null }, { completedAt: null }],
    }]);
  });

  it("resets the target scope when repeat mode changes and protects Today references", async () => {
    await createTask({ title: "Reusable", steps: ["Finish"] });
    await setStepCompleted(1, true);

    const changed = await app.inject({
      method: "PUT",
      url: "/api/tasks/1",
      payload: { title: "Reusable", repeatMode: "DAILY", localDate: TestDate },
    });
    expect(changed.statusCode).toBe(200);
    expect(changed.json().task).toMatchObject({ repeatMode: "DAILY", completedAt: null });

    await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: { localDate: TestDate, firstMove: "Start", primaryTaskId: 1, secondaryTaskId: null },
    });
    const protectedChange = await app.inject({
      method: "PUT",
      url: "/api/tasks/1",
      payload: { title: "Reusable", repeatMode: "ONCE", localDate: TestDate },
    });
    expect(protectedChange.statusCode).toBe(409);
    expect((await getTasks())[0]).toMatchObject({ repeatMode: "DAILY", completedAt: null });
  });

  it("batch deletes entries atomically and returns deleted resources", async () => {
    await createItem(itemInput());
    await createEntry(entryInput({ value: 1 }));
    await createEntry(entryInput({ value: 2 }));

    const missing = await app.inject({ method: "POST", url: "/api/entries/batch-delete", payload: { ids: [1, 3] } });
    expect(missing.statusCode).toBe(404);
    expect(await getEntries()).toHaveLength(2);

    const deleted = await app.inject({ method: "POST", url: "/api/entries/batch-delete", payload: { ids: [2, 1] } });
    expect(deleted.statusCode).toBe(200);
    expect(deleted.json().entries.map((entry: { id: number }) => entry.id)).toEqual([2, 1]);
    expect(await getEntries()).toEqual([]);
    expect((await getAuditLogs()).slice(0, 2)).toMatchObject([
      { action: "ENTRY_DELETED", entityId: 2, before: { value: 2 } },
      { action: "ENTRY_DELETED", entityId: 1, before: { value: 1 } },
    ]);
  });

  async function createItem(payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/items", payload });
  }

  async function getItems(archived: boolean): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: `/api/items?archived=${archived}` });
    expect(response.statusCode).toBe(200);
    return response.json().items;
  }

  async function archiveItems(ids: number[], archived: boolean) {
    return app.inject({ method: "POST", url: "/api/items/batch-archive", payload: { ids, archived } });
  }

  async function createEntry(payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/entries", payload });
  }

  async function getEntries(): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: "/api/entries?startDate=2026-07-01&endDate=2026-07-31" });
    expect(response.statusCode).toBe(200);
    return response.json().entries;
  }

  it("reorders subtasks and preserves completion state", async () => {
    const created = await createTask({ title: "Pack", steps: ["A", "B", "C"] });
    expect(created.statusCode).toBe(201);
    const task = created.json().task as { id: number; steps: Array<{ id: number; title: string }> };
    const ids = task.steps.map((step) => step.id);
    await setStepCompleted(ids[1]!, true);

    const reordered = await app.inject({
      method: "PUT",
      url: `/api/tasks/${task.id}/steps/order`,
      payload: { ids: [ids[2], ids[0], ids[1]], localDate: TestDate },
    });
    expect(reordered.statusCode).toBe(200);
    const steps = reordered.json().task.steps as Array<{ id: number; title: string; completedAt: number | null }>;
    expect(steps.map((step) => step.title)).toEqual(["C", "A", "B"]);
    expect(steps.map((step) => step.id)).toEqual([ids[2], ids[0], ids[1]]);
    expect(steps[2]?.completedAt).not.toBeNull();

    const listed = await getTasks();
    const listedTask = listed.find((item) => item.id === task.id) as { steps: Array<{ title: string }> };
    expect(listedTask.steps.map((step) => step.title)).toEqual(["C", "A", "B"]);

    const incomplete = await app.inject({
      method: "PUT",
      url: `/api/tasks/${task.id}/steps/order`,
      payload: { ids: [ids[0], ids[1]], localDate: TestDate },
    });
    expect(incomplete.statusCode).toBe(400);

    const foreign = await app.inject({
      method: "PUT",
      url: `/api/tasks/${task.id}/steps/order`,
      payload: { ids: [ids[0], ids[1], 999999], localDate: TestDate },
    });
    expect(foreign.statusCode).toBe(400);
  });

  it("rejects removed manual time-window fields", async () => {
    const item = await createItem(itemInput({
      windows: [{ startMinute: 480, endMinute: 540 }],
    }));
    expect(item.statusCode).toBe(400);
    expect(item.json().error.message).toBe("Manual time windows are not supported.");

    const task = await createTask({
      title: "Morning routine",
      steps: [{ title: "Stretch", windows: [{ startMinute: 480, endMinute: 540 }] }],
    });
    expect(task.statusCode).toBe(400);
    expect(task.json().error.message).toBe("Manual time windows are not supported.");

    const created = await createTask({ title: "Routine", steps: ["Stretch"] });
    expect(created.statusCode).toBe(201);
    const step = await app.inject({
      method: "PUT",
      url: "/api/steps/1",
      payload: { title: "Stretch", windows: [], localDate: TestDate },
    });
    expect(step.statusCode).toBe(400);
    expect(step.json().error.message).toBe("Manual time windows are not supported.");
  });

  it("adds plain subtasks, rejects task dates, and cascades deletes", async () => {
    const created = await createTask({ title: "Pack", steps: ["Bag"] });
    expect(created.statusCode).toBe(201);
    const taskId = created.json().task.id as number;

    const added = await createStep(taskId, {
      title: "Evening check",
    });
    expect(added.statusCode).toBe(201);
    const stepId = added.json().step.id as number;

    const removedDate = await app.inject({
      method: "PUT",
      url: `/api/tasks/${taskId}`,
      payload: { title: "Pack", repeatMode: "ONCE", localDate: TestDate, plannedDate: null },
    });
    expect(removedDate.statusCode).toBe(400);
    expect(removedDate.json().error.message).toBe("Task dates are not supported.");

    expect((await app.inject({ method: "DELETE", url: `/api/steps/${stepId}?localDate=${TestDate}` })).statusCode).toBe(200);
    const remaining = (await getTasks())[0] as { steps: Array<{ id: number }> };
    expect(remaining.steps.map((step) => step.id)).toEqual([1]);

    expect((await app.inject({ method: "DELETE", url: `/api/tasks/${taskId}?localDate=${TestDate}` })).statusCode).toBe(200);
    expect(await getTasks()).toEqual([]);
  });

  it("runs one daily plan through four reviews, an extra round, and explicit end", async () => {
    await createTask({ title: "Build feature", steps: ["Implement"] });
    await createTask({ title: "Write notes", steps: ["Draft"] });

    const created = await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: {
        localDate: "2026-07-20",
        firstMove: "  Go to the library  ",
        primaryTaskId: 1,
        secondaryTaskId: 2,
      },
    });
    expect(created.statusCode).toBe(201);
    expect(created.json().dailyPlan).toMatchObject({
      firstMove: "Go to the library",
      state: "READY",
      completedRounds: 0,
      revision: 0,
    });
    expect((await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: { localDate: "2026-07-20", firstMove: "Start", primaryTaskId: 1, secondaryTaskId: null },
    })).statusCode).toBe(409);
    expect((await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: { localDate: "2026-07-21", firstMove: "Start", primaryTaskId: 1, secondaryTaskId: 1 },
    })).statusCode).toBe(400);

    let plan = (await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/start",
      payload: { expectedRevision: 0, occurredAt: 1_000 },
    })).json().dailyPlan;
    expect(plan).toMatchObject({ state: "ACTIVE", currentTaskId: 1, nextReviewAt: 3_601_000, revision: 1 });
    expect((await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/end",
      payload: { expectedRevision: 0, occurredAt: 2_000 },
    })).statusCode).toBe(409);

    const overview = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2026-07-20&endDate=2026-07-20&planDate=2026-07-20",
    });
    expect(overview.json().dailyPlan).toMatchObject({ state: "ACTIVE", revision: 1 });

    const early = await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/review",
      payload: { expectedRevision: 1, occurredAt: 3_600_999, currentTaskId: 1 },
    });
    expect(early.statusCode).toBe(409);

    for (let round = 1; round <= 4; round += 1) {
      const response = await app.inject({
        method: "PUT",
        url: "/api/daily-plans/2026-07-20/review",
        payload: { expectedRevision: plan.revision, occurredAt: plan.nextReviewAt, currentTaskId: 1 },
      });
      expect(response.statusCode).toBe(200);
      plan = response.json().dailyPlan;
      expect(plan.completedRounds).toBe(round);
    }
    expect(plan).toMatchObject({ state: "ENOUGH", currentTaskId: null, nextReviewAt: null, revision: 5 });

    plan = (await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/extra-round",
      payload: { expectedRevision: 5, occurredAt: 20_000_000, currentTaskId: 1 },
    })).json().dailyPlan;
    expect(plan).toMatchObject({ state: "ACTIVE", completedRounds: 4, currentTaskId: 1, revision: 6 });

    const ended = await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/end",
      payload: { expectedRevision: 6, occurredAt: 20_000_100 },
    });
    expect(ended.json().dailyPlan).toMatchObject({ state: "ENDED", endedAt: 20_000_100, revision: 7 });
    const actions = (await getAuditLogs()).map((log) => String(log.action));
    expect(actions.filter((action) => action.startsWith("DAILY_PLAN"))).toEqual([
      "DAILY_PLAN_ENDED",
      "DAILY_PLAN_CREATED",
    ]);
  });

  it("supports explicit Today administration without weakening normal review timing", async () => {
    await createTask({ title: "Primary", steps: ["Primary step"] });
    await createTask({ title: "Secondary", steps: ["Secondary step"] });
    await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: { localDate: TestDate, firstMove: "Start", primaryTaskId: 1, secondaryTaskId: 2 },
    });
    await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/start`,
      payload: { expectedRevision: 0, occurredAt: 1_000 },
    });

    expect((await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/review`,
      payload: { expectedRevision: 1, occurredAt: 2_000, currentTaskId: 1 },
    })).statusCode).toBe(409);

    let plan = (await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/review-now`,
      payload: { expectedRevision: 1, occurredAt: 2_000, currentTaskId: 1 },
    })).json().dailyPlan;
    expect(plan).toMatchObject({ state: "ACTIVE", completedRounds: 1, currentTaskId: 1, nextReviewAt: 3_602_000, revision: 2 });

    plan = (await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/switch`,
      payload: { expectedRevision: 2, occurredAt: 3_000, currentTaskId: 2 },
    })).json().dailyPlan;
    expect(plan).toMatchObject({ state: "ACTIVE", completedRounds: 1, currentTaskId: 2, nextReviewAt: 3_603_000, revision: 3 });
    expect((await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/switch`,
      payload: { expectedRevision: 3, occurredAt: 3_500, currentTaskId: 2 },
    })).statusCode).toBe(409);

    await setStepCompleted(1, true);
    plan = (await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/end`,
      payload: { expectedRevision: 3, occurredAt: 4_000 },
    })).json().dailyPlan;
    expect(plan).toMatchObject({ state: "ENDED", completedRounds: 1, endedAt: 4_000, revision: 4 });

    plan = (await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/reset`,
      payload: { expectedRevision: 4, occurredAt: 5_000 },
    })).json().dailyPlan;
    expect(plan).toMatchObject({
      state: "READY",
      firstMove: "Start",
      primaryTaskId: 1,
      secondaryTaskId: 2,
      firstMoveCompletedAt: null,
      currentTaskId: null,
      completedRounds: 0,
      nextReviewAt: null,
      endedAt: null,
      revision: 5,
    });
    expect((await getTasks())[0]).toMatchObject({ completedAt: expect.any(Number) });
    expect((await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/reset`,
      payload: { expectedRevision: 5, occurredAt: 6_000 },
    })).statusCode).toBe(409);

    const planAudits = (await getAuditLogs()).filter((log) => String(log.action).startsWith("DAILY_PLAN"));
    expect(planAudits.map((log) => log.action)).toEqual([
      "DAILY_PLAN_UPDATED",
      "DAILY_PLAN_ENDED",
      "DAILY_PLAN_UPDATED",
      "DAILY_PLAN_CREATED",
    ]);
    expect(planAudits[2]).toMatchObject({
      before: { currentTaskId: 1, completedRounds: 1, nextReviewAt: 3_602_000 },
      after: { currentTaskId: 2, completedRounds: 1, nextReviewAt: 3_603_000 },
    });
    expect(planAudits[0]).toMatchObject({
      before: { state: "ENDED", completedRounds: 1 },
      after: { state: "READY", completedRounds: 0, currentTaskId: null, nextReviewAt: null },
    });
  });

  it("replaces an active commitment and advances when committed tasks complete", async () => {
    await createTask({ title: "Old primary", steps: ["Old step"] });
    await createTask({ title: "Secondary", steps: ["Secondary step"] });
    await createTask({ title: "New primary", steps: ["New step"] });
    await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: { localDate: "2026-07-20", firstMove: "Start", primaryTaskId: 1, secondaryTaskId: 2 },
    });
    await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/start",
      payload: { expectedRevision: 0, occurredAt: 1_000 },
    });

    const replaced = await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20",
      payload: {
        firstMove: "Start",
        primaryTaskId: 3,
        secondaryTaskId: 2,
        expectedRevision: 1,
        occurredAt: 2_000,
      },
    });
    expect(replaced.json().dailyPlan).toMatchObject({ currentTaskId: 3, nextReviewAt: 3_602_000, revision: 2 });

    await setStepCompleted(3, true);
    let plan = (await getDailyPlan("2026-07-20"))!;
    expect(plan).toMatchObject({ state: "ACTIVE", currentTaskId: 2, revision: 3 });

    await setStepCompleted(2, true);
    plan = (await getDailyPlan("2026-07-20"))!;
    expect(plan).toMatchObject({ state: "ENOUGH", currentTaskId: null, nextReviewAt: null, revision: 4 });
    expect((await app.inject({ method: "DELETE", url: `/api/tasks/2?localDate=${TestDate}` })).statusCode).toBe(409);

    await app.inject({
      method: "PUT",
      url: "/api/daily-plans/2026-07-20/end",
      payload: { expectedRevision: 4, occurredAt: 5_000 },
    });
    expect((await app.inject({ method: "DELETE", url: `/api/tasks/2?localDate=${TestDate}` })).statusCode).toBe(200);
  });


  async function createTask(payload: Record<string, unknown>) {
    const steps = Array.isArray(payload.steps)
      ? payload.steps.map((step) => typeof step === "string" ? { title: step } : step)
      : payload.steps;
    return app.inject({
      method: "POST",
      url: "/api/tasks",
      payload: { repeatMode: "ONCE", localDate: TestDate, ...payload, steps },
    });
  }

  async function getTasks(localDate = TestDate): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: `/api/tasks?localDate=${localDate}` });
    expect(response.statusCode).toBe(200);
    return response.json().tasks;
  }

  async function createStep(taskId: number, payload: Record<string, unknown>) {
    return app.inject({
      method: "POST",
      url: `/api/tasks/${taskId}/steps`,
      payload: { localDate: TestDate, ...payload },
    });
  }

  async function setStepCompleted(stepId: number, completed: boolean, localDate = TestDate) {
    return app.inject({
      method: "PUT",
      url: `/api/steps/${stepId}/completion`,
      payload: { completed, localDate },
    });
  }

  async function getDailyPlan(localDate: string): Promise<Record<string, unknown> | null> {
    const response = await app.inject({ method: "GET", url: `/api/daily-plans/${localDate}` });
    expect(response.statusCode).toBe(200);
    return response.json().dailyPlan;
  }

  async function getAuditLogs(): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: "/api/audit-logs" });
    expect(response.statusCode).toBe(200);
    return response.json().logs;
  }
});

function itemInput(patch: Record<string, unknown> = {}) {
  return {
    name: "Water",
    description: "",
    mode: "ACCUMULATE",
    valueType: "NUMBER",
    defaultValue: 1,
    minValue: null,
    maxValue: null,
    stepValue: 1,
    unit: "次",
    goalValue: null,
    goalPeriod: null,
    priority: 1,
    ...patch,
  };
}

function entryInput(patch: Record<string, unknown> = {}) {
  return {
    itemId: 1,
    occurredAt: 10,
    localDate: "2026-07-20",
    localMinute: 600,
    value: 1,
    ...patch,
  };
}
