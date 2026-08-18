import type {
  AuditLogPage,
  DailyPlan,
  ItemInput,
  RecordEntry,
  RecordItem,
  RecordMode,
  TaskTemplate,
  TemplateWriteInput,
} from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`api/${path}`, {
    ...init,
    headers: init?.body ? { "content-type": "application/json" } : undefined,
  });
  const value = await response.json();
  if (!response.ok) throw new Error(value.error?.message ?? "Request failed.");
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
    return request("items/batch-archive", { method: "POST", body: JSON.stringify({ ids, archived }) });
  },

  async deleteItem(id: number) {
    return request<{ item: RecordItem }>(`items/${id}`, { method: "DELETE" });
  },

  async templates(archived: boolean): Promise<TaskTemplate[]> {
    return (await request<{ templates: TaskTemplate[] }>(`templates?archived=${archived}`)).templates;
  },

  async saveTemplate(input: TemplateWriteInput, id?: number) {
    return request<{ template: TaskTemplate }>(id ? `templates/${id}` : "templates", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(input),
    });
  },

  async archiveTemplates(ids: number[], archived: boolean) {
    return request("templates/batch-archive", { method: "POST", body: JSON.stringify({ ids, archived }) });
  },

  async deleteTemplate(id: number) {
    return request<{ template: TaskTemplate }>(`templates/${id}`, { method: "DELETE" });
  },

  async dailyPlan(localDate: string): Promise<DailyPlan | null> {
    return (await request<{ dailyPlan: DailyPlan | null }>(`daily-plans/${localDate}`)).dailyPlan;
  },

  async createDailyPlan(input: { localDate: string; firstMove: string; primaryTemplateId: number; secondaryTemplateId: number | null }) {
    return request<{ dailyPlan: DailyPlan }>("daily-plans", { method: "POST", body: JSON.stringify(input) });
  },

  async updateDailyPlan(plan: DailyPlan, input: { firstMove: string; primaryTemplateId: number; secondaryTemplateId: number | null }) {
    return request<{ dailyPlan: DailyPlan }>(`daily-plans/${plan.localDate}`, {
      method: "PUT",
      body: JSON.stringify({ ...input, expectedRevision: plan.revision, occurredAt: Date.now() }),
    });
  },

  async startDailyPlan(plan: DailyPlan) {
    return dailyPlanTransition(plan, "start");
  },

  async reviewDailyPlan(plan: DailyPlan, currentCommitmentId: number) {
    return dailyPlanTransition(plan, "review", currentCommitmentId);
  },

  async reviewDailyPlanNow(plan: DailyPlan, currentCommitmentId: number) {
    return dailyPlanTransition(plan, "review-now", currentCommitmentId);
  },

  async switchDailyPlanCommitment(plan: DailyPlan, currentCommitmentId: number) {
    return dailyPlanTransition(plan, "switch", currentCommitmentId);
  },

  async continueDailyPlan(plan: DailyPlan, currentCommitmentId: number) {
    return dailyPlanTransition(plan, "continue", currentCommitmentId);
  },

  async addDailyPlanCommitment(plan: DailyPlan, templateId: number) {
    return request<{ dailyPlan: DailyPlan }>(`daily-plans/${plan.localDate}/commitments`, {
      method: "POST",
      body: JSON.stringify({ templateId, expectedRevision: plan.revision, occurredAt: Date.now() }),
    });
  },

  async setCommitmentStepCompleted(plan: DailyPlan, id: number, completed: boolean) {
    return request<{ dailyPlan: DailyPlan }>(`commitment-steps/${id}/completion`, {
      method: "PUT",
      body: JSON.stringify({ completed, expectedRevision: plan.revision, occurredAt: Date.now() }),
    });
  },

  async endDailyPlan(plan: DailyPlan) {
    return dailyPlanTransition(plan, "end");
  },

  async resetDailyPlan(plan: DailyPlan) {
    return dailyPlanTransition(plan, "reset");
  },

  async entries(startDate: string, endDate: string, itemId?: string, mode?: RecordMode): Promise<RecordEntry[]> {
    const params = new URLSearchParams({ startDate, endDate });
    if (itemId) params.set("itemId", itemId);
    if (mode) params.set("mode", mode);
    return (await request<{ entries: RecordEntry[] }>(`entries?${params}`)).entries;
  },

  async createEntry(input: { itemId: number; occurredAt: number; localDate: string; localMinute: number; value: number }) {
    return request<{ entry: RecordEntry }>("entries", { method: "POST", body: JSON.stringify(input) });
  },

  async auditLogs(beforeId?: number): Promise<AuditLogPage> {
    return request(beforeId === undefined ? "audit-logs" : `audit-logs?beforeId=${beforeId}`);
  },

  async deleteEntries(ids: number[]) {
    return request("entries/batch-delete", { method: "POST", body: JSON.stringify({ ids }) });
  },
};

function dailyPlanTransition(
  plan: DailyPlan,
  action: "start" | "review" | "review-now" | "switch" | "continue" | "end" | "reset",
  currentCommitmentId?: number,
) {
  return request<{ dailyPlan: DailyPlan }>(`daily-plans/${plan.localDate}/${action}`, {
    method: "PUT",
    body: JSON.stringify({
      expectedRevision: plan.revision,
      occurredAt: Date.now(),
      ...(currentCommitmentId === undefined ? {} : { currentCommitmentId }),
    }),
  });
}
