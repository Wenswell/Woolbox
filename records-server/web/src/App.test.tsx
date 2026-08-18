import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

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
  id: 10, title: "Prepare launch", completedAt: null, createdAt: 1, updatedAt: 1,
  steps: [
    { id: 11, taskId: 10, title: "Pack bag", position: 0, completedAt: null, createdAt: 1, updatedAt: 1 },
    { id: 12, taskId: 10, title: "Check route", position: 1, completedAt: null, createdAt: 1, updatedAt: 1 },
  ],
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
  beforeEach(() => {
    window.history.replaceState(null, "", window.location.pathname);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (method !== "GET") {
        if (url === "api/items") return response({ item: activeItem });
        if (url.includes("completion")) return response({ step: { ...task.steps[0], completedAt: Date.now() } });
        if (url.includes("steps/order")) return response({ task });
        if (url === "api/entries") return response({ entry });
        return response({ ok: true, task, step: task.steps[0], item: activeItem });
      }
      if (url === "api/tasks") return response({ tasks: [task] });
      if (url.includes("audit-logs")) return response({ logs: [auditLog], nextBeforeId: null });
      if (url.includes("items?archived=true")) return response({ items: [archivedItem] });
      if (url.includes("items?archived=false")) return response({ items: [activeItem, measurementItem] });
      if (url.includes("entries?")) return response({ entries: [entry] });
      throw new Error(`Unhandled request: ${method} ${url}`);
    }));
  });

  it("loads the six management areas and overview data", async () => {
    render(<App />);
    expect(await screen.findByText("Prepare launch")).toBeTruthy();
    for (const label of ["Overview", "Tasks", "Accumulate", "Measurement", "Entries", "Activity"]) {
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
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/steps/11/completion", expect.objectContaining({ method: "PUT", body: JSON.stringify({ completed: true }) })));

    await user.click(within(dialog).getByRole("button", { name: "Move Pack bag down" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/tasks/10/steps/order", expect.objectContaining({ method: "PUT", body: JSON.stringify({ ids: [12, 11] }) })));
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
