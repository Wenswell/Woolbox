import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { localToday, shiftLocalDate } from "./lib/format";

const activeItem = {
  id: 1, name: "Water", description: "Daily hydration", mode: "ACCUMULATE", valueType: "NUMBER",
  defaultValue: 250, minValue: null, maxValue: null, stepValue: 250, unit: "ml", goalValue: 2000,
  goalPeriod: "DAY", priority: 1, archived: false, createdAt: 1, updatedAt: 1,
};
const measurementItem = {
  ...activeItem, id: 2, name: "Temperature", description: "Morning reading", mode: "MEASUREMENT",
  defaultValue: null, minValue: 35, maxValue: 42, stepValue: 0.1, unit: "°C", goalValue: null, goalPeriod: null,
};
const archivedItem = { ...activeItem, id: 3, name: "Reading", unit: "pages", archived: true };
const task = {
  id: 10, title: "Prepare launch", repeatMode: "ONCE", completedAt: null, createdAt: 1, updatedAt: 1,
  steps: [
    { id: 11, taskId: 10, title: "Pack bag", position: 0, completedAt: null, createdAt: 1, updatedAt: 1 },
    { id: 12, taskId: 10, title: "Check route", position: 1, completedAt: null, createdAt: 1, updatedAt: 1 },
  ],
};
const secondaryTask = {
  ...task,
  id: 20,
  title: "Write notes",
  steps: [{ id: 21, taskId: 20, title: "Draft outline", position: 0, completedAt: null, createdAt: 1, updatedAt: 1 }],
};
const activePlan = {
  id: 40,
  localDate: localToday(),
  firstMove: "Open the project",
  firstMoveCompletedAt: 1,
  primaryTaskId: 10,
  secondaryTaskId: 20,
  currentTaskId: 10,
  state: "ACTIVE",
  completedRounds: 1,
  nextReviewAt: 1,
  revision: 2,
  endedAt: null,
  createdAt: 1,
  updatedAt: 1,
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
  let dailyPlan: typeof activePlan | null;

  beforeEach(() => {
    dailyPlan = null;
    window.history.replaceState(null, "", window.location.pathname);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (method !== "GET") {
        if (url === "api/items") return response({ item: activeItem });
        if (url.includes("completion")) return response({ step: { ...task.steps[0], completedAt: Date.now() } });
        if (url.includes("steps/order")) return response({ task });
        if (url.includes("daily-plans")) return response({ dailyPlan: activePlan });
        if (url === "api/entries") return response({ entry });
        return response({ ok: true, task, step: task.steps[0], item: activeItem });
      }
      if (url.startsWith("api/tasks?localDate=")) return response({ tasks: [task, secondaryTask] });
      if (url.startsWith("api/daily-plans/")) return response({ dailyPlan: url.endsWith(localToday()) ? dailyPlan : null });
      if (url.includes("audit-logs")) return response({ logs: [auditLog], nextBeforeId: null });
      if (url.includes("items?archived=true")) return response({ items: [archivedItem] });
      if (url.includes("items?archived=false")) return response({ items: [activeItem, measurementItem] });
      if (url.includes("entries?")) return response({ entries: [entry] });
      throw new Error(`Unhandled request: ${method} ${url}`);
    }));
  });

  it("loads the seven management areas and overview data", async () => {
    render(<App />);
    expect(await screen.findByText("Prepare launch")).toBeTruthy();
    for (const label of ["Overview", "Today", "Tasks", "Accumulate", "Measurement", "Entries", "Activity"]) {
      expect(screen.getByRole("button", { name: label })).toBeTruthy();
    }
    expect(screen.getByText("Recent entries")).toBeTruthy();
    expect(screen.getByText("Recent activity")).toBeTruthy();
  });

  it("manages subtask completion and order through existing task APIs", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Tasks" }));
    await user.click(screen.getByRole("button", { name: "Edit Prepare launch" }));

    const dialog = await screen.findByRole("dialog", { name: "Edit task" });
    await user.click(within(dialog).getByLabelText("Complete Pack bag"));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/steps/11/completion", expect.objectContaining({ method: "PUT", body: JSON.stringify({ completed: true, localDate: localToday() }) })));

    await user.click(within(dialog).getByRole("button", { name: "Move Pack bag down" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/tasks/10/steps/order", expect.objectContaining({ method: "PUT", body: JSON.stringify({ ids: [12, 11], localDate: localToday() }) })));
  });

  it("creates Today with an explicit 1 + 1 selection", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    await user.type(screen.getByLabelText("First move"), "Open notes");
    await user.selectOptions(screen.getByLabelText("Primary task"), "10");
    await user.click(screen.getByRole("button", { name: "Save today" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/daily-plans", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ localDate: localToday(), firstMove: "Open notes", primaryTaskId: 10, secondaryTaskId: null }),
    })));
  });

  it("manages due Today work and hourly review", async () => {
    dailyPlan = activePlan;
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByText("Review due")).toBeTruthy();

    await user.click(screen.getByRole("checkbox", { name: "Pack bag" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/steps/11/completion", expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({ completed: true, localDate: localToday() }),
    })));
    await user.click(screen.getByRole("button", { name: "Complete review" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/review-now`, expect.objectContaining({
      method: "PUT",
      body: expect.stringContaining('"currentTaskId":10'),
    })));
  });

  it("switches Today dates without changing the inventory date", async () => {
    const user = userEvent.setup();
    const selectedDate = shiftLocalDate(localToday(), -1);
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    fireEvent.change(screen.getByLabelText("Today plan date"), { target: { value: selectedDate } });

    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/tasks?localDate=${selectedDate}`, expect.any(Object)));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${selectedDate}`, expect.any(Object)));
    await user.click(screen.getByRole("button", { name: "Tasks" }));
    expect(fetch).toHaveBeenCalledWith(`api/tasks?localDate=${localToday()}`, expect.any(Object));
  });

  it("provides explicit switch, early review, and reset commands", async () => {
    dailyPlan = activePlan;
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByRole("checkbox", { name: "Draft outline" })).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "Switch now to Write notes" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/switch`, expect.objectContaining({
      method: "PUT",
      body: expect.stringContaining('"currentTaskId":20'),
    })));
    await user.click(screen.getByRole("button", { name: "Reset" }));
    await user.click(within(await screen.findByRole("dialog", { name: "Reset today" })).getByRole("button", { name: "Reset to Ready" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-plans/${localToday()}/reset`, expect.objectContaining({ method: "PUT" })));
  });

  it("creates a task directly while planning Today", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    await user.click(screen.getByRole("button", { name: "New task" }));
    const dialog = await screen.findByRole("dialog", { name: "New task" });
    await user.type(within(dialog).getByLabelText("Title"), "Plan tomorrow");
    await user.type(within(dialog).getByLabelText("Subtask 1"), "Write first step");
    await user.click(within(dialog).getByRole("button", { name: "Create task" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/tasks", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ title: "Plan tomorrow", repeatMode: "ONCE", localDate: localToday(), steps: [{ title: "Write first step" }] }),
    })));
  });

  it("creates a reusable daily task", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Tasks" }));
    await user.click(screen.getByRole("button", { name: "New task" }));
    const dialog = await screen.findByRole("dialog", { name: "New task" });
    await user.type(within(dialog).getByLabelText("Title"), "Daily review");
    await user.click(within(dialog).getByRole("button", { name: "Daily" }));
    await user.type(within(dialog).getByLabelText("Subtask 1"), "Open notes");
    await user.click(within(dialog).getByRole("button", { name: "Create task" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/tasks", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ title: "Daily review", repeatMode: "DAILY", localDate: localToday(), steps: [{ title: "Open notes" }] }),
    })));
  });

  it("uses a dedicated accumulate form without a mode switch", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Accumulate" }));
    await user.click(screen.getByRole("button", { name: "New accumulate" }));

    const dialog = await screen.findByRole("dialog", { name: "New accumulate" });
    expect(within(dialog).queryByLabelText("Mode")).toBeNull();
    expect(within(dialog).getByLabelText("Goal")).toBeTruthy();
    await user.type(within(dialog).getByLabelText("Name"), "Walk");
    await user.click(within(dialog).getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/items", expect.objectContaining({
      method: "POST",
      body: expect.stringContaining('"mode":"ACCUMULATE"'),
    })));
  });

  it("creates and batch deletes entries while preserving filters", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Entries" }));
    await screen.findByText("250 ml");

    await user.selectOptions(screen.getByLabelText("Mode"), "ACCUMULATE");
    await user.selectOptions(screen.getByLabelText("Item"), "1");
    await user.click(screen.getByRole("button", { name: "Add entry" }));
    const form = await screen.findByRole("dialog", { name: "Add entry" });
    fireEvent.change(within(form).getByLabelText("When"), { target: { value: "2026-08-07T10:10" } });
    await user.type(within(form).getByLabelText("Value"), "250");
    await user.click(within(form).getByRole("button", { name: "Add entry" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/entries", expect.objectContaining({ method: "POST" })));

    await user.click(screen.getByLabelText("Select Water"));
    await user.click(screen.getByRole("button", { name: "Delete selected" }));
    const confirm = await screen.findByRole("dialog", { name: "Delete entries" });
    await user.click(within(confirm).getByRole("button", { name: "Delete entries" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/entries/batch-delete", expect.objectContaining({ method: "POST", body: JSON.stringify({ ids: [20] }) })));
  });

  it("shows the expanded audit trail", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Activity" }));
    expect(await screen.findByText("Created entry")).toBeTruthy();
    expect(screen.getByText("Entry: Water")).toBeTruthy();
    expect(screen.getByText("Water; 2026-08-07 10:10; 250")).toBeTruthy();
  });
});

function response(value: unknown) {
  return Promise.resolve({ ok: true, json: async () => value } as Response);
}
