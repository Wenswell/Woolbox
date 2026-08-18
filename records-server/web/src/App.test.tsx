import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { localToday, shiftLocalDate } from "./lib/format";
import type { DailyPlan } from "./types";

const activeItem = {
  id: 1, name: "Water", description: "Daily hydration", mode: "ACCUMULATE", valueType: "NUMBER",
  defaultValue: 250, minValue: null, maxValue: null, stepValue: 250, unit: "ml", goalValue: 2000,
  goalPeriod: "DAY", priority: 1, archived: false, createdAt: 1, updatedAt: 1,
};
const measurementItem = {
  ...activeItem, id: 2, name: "Temperature", description: "Morning reading", mode: "MEASUREMENT",
  defaultValue: null, minValue: 35, maxValue: 42, stepValue: 0.1, unit: "C", goalValue: null, goalPeriod: null,
};
const archivedItem = { ...activeItem, id: 3, name: "Reading", unit: "pages", archived: true };
const template = {
  id: 10, title: "Prepare launch", cadence: "DAILY", archived: false, createdAt: 1, updatedAt: 1,
  steps: [
    { id: 11, templateId: 10, title: "Pack bag", position: 0, createdAt: 1, updatedAt: 1 },
    { id: 12, templateId: 10, title: "Check route", position: 1, createdAt: 1, updatedAt: 1 },
  ],
};
const secondaryTemplate = {
  id: 20, title: "Write notes", cadence: "MANUAL", archived: false, createdAt: 2, updatedAt: 2,
  steps: [{ id: 21, templateId: 20, title: "Draft outline", position: 0, createdAt: 2, updatedAt: 2 }],
};
const commitments = [
  {
    id: 101, planId: 40, templateId: 10, role: "PRIMARY", position: 0, title: "Prepare launch", cadence: "DAILY", completedAt: null, createdAt: 1, updatedAt: 1,
    steps: [
      { id: 111, commitmentId: 101, title: "Pack bag", position: 0, completedAt: null, createdAt: 1, updatedAt: 1 },
      { id: 112, commitmentId: 101, title: "Check route", position: 1, completedAt: null, createdAt: 1, updatedAt: 1 },
    ],
  },
  {
    id: 102, planId: 40, templateId: 20, role: "SECONDARY", position: 1, title: "Write notes", cadence: "MANUAL", completedAt: null, createdAt: 1, updatedAt: 1,
    steps: [{ id: 121, commitmentId: 102, title: "Draft outline", position: 0, completedAt: null, createdAt: 1, updatedAt: 1 }],
  },
] as const;
const activePlan: DailyPlan = {
  id: 40,
  localDate: localToday(),
  firstMove: "Open the project",
  firstMoveCompletedAt: 1,
  currentCommitmentId: 101,
  state: "ACTIVE",
  pauseReason: null,
  completedRounds: 1,
  nextReviewAt: 1,
  revision: 2,
  endedAt: null,
  createdAt: 1,
  updatedAt: 1,
  commitments: commitments.map((commitment) => ({ ...commitment, steps: commitment.steps.map((step) => ({ ...step })) })),
};
const entry = {
  id: 20, itemId: 1, occurredAt: 1_786_073_400_000, localDate: "2026-08-07", localMinute: 610,
  value: 250, createdAt: 1_786_073_400_000, itemName: "Water", itemMode: "ACCUMULATE", itemUnit: "ml", itemValueType: "NUMBER",
};
const auditLog = {
  id: 30, action: "ENTRY_CREATED", entityType: "ENTRY", entityId: 20, before: null,
  after: { id: 20, itemId: 1, itemName: "Water", occurredAt: entry.occurredAt, localDate: entry.localDate, localMinute: entry.localMinute, value: 250 },
  createdAt: entry.occurredAt,
};

