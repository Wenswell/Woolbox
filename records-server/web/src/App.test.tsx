import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

const activeItem = { id: 1, name: "Water", description: "", valueType: "NUMBER", defaultValue: 1, stepValue: 1, unit: "ml", goalValue: null, goalPeriod: null, priority: 1, archived: false, createdAt: 1, updatedAt: 1, windows: [] };
const archivedItem = { ...activeItem, id: 2, name: "Read", unit: "pages", archived: true };
const entry = { id: 3, itemId: 1, occurredAt: 1_752_984_000_000, localDate: "2025-07-20", localMinute: 600, value: 2, createdAt: 1, itemName: "Water", itemUnit: "ml", itemValueType: "NUMBER" };
const auditLog = { id: 4, action: "ENTRY_CREATED", entityType: "ENTRY", entityId: 3, before: null, after: { id: 3, itemId: 1, itemName: "Water", occurredAt: entry.occurredAt, localDate: entry.localDate, localMinute: entry.localMinute, value: 2 }, createdAt: entry.occurredAt };

describe("Records administration", () => {
  beforeEach(() => {
    vi.stubGlobal("confirm", vi.fn(() => true));
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("audit-logs")) return response({ logs: [auditLog], nextBeforeId: null });
      if (init?.method === "POST") return response({ items: [activeItem], entries: [entry] });
      if (url.includes("archived=true")) return response({ items: [archivedItem] });
      if (url.includes("items?archived=false")) return response({ items: [activeItem] });
      return response({ entries: [entry] });
    }));
  });

  it("applies entry filters and archives selected active items", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("2 ml");

    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2025-07-01" } });
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2025-07-31" } });
    await user.selectOptions(screen.getByLabelText("Item"), "1");
    await user.click(screen.getByRole("button", { name: "Apply" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining("entries?startDate=2025-07-01&endDate=2025-07-31&itemId=1"), expect.anything()));

    await user.click(screen.getByRole("button", { name: "Record items" }));
    await user.click(screen.getByLabelText("Select Water"));
    await user.click(screen.getByRole("button", { name: "Archive selected" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/items/batch-archive", expect.objectContaining({ method: "POST", body: JSON.stringify({ ids: [1], archived: true }) })));
  });

  it("keeps Entries read-only while allowing deletion", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("2 ml");

    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();
    await user.click(screen.getByLabelText("Select Water"));
    await user.click(screen.getByRole("button", { name: "Delete selected" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/entries/batch-delete", expect.objectContaining({ method: "POST", body: JSON.stringify({ ids: [3] }) })));
    expect(vi.mocked(fetch).mock.calls.some(([, init]) => init?.method === "PUT")).toBe(false);
  });

  it("shows audit logs with the recorded operation", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("2 ml");

    await user.click(screen.getByRole("button", { name: "Logs" }));

    expect(await screen.findByText("Created entry")).toBeTruthy();
    expect(screen.getByText("Entry: Water")).toBeTruthy();
    expect(screen.getByText("Water; 2025-07-20 10:00; 2")).toBeTruthy();
  });
});

function response(value: unknown) {
  return Promise.resolve({ ok: true, json: async () => value } as Response);
}
