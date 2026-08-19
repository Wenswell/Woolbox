import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { localToday, shiftLocalDate } from "./lib/format";
import type { DailyFocus, TaskTemplate, TodayState } from "./types";

const activeItem = {
  id: 1, name: "Water", description: "Daily hydration", mode: "ACCUMULATE", valueType: "NUMBER",
  defaultValue: 250, minValue: null, maxValue: null, stepValue: 250, unit: "ml", goalValue: 2000,
  goalPeriod: "DAY", priority: 1, archived: false, createdAt: 1, updatedAt: 1,
};
const measurementItem = { ...activeItem, id: 2, name: "Temperature", mode: "MEASUREMENT", defaultValue: null, minValue: 35, maxValue: 42, stepValue: 0.1, unit: "C", goalValue: null, goalPeriod: null };
const archivedItem = { ...activeItem, id: 3, name: "Reading", unit: "pages", archived: true };
const template: TaskTemplate = {
  id: 10, title: "Prepare launch", cadence: "DAILY", archived: false, createdAt: 1, updatedAt: 1,
  steps: [{ id: 11, templateId: 10, title: "Pack bag", position: 0, createdAt: 1, updatedAt: 1 }, { id: 12, templateId: 10, title: "Check route", position: 1, createdAt: 1, updatedAt: 1 }],
};
const secondaryTemplate: TaskTemplate = { id: 20, title: "Write notes", cadence: "MANUAL", archived: false, createdAt: 2, updatedAt: 2, steps: [] };
const tasks = [
  {
    id: 101, localDate: localToday(), templateId: 10, sourceTaskId: null, title: "Prepare launch", position: 0, completedAt: null, revision: 2, createdAt: 1, updatedAt: 1,
    steps: [{ id: 111, taskId: 101, title: "Pack bag", position: 0, completedAt: null, createdAt: 1, updatedAt: 1 }, { id: 112, taskId: 101, title: "Check route", position: 1, completedAt: null, createdAt: 1, updatedAt: 1 }],
  },
  { id: 102, localDate: localToday(), templateId: 20, sourceTaskId: null, title: "Write notes", position: 1, completedAt: null, revision: 0, createdAt: 1, updatedAt: 1, steps: [] },
  { id: 103, localDate: localToday(), templateId: null, sourceTaskId: null, title: "Laundry", position: 2, completedAt: null, revision: 0, createdAt: 1, updatedAt: 1, steps: [] },
];
const activeFocus: DailyFocus = {
  id: 40, localDate: localToday(), firstMove: "Open the project", firstMoveCompletedAt: 1,
  primaryTaskId: 101, secondaryTaskId: 102, currentTaskId: 101, state: "ACTIVE", pauseReason: null,
  completedRounds: 1, nextReviewAt: 1, revision: 2, endedAt: null, createdAt: 1, updatedAt: 1,
};
const entry = { id: 20, itemId: 1, occurredAt: 1_786_073_400_000, localDate: "2026-08-07", localMinute: 610, value: 250, createdAt: 1_786_073_400_000, itemName: "Water", itemMode: "ACCUMULATE", itemUnit: "ml", itemValueType: "NUMBER" };
const auditLog = { id: 30, action: "ENTRY_CREATED", entityType: "ENTRY", entityId: 20, before: null, after: { id: 20, itemId: 1, itemName: "Water", occurredAt: entry.occurredAt, localDate: entry.localDate, localMinute: entry.localMinute, value: 250 }, createdAt: entry.occurredAt };

