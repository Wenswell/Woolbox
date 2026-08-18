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

  it("serves record item CRUD with validation and audit logs", async () => {
    const created = await createItem(itemInput({ name: "  Water  ", description: " drink ", unit: " ml " }));
    expect(created.statusCode).toBe(201);
    expect(created.json().item).toMatchObject({
      id: 1,
      name: "Water",
      description: "drink",
      mode: "ACCUMULATE",
      unit: "ml",
      archived: false,
    });

    const updated = await app.inject({
      method: "PUT",
      url: "/api/items/1",
      payload: itemInput({ name: "Hydration" }),
    });
    expect(updated.statusCode).toBe(200);
    expect(updated.json().item.name).toBe("Hydration");
    expect((await getAuditLogs()).map((log) => log.action)).toEqual(["ITEM_UPDATED", "ITEM_CREATED"]);

    expect((await createItem(itemInput({ name: "hydration" }))).statusCode).toBe(409);
    expect((await createItem(itemInput({ name: " " }))).statusCode).toBe(400);
    expect((await createItem(itemInput({ stepValue: 0 }))).statusCode).toBe(400);

    expect((await archiveItems([1], true)).statusCode).toBe(200);
    expect((await getItems(true))[0]).toMatchObject({ id: 1, archived: true });
    expect((await app.inject({ method: "DELETE", url: "/api/items/1" })).statusCode).toBe(200);
    expect(await getItems(true)).toEqual([]);
  });

  it("validates, filters, and deletes accumulate and measurement entries", async () => {
    await createItem(itemInput({ name: "Water", unit: "ml" }));
    await createItem(itemInput({
      name: "Temperature",
      mode: "MEASUREMENT",
      defaultValue: null,
      minValue: -10,
      maxValue: 10,
      stepValue: 0.5,
      unit: "C",
    }));
    expect((await createEntry(entryInput({ itemId: 1, localDate: "2026-07-19", value: 2 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 2, occurredAt: 20, value: -5.5 }))).statusCode).toBe(201);
    expect((await createEntry(entryInput({ itemId: 2, value: -5.25 }))).statusCode).toBe(400);
    expect((await createEntry(entryInput({ itemId: 1, value: 0 }))).statusCode).toBe(400);

    const accumulate = await app.inject({
      method: "GET",
      url: "/api/entries?startDate=2026-07-19&endDate=2026-07-20&mode=ACCUMULATE",
    });
    expect(accumulate.json().entries).toEqual([expect.objectContaining({ itemId: 1, value: 2 })]);

    const latest = await app.inject({ method: "GET", url: "/api/entries/latest" });
    expect(latest.json().entries).toEqual([expect.objectContaining({ itemId: 2, value: -5.5 })]);

    const missingBatch = await app.inject({
      method: "POST",
      url: "/api/entries/batch-delete",
      payload: { ids: [1, 3] },
    });
    expect(missingBatch.statusCode).toBe(404);
    expect(await getEntries()).toHaveLength(2);
    expect((await app.inject({ method: "DELETE", url: "/api/entries/1" })).statusCode).toBe(200);
  });

  it("returns active records, templates, and Today in one overview", async () => {
    await createItem(itemInput({ name: "Water" }));
    await createTemplate({ title: "Pack", cadence: "DAILY", steps: ["Bag"] });
    await createEntry(entryInput({ value: 2 }));

    const response = await app.inject({
      method: "GET",
      url: `/api/overview?startDate=${TestDate}&endDate=${TestDate}&planDate=${TestDate}`,
    });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      items: [{ name: "Water" }],
      templates: [{ title: "Pack", cadence: "DAILY", steps: [{ title: "Bag" }] }],
      dailyPlan: null,
      accumulateEntries: [{ itemId: 1, value: 2 }],
    });
    expect(response.json()).not.toHaveProperty("tasks");
  });

  it("manages reusable templates and keeps Today snapshots immutable", async () => {
    await createTemplate({ title: "Manual first", cadence: "MANUAL", steps: ["Old step"] });
    await createTemplate({ title: "Daily first", cadence: "DAILY", steps: ["Repeat"] });
    expect((await getTemplates()).map((template) => template.title)).toEqual(["Daily first", "Manual first"]);

    const plan = await createPlan({ primaryTemplateId: 1 });
    expect(plan.commitments).toMatchObject([{
      templateId: 1,
      role: "PRIMARY",
      title: "Manual first",
      completedAt: null,
      steps: [{ title: "Old step", completedAt: null }],
    }]);

    const updated = await app.inject({
      method: "PUT",
      url: "/api/templates/1",
      payload: templateInput({ title: "Changed", cadence: "DAILY", steps: ["New step", "Second"] }),
    });
    expect(updated.statusCode).toBe(200);
    expect(updated.json().template.steps.map((step: { title: string }) => step.title)).toEqual(["New step", "Second"]);
    expect(await getDailyPlan()).toMatchObject({
      commitments: [{ title: "Manual first", cadence: "MANUAL", steps: [{ title: "Old step" }] }],
    });

    expect((await app.inject({ method: "DELETE", url: "/api/templates/1" })).statusCode).toBe(400);
    expect((await archiveTemplates([1], true)).statusCode).toBe(200);
    expect((await app.inject({ method: "DELETE", url: "/api/templates/1" })).statusCode).toBe(200);
    expect(await getDailyPlan()).toMatchObject({
      commitments: [{ templateId: null, title: "Manual first", steps: [{ title: "Old step" }] }],
    });
  });

  it("pauses at four reviewed rounds and continues only an unfinished commitment", async () => {
    await createTemplate({ title: "Primary", steps: ["Build"] });
    await createTemplate({ title: "Secondary", steps: ["Write"] });
    let plan = await createPlan({ primaryTemplateId: 1, secondaryTemplateId: 2 });
    expect(plan).toMatchObject({ state: "READY", pauseReason: null, completedRounds: 0, revision: 0 });

    plan = (await transition("start", { expectedRevision: 0, occurredAt: 1_000 })).json().dailyPlan;
    expect(plan).toMatchObject({ state: "ACTIVE", currentCommitmentId: 1, nextReviewAt: 3_601_000, revision: 1 });

    const early = await transition("review", {
      expectedRevision: 1,
      occurredAt: 3_600_999,
      currentCommitmentId: 1,
    });
    expect(early.statusCode).toBe(409);

    for (let round = 1; round <= 4; round += 1) {
      const response = await transition("review-now", {
        expectedRevision: plan.revision,
        occurredAt: 2_000 + round,
        currentCommitmentId: 1,
      });
      expect(response.statusCode).toBe(200);
      plan = response.json().dailyPlan;
      expect(plan.completedRounds).toBe(round);
    }
    expect(plan).toMatchObject({
      state: "PAUSED",
      pauseReason: "TARGET_REACHED",
      currentCommitmentId: null,
      nextReviewAt: null,
      revision: 5,
    });

    const blockedAdd = await app.inject({
      method: "POST",
      url: `/api/daily-plans/${TestDate}/commitments`,
      payload: { expectedRevision: 5, occurredAt: 8_000, templateId: 2 },
    });
    expect(blockedAdd.statusCode).toBe(409);

    plan = (await transition("continue", {
      expectedRevision: 5,
      occurredAt: 9_000,
      currentCommitmentId: 2,
    })).json().dailyPlan;
    expect(plan).toMatchObject({
      state: "ACTIVE",
      pauseReason: null,
      completedRounds: 4,
      currentCommitmentId: 2,
      nextReviewAt: 3_609_000,
      revision: 6,
    });
    expect((await app.inject({
      method: "PUT",
      url: `/api/daily-plans/${TestDate}/extra-round`,
      payload: { expectedRevision: 6, occurredAt: 10_000, currentCommitmentId: 1 },
    })).statusCode).toBe(404);
  });

  it("adds another instance after commitments finish and reopens paused work", async () => {
    await createTemplate({ title: "Ship", cadence: "DAILY", steps: ["Publish"] });
    let plan = await createPlan({ primaryTemplateId: 1 });
    plan = (await transition("start", { expectedRevision: 0, occurredAt: 1_000 })).json().dailyPlan;

    const firstStepId = plan.commitments[0].steps[0].id as number;
    plan = (await setCommitmentStep(firstStepId, true, plan.revision, 2_000)).json().dailyPlan;
    expect(plan).toMatchObject({
      state: "PAUSED",
      pauseReason: "COMMITMENTS_COMPLETED",
      completedRounds: 0,
      currentCommitmentId: null,
      revision: 2,
    });

    const added = await app.inject({
      method: "POST",
      url: `/api/daily-plans/${TestDate}/commitments`,
      payload: { expectedRevision: 2, occurredAt: 3_000, templateId: 1 },
    });
    expect(added.statusCode).toBe(201);
    plan = added.json().dailyPlan;
    expect(plan).toMatchObject({ state: "ACTIVE", currentCommitmentId: 2, revision: 3 });
    expect(plan.commitments).toHaveLength(2);
    expect(plan.commitments.map((commitment: { templateId: number }) => commitment.templateId)).toEqual([1, 1]);

    const secondStepId = plan.commitments[1].steps[0].id as number;
    plan = (await setCommitmentStep(secondStepId, true, 3, 4_000)).json().dailyPlan;
    expect(plan).toMatchObject({ state: "PAUSED", pauseReason: "COMMITMENTS_COMPLETED", revision: 4 });

    plan = (await setCommitmentStep(firstStepId, false, 4, 5_000)).json().dailyPlan;
    expect(plan).toMatchObject({
      state: "ACTIVE",
      pauseReason: null,
      currentCommitmentId: 1,
      nextReviewAt: 3_605_000,
      revision: 5,
    });
  });

  it("returns a reopened pre-start commitment to READY and retains completion on reset", async () => {
    await createTemplate({ title: "Prepare", steps: ["Open"] });
    let plan = await createPlan({ primaryTemplateId: 1 });
    const stepId = plan.commitments[0].steps[0].id as number;

    plan = (await setCommitmentStep(stepId, true, 0, 1_000)).json().dailyPlan;
    expect(plan).toMatchObject({ state: "PAUSED", pauseReason: "COMMITMENTS_COMPLETED", revision: 1 });
    plan = (await setCommitmentStep(stepId, false, 1, 2_000)).json().dailyPlan;
    expect(plan).toMatchObject({ state: "READY", pauseReason: null, currentCommitmentId: null, revision: 2 });

    plan = (await transition("start", { expectedRevision: 2, occurredAt: 3_000 })).json().dailyPlan;
    plan = (await setCommitmentStep(stepId, true, plan.revision, 4_000)).json().dailyPlan;
    const reset = await transition("reset", { expectedRevision: plan.revision, occurredAt: 5_000 });
    expect(reset.json().dailyPlan).toMatchObject({
      state: "READY",
      completedRounds: 0,
      firstMoveCompletedAt: null,
      commitments: [{ completedAt: 4_000, steps: [{ completedAt: 4_000 }] }],
    });
    const restarted = await transition("start", {
      expectedRevision: reset.json().dailyPlan.revision,
      occurredAt: 6_000,
    });
    expect(restarted.json().dailyPlan).toMatchObject({
      state: "PAUSED",
      pauseReason: "COMMITMENTS_COMPLETED",
      completedRounds: 0,
    });
  });

  it("rejects invalid templates and stale plan commands without partial writes", async () => {
    expect((await createTemplate({ title: " ", steps: ["Step"] })).statusCode).toBe(400);
    expect((await createTemplate({ title: "Task", steps: [] })).statusCode).toBe(400);
    expect((await createTemplate({ title: "Task", steps: [" "] })).statusCode).toBe(400);
    expect(await getTemplates()).toEqual([]);

    await createTemplate({ title: "Task", steps: ["Step"] });
    await createPlan({ primaryTemplateId: 1 });
    expect((await transition("start", { expectedRevision: 1, occurredAt: 1_000 })).statusCode).toBe(409);
    expect(await getDailyPlan()).toMatchObject({ state: "READY", revision: 0 });
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

  async function createTemplate(payload: Record<string, unknown>) {
    return app.inject({ method: "POST", url: "/api/templates", payload: templateInput(payload) });
  }

  async function getTemplates(archived = false): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: `/api/templates?archived=${archived}` });
    expect(response.statusCode).toBe(200);
    return response.json().templates;
  }

  async function archiveTemplates(ids: number[], archived: boolean) {
    return app.inject({ method: "POST", url: "/api/templates/batch-archive", payload: { ids, archived } });
  }

  async function createPlan(patch: Record<string, unknown>) {
    const response = await app.inject({
      method: "POST",
      url: "/api/daily-plans",
      payload: {
        localDate: TestDate,
        firstMove: "Go to the library",
        secondaryTemplateId: null,
        ...patch,
      },
    });
    expect(response.statusCode).toBe(201);
    return response.json().dailyPlan;
  }

  async function getDailyPlan(localDate = TestDate) {
    const response = await app.inject({ method: "GET", url: `/api/daily-plans/${localDate}` });
    expect(response.statusCode).toBe(200);
    return response.json().dailyPlan;
  }

  async function transition(command: string, payload: Record<string, unknown>) {
    return app.inject({ method: "PUT", url: `/api/daily-plans/${TestDate}/${command}`, payload });
  }

  async function setCommitmentStep(stepId: number, completed: boolean, expectedRevision: number, occurredAt: number) {
    return app.inject({
      method: "PUT",
      url: `/api/commitment-steps/${stepId}/completion`,
      payload: { completed, expectedRevision, occurredAt },
    });
  }

  async function getAuditLogs(): Promise<Array<Record<string, unknown>>> {
    const response = await app.inject({ method: "GET", url: "/api/audit-logs" });
    expect(response.statusCode).toBe(200);
    return response.json().logs;
  }
});

function templateInput(patch: Record<string, unknown> = {}) {
  const input = {
    title: "Template",
    cadence: "MANUAL",
    steps: ["Step"],
    ...patch,
  };
  return {
    ...input,
    steps: Array.isArray(input.steps)
      ? input.steps.map((step) => typeof step === "string" ? { title: step } : step)
      : input.steps,
  };
}

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
    unit: "times",
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
    localDate: TestDate,
    localMinute: 600,
    value: 1,
    ...patch,
  };
}
