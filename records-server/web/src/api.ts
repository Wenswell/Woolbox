import type { AuditLogPage, ItemInput, RecordEntry, RecordItem } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`api/${path}`, {
    ...init,
    headers: init?.body ? { "content-type": "application/json" } : undefined,
  });
  const value = await response.json();
  if (!response.ok) {
    throw new Error(value.error?.message ?? "Request failed.");
  }
  return value;
}

export const api = {
  async items(archived: boolean): Promise<RecordItem[]> {
    return (await request<{ items: RecordItem[] }>(`items?archived=${archived}`)).items;
  },

  async saveItem(input: ItemInput, id?: number) {
    return request<{ item: RecordItem }>(id ? `items/${id}` : "items", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(input),
    });
  },

  async archive(ids: number[], archived: boolean) {
    return request("items/batch-archive", {
      method: "POST",
      body: JSON.stringify({ ids, archived }),
    });
  },

  async entries(startDate: string, endDate: string, itemId?: string): Promise<RecordEntry[]> {
    const params = new URLSearchParams({ startDate, endDate });
    if (itemId) {
      params.set("itemId", itemId);
    }
    return (await request<{ entries: RecordEntry[] }>(`entries?${params}`)).entries;
  },

  async auditLogs(beforeId?: number): Promise<AuditLogPage> {
    return request(beforeId === undefined ? "audit-logs" : `audit-logs?beforeId=${beforeId}`);
  },

  async deleteEntries(ids: number[]) {
    return request("entries/batch-delete", {
      method: "POST",
      body: JSON.stringify({ ids }),
    });
  },
};