describe("Records administration", () => {
  let todayState: TodayState;

  beforeEach(() => {
    todayState = { todayTasks: tasks.map((task) => ({ ...task, steps: task.steps.map((step) => ({ ...step })) })), dailySuggestions: [template], unfinishedTasks: [], dailyFocus: null };
    window.history.replaceState(null, "", window.location.pathname);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (method !== "GET") {
        if (url === "api/items") return response({ item: activeItem });
        if (url === "api/templates") return response({ template: { ...template, id: 30, title: "Daily review", steps: [] } });
        if (url.startsWith("api/today-task") || url.startsWith("api/today-tasks")) return response({ today: todayState });
        if (url.startsWith("api/daily-focus")) return response({ dailyFocus: activeFocus });
        if (url === "api/entries") return response({ entry });
        return response({ ok: true });
      }
      if (url === "api/templates?archived=false") return response({ templates: [template, secondaryTemplate] });
      if (url === "api/templates?archived=true") return response({ templates: [] });
      if (url.startsWith("api/goals?")) return response({ goals: [] });
      if (url.startsWith("api/today/")) return response({ today: url.endsWith(localToday()) ? todayState : { todayTasks: [], dailySuggestions: [template], unfinishedTasks: [], dailyFocus: null } });
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
    for (const label of ["Overview", "Today", "Templates", "Goals", "Accumulate", "Measurement", "Entries", "Activity"]) expect(screen.getByRole("button", { name: label })).toBeTruthy();
    expect(screen.getByText("Daily templates")).toBeTruthy();
  });

  it("creates a reusable template without requiring checklist steps", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Templates" }));
    await user.click(screen.getByRole("button", { name: "New template" }));
    const dialog = await screen.findByRole("dialog", { name: "New template" });
    await user.type(within(dialog).getByLabelText("Title"), "Daily review");
    await user.click(within(dialog).getByRole("button", { name: "Daily" }));
    await user.click(within(dialog).getByRole("button", { name: "Create template" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/templates", expect.objectContaining({ method: "POST", body: JSON.stringify({ title: "Daily review", cadence: "DAILY", steps: [] }) })));
  });

  it("adds a recommended template to Today only after confirmation", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByText("No Focus planned")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Add task" }));
    const dialog = await screen.findByRole("dialog", { name: "Add task" });
    await user.click(within(dialog).getByRole("button", { name: /Prepare launch/ }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/today-tasks/from-template", expect.objectContaining({ method: "POST", body: JSON.stringify({ localDate: localToday(), templateId: 10 }) })));
  });

  it("creates an ad hoc Today task with an optional checklist", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    await user.click(screen.getByRole("button", { name: "Add task" }));
    const dialog = await screen.findByRole("dialog", { name: "Add task" });
    await user.click(within(dialog).getByRole("button", { name: "New task" }));
    await user.type(within(dialog).getByLabelText("Title"), "Hang laundry");
    await user.click(within(dialog).getByRole("button", { name: "Add step" }));
    await user.type(within(dialog).getByLabelText("Task step 1"), "Take clothes out");
    await user.click(within(dialog).getByRole("button", { name: "Add task" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/today-tasks", expect.objectContaining({ method: "POST", body: JSON.stringify({ localDate: localToday(), title: "Hang laundry", steps: [{ title: "Take clothes out" }] }) })));
  });

  it("plans Focus from existing Today tasks", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    await user.click(await screen.findByRole("button", { name: "Plan Focus" }));
    const dialog = await screen.findByRole("dialog", { name: "Plan Focus" });
    await user.type(within(dialog).getByLabelText("First move"), "Open notes");
    await user.selectOptions(within(dialog).getByLabelText("Secondary Focus"), "102");
    await user.click(within(dialog).getByRole("button", { name: "Save Focus" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/daily-focus", expect.objectContaining({ method: "POST", body: JSON.stringify({ localDate: localToday(), firstMove: "Open notes", primaryTaskId: 101, secondaryTaskId: 102 }) })));
  });

  it("manages task progress and active Focus by Today task ID", async () => {
    todayState.dailyFocus = activeFocus;
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByText("Review due")).toBeTruthy();

    await user.click(screen.getByRole("checkbox", { name: "Pack bag" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/today-task-steps/111/completion", expect.objectContaining({ method: "PUT", body: expect.stringContaining('"expectedRevision":2') })));
    await user.click(screen.getByRole("button", { name: "Complete review" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-focus/${localToday()}/review-now`, expect.objectContaining({ method: "PUT", body: expect.stringContaining('"currentTaskId":101') })));
    await user.click(screen.getByRole("button", { name: "Switch to Write notes" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-focus/${localToday()}/switch`, expect.objectContaining({ method: "PUT", body: expect.stringContaining('"currentTaskId":102') })));
  });

  it("chooses new Focus after selected tasks complete while ordinary tasks stay available", async () => {
    todayState.todayTasks = todayState.todayTasks.map((task) => task.id === 103 ? task : { ...task, completedAt: 2, steps: task.steps.map((step) => ({ ...step, completedAt: 2 })) });
    todayState.dailyFocus = { ...activeFocus, state: "PAUSED", pauseReason: "FOCUS_CLEARED", completedRounds: 0, currentTaskId: null, nextReviewAt: null, revision: 3 };
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByText("Selected Focus completed")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Add task" })).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Choose new Focus" }));
    const dialog = await screen.findByRole("dialog", { name: "Choose new Focus" });
    await user.click(within(dialog).getByRole("button", { name: "Save Focus" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-focus/${localToday()}/refocus`, expect.objectContaining({ method: "PUT", body: expect.stringContaining('"primaryTaskId":103') })));
  });

  it("continues Focus after four rounds without requiring ordinary task completion", async () => {
    todayState.dailyFocus = { ...activeFocus, state: "PAUSED", pauseReason: "TARGET_REACHED", completedRounds: 4, currentTaskId: null, nextReviewAt: null, revision: 5 };
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(await screen.findByText("Today's Focus is enough")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Continue Focus" }));
    const dialog = await screen.findByRole("dialog", { name: "Continue Focus" });
    await user.click(within(dialog).getByRole("button", { name: "Continue Focus" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/daily-focus/${localToday()}/continue`, expect.objectContaining({ method: "PUT", body: expect.stringContaining('"primaryTaskId":101') })));
  });

  it("switches Today dates without date-scoping templates", async () => {
    const user = userEvent.setup();
    const selectedDate = shiftLocalDate(localToday(), -1);
    render(<App />);
    await screen.findByText("Prepare launch");
    await user.click(screen.getByRole("button", { name: "Today" }));
    fireEvent.change(screen.getByLabelText("Today date"), { target: { value: selectedDate } });
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`api/today/${selectedDate}`, expect.any(Object)));
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
