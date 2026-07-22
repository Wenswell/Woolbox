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

  it("creates, filters, updates, and deletes entries", async () => {
    await createItem(itemInput({ name: "Water", unit: "ml" }));
    await createItem(itemInput({ name: "Read", unit: "pages" }));
    await createEntry(entryInput({ itemId: 1, localDate: "2026-07-19", localMinute: 600, value: 2 }));
    await createEntry(entryInput({ itemId: 2, localDate: "2026-07-20", localMinute: 700, value: 3 }));

    const filtered = await app.inject({ method: "GET", url: "/api/entries?startDate=2026-07-19&endDate=2026-07-20&itemId=2" });
    expect(filtered.statusCode).toBe(200);
    expect(filtered.json().entries).toEqual([
      expect.objectContaining({ id: 2, itemId: 2, itemName: "Read", localDate: "2026-07-20" }),
    ]);

    const update = await app.inject({
      method: "PUT",
      url: "/api/entries/1",
      payload: entryUpdateInput({ occurredAt: 20, localDate: "2026-07-18", localMinute: 59, value: 4 }),
    });
    expect(update.statusCode).toBe(200);
    expect(update.json().entry).toMatchObject({ id: 1, itemId: 1, occurredAt: 20, localDate: "2026-07-18", localMinute: 59, value: 4 });

    const deleted = await app.inject({ method: "DELETE", url: "/api/entries/1" });
    expect(deleted.statusCode).toBe(200);
    expect(deleted.json().entry.id).toBe(1);
    expect((await getAuditLogs()).slice(0, 3)).toMatchObject([
      { action: "ENTRY_DELETED", before: { id: 1, itemName: "Water", value: 4 } },
      { action: "ENTRY_UPDATED", before: { id: 1, itemName: "Water", value: 2 }, after: { id: 1, itemName: "Water", value: 4 } },
      { action: "ENTRY_CREATED", after: { id: 2, itemName: "Read", value: 3 } },
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

  it("allows signed measurement values and rejects incompatible measurement fields", async () => {
    const created = await createItem(itemInput({
      name: "Temperature",
      mode: "MEASUREMENT",
      defaultValue: null,
      unit: "°C",
      windows: [],
    }));
    expect(created.statusCode).toBe(201);
    expect(created.json().item).toMatchObject({ mode: "MEASUREMENT", valueType: "NUMBER", goalValue: null, windows: [] });

    expect((await createEntry(entryInput({ itemId: 1, value: 0 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 1, value: -5.5 }))).statusCode).toBe(201);
    expect((await getEntries()).map((entry) => entry.value)).toEqual([-5.5, 0]);

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

  it("manages tasks and first-level steps atomically", async () => {
    const created = await createTask({ title: "  Prepare trip  " });
    expect(created.statusCode).toBe(201);
    expect(created.json().task).toMatchObject({ id: 1, title: "Prepare trip", completedAt: null, steps: [] });

    const directComplete = await setTaskCompleted(1, true);
    expect(directComplete.statusCode).toBe(200);
    expect(directComplete.json().task).toMatchObject({ completedAt: 1_700_000_000_000 });

    const firstStep = await createStep(1, { title: "Book train" });
    expect(firstStep.statusCode).toBe(201);
    expect(firstStep.json().step).toMatchObject({ id: 1, taskId: 1, title: "Book train", completedAt: null });
    expect((await getTasks())[0]).toMatchObject({ completedAt: null });

    await createStep(1, { title: "Pack bag" });
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
    const parentComplete = await setTaskCompleted(1, true);
    expect(parentComplete.json().task.steps).toEqual([
      expect.objectContaining({ id: 1, completedAt: 1_700_000_000_000 }),
      expect.objectContaining({ id: 2, completedAt: 1_700_000_000_000 }),
    ]);

    await setStepCompleted(1, false);
    const deleted = await app.inject({ method: "DELETE", url: "/api/steps/1" });
    expect(deleted.statusCode).toBe(200);
    expect((await getTasks())[0]).toMatchObject({
      completedAt: 1_700_000_000_000,
      steps: [expect.objectContaining({ id: 2, completedAt: 1_700_000_000_000 })],
    });

    const renamed = await app.inject({ method: "PUT", url: "/api/tasks/1", payload: { title: "Prepare journey" } });
    expect(renamed.statusCode).toBe(200);
    expect(renamed.json().task.title).toBe("Prepare journey");
    expect((await getAuditLogs()).map((log) => log.action)).toContain("TASK_COMPLETED");
    expect((await getAuditLogs()).map((log) => log.action)).toContain("STEP_REOPENED");
  });

  it("rejects empty task titles and does not write partial task data", async () => {
    const task = await createTask({ title: " " });
    expect(task.statusCode).toBe(400);
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

  async function createTask(payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/tasks", payload });
  }

  async function getTasks(): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: "/api/tasks" });
    expect(response.statusCode).toBe(200);
    return response.json().tasks;
  }

  async function createStep(taskId: number, payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: `/api/tasks/${taskId}/steps`, payload });
  }

  async function setTaskCompleted(taskId: number, completed: boolean) {
    return app.inject({ method: "PUT", url: `/api/tasks/${taskId}/completion`, payload: { completed } });
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

function entryUpdateInput(patch: Record<string, unknown> = {}) {
  return {
    occurredAt: 10,
    localDate: "2026-07-20",
    localMinute: 600,
    value: 1,
    ...patch,
  };
}
