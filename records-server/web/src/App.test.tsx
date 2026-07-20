import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

const activeItem = { id:1,name:"Water",description:"",valueType:"NUMBER",defaultValue:1,stepValue:1,unit:"ml",goalValue:null,goalPeriod:null,priority:1,archived:false,createdAt:1,updatedAt:1,windows:[] };
const archivedItem = { ...activeItem,id:2,name:"Read",unit:"pages",archived:true };
const entry = { id:3,itemId:1,occurredAt:1_752_984_000_000,localDate:"2025-07-20",localMinute:600,value:2,createdAt:1,itemName:"Water",itemUnit:"ml",itemValueType:"NUMBER" };

describe("Records administration", () => {
  beforeEach(() => {
    vi.stubGlobal("confirm", vi.fn(() => true));
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === "POST" || init?.method === "PUT") return response({ items:[activeItem], entry });
      if (url.includes("archived=true")) return response({ items:[archivedItem] });
      if (url.includes("items?archived=false")) return response({ items:[activeItem] });
      return response({ entries:[entry] });
    }));
  });

  it("applies entry filters and archives selected active items", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("2 ml");

    fireEvent.change(screen.getByLabelText("From"), { target:{ value:"2025-07-01" } });
    fireEvent.change(screen.getByLabelText("To"), { target:{ value:"2025-07-31" } });
    await user.selectOptions(screen.getByLabelText("Item"), "1");
    await user.click(screen.getByRole("button", { name:"Apply" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining("entries?startDate=2025-07-01&endDate=2025-07-31&itemId=1"), expect.anything()));

    await user.click(screen.getByRole("button", { name:"Record items" }));
    await user.click(screen.getByLabelText("Select Water"));
    await user.click(screen.getByRole("button", { name:"Archive selected" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/items/batch-archive", expect.objectContaining({ method:"POST", body:JSON.stringify({ ids:[1], archived:true }) })));
  });

  it("sends all editable entry fields", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("2 ml");
    await user.click(screen.getByRole("button", { name:"Edit" }));
    fireEvent.change(screen.getByLabelText("Value"), { target:{ value:"4" } });
    fireEvent.change(screen.getByLabelText("Local date"), { target:{ value:"2025-07-21" } });
    fireEvent.change(screen.getByLabelText("Local time"), { target:{ value:"11:30" } });
    await user.click(screen.getByRole("button", { name:"Save" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("api/entries/3", expect.objectContaining({
      method:"PUT",
      body:expect.stringContaining('"localMinute":690'),
    })));
    const call = vi.mocked(fetch).mock.calls.find(([url]) => String(url) === "api/entries/3")!;
    expect(JSON.parse(String(call[1]?.body))).toMatchObject({ itemId:1,localDate:"2025-07-21",localMinute:690,value:4 });
  });

  it("shows an entry save failure inside the open dialog", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("2 ml");
    await user.click(screen.getByRole("button", { name:"Edit" }));
    vi.mocked(fetch).mockResolvedValueOnce(await errorResponse("Record item is archived."));

    await user.click(screen.getByRole("button", { name:"Save" }));

    expect((await screen.findByRole("alert")).textContent).toBe("Record item is archived.");
    expect(screen.getByRole("dialog", { name:"Edit entry" })).toBeTruthy();
  });
});

function response(value: unknown) {
  return Promise.resolve({ ok:true, json:async()=>value } as Response);
}

function errorResponse(message: string) {
  return Promise.resolve({ ok:false, json:async()=>({ error:{ message } }) } as Response);
}
