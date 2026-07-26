import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import { buildApp } from "../src/app.js";

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
      windows: [{ id: 1, itemId: 1, startMinute: 480, endMinute: 540 }],
    });

    const updated = await app.inject({
      method: "PUT",
      url: "/api/items/1",
      payload: itemInput({ name: "Hydration", windows: [{ startMinute: 600, endMinute: 660 }] }),
    });
    expect(updated.statusCode).toBe(200);
    expect(updated.json().item).toMatchObject({ name: "Hydration", windows: [{ startMinute: 600, endMinute: 660 }] });

    expect((await getAuditLogs()).map((log) => log.action)).toEqual(["ITEM_UPDATED", "ITEM_CREATED"]);
    expect((await getAuditLogs())[0]).toMatchObject({
      entityType: "ITEM",
      entityId: 1,
      before: { name: "Water", windows: [{ startMinute: 480, endMinute: 540 }] },
      after: { name: "Hydration", windows: [{ startMinute: 600, endMinute: 660 }] },
    });

    const unchanged = await app.inject({
      method: "PUT",
      url: "/api/items/1",
      payload: itemInput({ name: "Hydration", windows: [{ startMinute: 600, endMinute: 660 }] }),
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
    ["invalid start minute", { windows: [{ startMinute: -1, endMinute: 60 }] }],
    ["invalid end minute", { windows: [{ startMinute: 60, endMinute: 1440 }] }],
    ["equal window bounds", { windows: [{ startMinute: 60, endMinute: 60 }] }],
    ["duplicate windows", { windows: [{ startMinute: 60, endMinute: 120 }, { startMinute: 60, endMinute: 120 }] }],
    ["measurement duration", { mode: "MEASUREMENT", valueType: "DURATION", unit: "分", windows: [] }],
    ["measurement goal", { mode: "MEASUREMENT", goalValue: 2, goalPeriod: "DAY", windows: [] }],
    ["measurement windows", { mode: "MEASUREMENT" }],
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
        windows: [{ startMinute: 100, endMinute: 200 }],
      }),
    });
    expect(response.statusCode).toBe(400);
    expect((await getItems(false))[0]).toMatchObject({
      name: "Water",
      valueType: "NUMBER",
      unit: "ml",
      windows: [{ startMinute: 480, endMinute: 540 }],
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
      windows: [],
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
      windows: [],
    }));
    expect(created.statusCode).toBe(201);
    expect(created.json().item).toMatchObject({
      mode: "MEASUREMENT",
      valueType: "NUMBER",
      defaultValue: null,
      minValue: -10,
      maxValue: 10,
      goalValue: null,
      windows: [],
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
      windows: [],
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
      windows: [],
    }));
    await createItem(itemInput({
      name: "Weight",
      mode: "MEASUREMENT",
      defaultValue: null,
      minValue: 0,
      maxValue: 300,
      unit: "kg",
      windows: [],
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
      url: "/api/overview?startDate=2026-07-19&endDate=2026-07-20",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      items: [
        { id: 2, name: "Temperature", archived: false },
        { id: 1, name: "Water", archived: false },
        { id: 3, name: "Weight", archived: false },
      ],
      tasks: [{ id: 1, title: "Pack", horizon: "TODAY", steps: [{ title: "Bag" }] }],
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
      url: "/api/overview?startDate=2026-06-21&endDate=2026-07-20",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json().accumulateEntries).toMatchObject([
      { localDate: "2026-07-20", localMinute: 540 },
      { localDate: "2026-07-05", localMinute: 540 },
      { localDate: "2026-06-21", localMinute: 540 },
    ]);

    const narrower = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2026-07-01&endDate=2026-07-20",
    });
    expect(narrower.json().accumulateEntries).toHaveLength(2);
  });

  it("creates tasks with subtasks atomically and derives completion from them", async () => {
    const created = await createTask({ title: "  Prepare trip  ", steps: [" Book train ", "Pack bag"] });
    expect(created.statusCode).toBe(201);
    expect(created.json().task).toMatchObject({
      id: 1,
      title: "Prepare trip",
      horizon: "TODAY",
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
    const deleted = await app.inject({ method: "DELETE", url: "/api/steps/1" });
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

    expect((await app.inject({ method: "DELETE", url: "/api/steps/2" })).statusCode).toBe(200);
    expect((await app.inject({ method: "DELETE", url: "/api/steps/3" })).statusCode).toBe(400);

    const renamed = await app.inject({ method: "PUT", url: "/api/tasks/1", payload: { title: "Prepare journey", horizon: "LATER" } });
    expect(renamed.statusCode).toBe(200);
    expect(renamed.json().task.title).toBe("Prepare journey");
    expect(renamed.json().task.horizon).toBe("LATER");
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
      payload: { ids: [ids[2], ids[0], ids[1]] },
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
      payload: { ids: [ids[0], ids[1]] },
    });
    expect(incomplete.statusCode).toBe(400);

    const foreign = await app.inject({
      method: "PUT",
      url: `/api/tasks/${task.id}/steps/order`,
      payload: { ids: [ids[0], ids[1], 999999] },
    });
    expect(foreign.statusCode).toBe(400);
  });

  it("stores subtask suggested windows and audits their changes", async () => {
    const created = await createTask({
      title: "Morning routine",
      steps: [{ title: "Stretch", windows: [{ startMinute: 480, endMinute: 540 }] }, "Shower"],
    });
    expect(created.statusCode).toBe(201);
    expect(created.json().task.steps).toMatchObject([
      { title: "Stretch", windows: [{ id: 1, stepId: 1, startMinute: 480, endMinute: 540 }] },
      { title: "Shower", windows: [] },
    ]);
    expect((await getTasks())[0]).toMatchObject({
      steps: [{ title: "Stretch", windows: [{ startMinute: 480, endMinute: 540 }] }, { windows: [] }],
    });

    const updated = await app.inject({
      method: "PUT",
      url: "/api/steps/1",
      payload: { title: "Stretch", windows: [{ startMinute: 420, endMinute: 480 }] },
    });
    expect(updated.statusCode).toBe(200);
    expect(updated.json().step.windows).toMatchObject([{ startMinute: 420, endMinute: 480 }]);

    const logs = (await app.inject({ method: "GET", url: "/api/audit-logs" })).json().logs as Array<{
      action: string;
      before: { windows?: unknown } | null;
      after: { windows?: unknown } | null;
    }>;
    const stepUpdated = logs.find((log) => log.action === "STEP_UPDATED")!;
    expect(stepUpdated.before?.windows).toEqual([{ startMinute: 480, endMinute: 540 }]);
    expect(stepUpdated.after?.windows).toEqual([{ startMinute: 420, endMinute: 480 }]);
    const stepCreated = logs.filter((log) => log.action === "STEP_CREATED");
    expect(stepCreated.at(-1)?.after?.windows).toEqual([{ startMinute: 480, endMinute: 540 }]);
  });

  it("adds subtask windows through the step endpoint and cascades deletes", async () => {
    const created = await createTask({ title: "Pack", steps: ["Bag"] });
    expect(created.statusCode).toBe(201);
    expect(created.json().task.steps[0].windows).toEqual([]);
    const taskId = created.json().task.id as number;

    const added = await createStep(taskId, {
      title: "Evening check",
      windows: [{ startMinute: 1200, endMinute: 1320 }],
    });
    expect(added.statusCode).toBe(201);
    expect(added.json().step.windows).toMatchObject([{ startMinute: 1200, endMinute: 1320 }]);
    const stepId = added.json().step.id as number;

    expect((await app.inject({ method: "DELETE", url: `/api/steps/${stepId}` })).statusCode).toBe(200);
    const remaining = (await getTasks())[0] as { steps: Array<{ id: number }> };
    expect(remaining.steps.map((step) => step.id)).toEqual([1]);

    expect((await app.inject({ method: "DELETE", url: `/api/tasks/${taskId}` })).statusCode).toBe(200);
    expect(await getTasks()).toEqual([]);
  });

  it("rejects subtask windows that repeat, are invalid, or cover half the day", async () => {
    const duplicate = await createTask({
      title: "Dup",
      steps: [
        {
          title: "A",
          windows: [
            { startMinute: 480, endMinute: 540 },
            { startMinute: 480, endMinute: 540 },
          ],
        },
      ],
    });
    expect(duplicate.statusCode).toBe(400);

    const equal = await createTask({
      title: "Equal",
      steps: [{ title: "A", windows: [{ startMinute: 480, endMinute: 480 }] }],
    });
    expect(equal.statusCode).toBe(400);

    const wide = await createTask({
      title: "Wide",
      steps: [{ title: "A", windows: [{ startMinute: 0, endMinute: 1439 }] }],
    });
    expect(wide.statusCode).toBe(400);
    expect(wide.json().error.message).toBe("Suggested times must cover less than 12 hours.");

    expect(await getTasks()).toEqual([]);
  });

  it("caps record item suggested time coverage", async () => {
    const wide = await createItem(
      itemInput({ name: "Wide", windows: [{ startMinute: 0, endMinute: 720 }] }),
    );
    expect(wide.statusCode).toBe(400);

    const overlapping = await createItem(
      itemInput({
        name: "Overlapping",
        windows: [
          { startMinute: 480, endMinute: 900 },
          { startMinute: 600, endMinute: 960 },
        ],
      }),
    );
    // Union is 480..960 = 8h, under the cap even though the sum is over it.
    expect(overlapping.statusCode).toBe(201);

    const overnight = await createItem(
      itemInput({ name: "Overnight", windows: [{ startMinute: 1380, endMinute: 300 }] }),
    );
    // 23:00 -> 05:00 wraps midnight and counts as 6h.
    expect(overnight.statusCode).toBe(201);
  });


  async function createTask(payload: Record<string, unknown>) {
    return app.inject({
      method: "POST",
      url: "/api/tasks",
      payload: { horizon: "TODAY", ...payload },
    });
  }

  async function getTasks(): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: "/api/tasks" });
    expect(response.statusCode).toBe(200);
    return response.json().tasks;
  }

  async function createStep(taskId: number, payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: `/api/tasks/${taskId}/steps`, payload });
  }

  async function setStepCompleted(stepId: number, completed: boolean) {
    return app.inject({ method: "PUT", url: `/api/steps/${stepId}/completion`, payload: { completed } });
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
    windows: [{ startMinute: 480, endMinute: 540 }],
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