describe("Records administration", () => {
  let dailyPlan: DailyPlan | null;

  beforeEach(() => {
    dailyPlan = null;
    window.history.replaceState(null, "", window.location.pathname);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (method !== "GET") {
        if (url === "api/items") return response({ item: activeItem });
        if (url === "api/templates") return response({ template: { ...template, id: 30, title: "Daily review" } });
        if (url.includes("commitment-steps")) return response({ dailyPlan: activePlan });
        if (url.includes("daily-plans")) return response({ dailyPlan: activePlan });
        if (url === "api/entries") return response({ entry });
        return response({ ok: true });
      }
      if (url === "api/templates?archived=false") return response({ templates: [template, secondaryTemplate] });
      if (url === "api/templates?archived=true") return response({ templates: [] });
      if (url.startsWith("api/daily-plans/")) return response({ dailyPlan: url.endsWith(localToday()) ? dailyPlan : null });
      if (url.includes("audit-logs")) return response({ logs: [auditLog], nextBeforeId: null });
      if (url.includes("items?archived=true")) return response({ items: [archivedItem] });
      if (url.includes("items?archived=false")) return response({ items: [activeItem, measurementItem] });
      if (url.includes("entries?")) return response({ entries: [entry] });
      throw new Error(`Unhandled request: ${method} ${url}`);
    }));
  });

  it("loads the seven management areas with template inventory", async () => {
    render(<App />);
    expect(await screen.findByText("Prepare launch")).toBeTruthy();
    for (const label of ["Overview", "Today", "Templates", "Accumulate", "Measurement", "Entries", "Activity"]) {
      expect(screen.getByRole("button", { name: label })).toBeTruthy();
    }
    expect(screen.getByText("Daily templates")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Tasks" })).toBeNull();
  });

  it("creates a reusable daily template as one form", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Templates" }));
    await user.click(screen.getByRole("button", { name: "New template" }));
    const dialog = await screen.findByRole("dialog", { name: "New template" });
    await user.type(within(dialog).getByLabelText("Title"), "Daily review");
    await user.click(within(dialog).getByRole("button", { name: "Daily" }));
    await user.type(within(dialog).getByLabelText("Step 1"), "Open notes");
    await user.click(within(dialog).getByRole("button", { name: "Create template" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/templates", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ title: "Daily review", cadence: "DAILY", steps: [{ title: "Open notes" }] }),
    })));
  });

  it("creates Today from template snapshots", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    await user.type(screen.getByLabelText("First move"), "Open notes");
    await user.selectOptions(screen.getByLabelText("Primary template"), "10");
    await user.click(screen.getByRole("button", { name: "Save today" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/daily-plans", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ localDate: localToday(), firstMove: "Open notes", primaryTemplateId: 10, secondaryTemplateId: null }),
    })));
  });

  it("manages active commitment steps, reviews, and switching by instance ID", async () => {
    dailyPlan = activePlan;
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByText("Review due")).toBeTruthy();

    await user.click(screen.getByRole("checkbox", { name: "Pack bag" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/commitment-steps/111/completion", expect.objectContaining({
      method: "PUT",
      body: expect.stringContaining('"expectedRevision":2'),
    })));
    await user.click(screen.getByRole("button", { name: "Complete review" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/review-now`, expect.objectContaining({
      method: "PUT",
      body: expect.stringContaining('"currentCommitmentId":101'),
    })));
    await user.click(screen.getByRole("button", { name: "Switch to Write notes" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/switch`, expect.objectContaining({
      method: "PUT",
      body: expect.stringContaining('"currentCommitmentId":102'),
    })));
  });

  it("offers Add commitment only after all commitments complete", async () => {
    dailyPlan = {
      ...activePlan,
      state: "PAUSED",
      pauseReason: "COMMITMENTS_COMPLETED",
      completedRounds: 0,
      currentCommitmentId: null,
      nextReviewAt: null,
      commitments: activePlan.commitments.map((commitment) => ({
        ...commitment,
        completedAt: 2,
        steps: commitment.steps.map((step) => ({ ...step, completedAt: 2 })),
      })),
    };
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));

    expect(await screen.findByText("Today's commitments completed")).toBeTruthy();
    expect(screen.getByText("Completed before the first review")).toBeTruthy();
    expect(screen.queryByText("0 rounds completed")).toBeNull();
    expect(screen.queryByText(/Continue one more round/)).toBeNull();
    await user.click(screen.getByRole("button", { name: "Add commitment" }));
    const dialog = await screen.findByRole("dialog", { name: "Add commitment" });
    await user.selectOptions(within(dialog).getByLabelText("Commitment template"), "10");
    await user.click(within(dialog).getByRole("button", { name: "Add commitment" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/commitments`, expect.objectContaining({
      method: "POST",
      body: expect.stringContaining('"templateId":10'),
    })));
  });

  it("offers Continue one more round only after reaching the target with open work", async () => {
    dailyPlan = {
      ...activePlan,
      state: "PAUSED",
      pauseReason: "TARGET_REACHED",
      completedRounds: 4,
      currentCommitmentId: null,
      nextReviewAt: null,
    };
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));

    expect((await screen.findAllByText("Daily target reached")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Add commitment" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Continue one more round: Prepare launch" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/continue`, expect.objectContaining({
      method: "PUT",
      body: expect.stringContaining('"currentCommitmentId":101'),
    })));
  });

  it("switches Today dates without date-scoping templates", async () => {
    const user = userEvent.setup();
    const selectedDate = shiftLocalDate(localToday(), -1);
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    fireEvent.change(screen.getByLabelText("Today plan date"), { target: { value: selectedDate } });

    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${selectedDate}`, expect.any(Object)));
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining("templates?localDate="), expect.any(Object));
  });

  it("creates entries and shows their audit trail", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Entries" }));
    await user.click(screen.getByRole("button", { name: "Add entry" }));
    const form = await screen.findByRole("dialog", { name: "Add entry" });
    fireEvent.change(within(form).getByLabelText("When"), { target: { value: "2026-08-07T10:10" } });
    await user.type(within(form).getByLabelText("Value"), "250");
    await user.click(within(form).getByRole("button", { name: "Add entry" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/entries", expect.objectContaining({ method: "POST" })));

    await user.click(screen.getByRole("button", { name: "Activity" }));
    expect(await screen.findByText("Created entry")).toBeTruthy();
    expect(screen.getByText("Entry: Water")).toBeTruthy();
  });
});

function response(value: unknown) {
  return Promise.resolve({ ok: true, json: async () => value } as Response);
}
