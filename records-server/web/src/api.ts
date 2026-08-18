import type {
  AuditLogPage,
  DailyPlan,
  ItemInput,
  RecordEntry,
  RecordItem,
  RecordMode,
  TaskCreateInput,
  TaskWriteInput,
  TodoTask,
  TodoStep,
} from "./types";

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

  async deleteItem(id: number) {
    return request<{ item: RecordItem }>(`items/${id}`, { method: "DELETE" });
  },

  async tasks(localDate: string): Promise<TodoTask[]> {
    return (await request<{ tasks: TodoTask[] }>(`tasks?localDate=${localDate}`)).tasks;
  },

  async createTask(input: TaskCreateInput) {
    return request<{ task: TodoTask }>("tasks", { method: "POST", body: JSON.stringify(input) });
  },

  async updateTask(id: number, input: TaskWriteInput) {
    return request<{ task: TodoTask }>(`tasks/${id}`, { method: "PUT", body: JSON.stringify(input) });
  },

  async deleteTask(id: number, localDate: string) {
    return request<{ task: TodoTask }>(`tasks/${id}?localDate=${localDate}`, { method: "DELETE" });
  },

  async createStep(taskId: number, title: string, localDate: string) {
    return request<{ step: TodoStep }>(`tasks/${taskId}/steps`, {
      method: "POST",
      body: JSON.stringify({ title, localDate }),
    });
  },

  async updateStep(id: number, title: string, localDate: string) {
    return request<{ step: TodoStep }>(`steps/${id}`, {
      method: "PUT",
      body: JSON.stringify({ title, localDate }),
    });
  },

  async setStepCompleted(id: number, completed: boolean, localDate: string) {
    return request<{ step: TodoStep }>(`steps/${id}/completion`, {
      method: "PUT",
      body: JSON.stringify({ completed, localDate }),
    });
  },

  async deleteStep(id: number, localDate: string) {
    return request<{ step: TodoStep }>(`steps/${id}?localDate=${localDate}`, { method: "DELETE" });
  },

  async reorderSteps(taskId: number, stepIds: number[], localDate: string) {
    return request<{ task: TodoTask }>(`tasks/${taskId}/steps/order`, {
      method: "PUT",
      body: JSON.stringify({ ids: stepIds, localDate }),
    });
  },

  async dailyPlan(localDate: string): Promise<DailyPlan | null> {
    return (await request<{ dailyPlan: DailyPlan | null }>(`daily-plans/${localDate}`)).dailyPlan;
  },

  async createDailyPlan(input: { localDate: string; firstMove: string; primaryTaskId: number; secondaryTaskId: number | null }) {
    return request<{ dailyPlan: DailyPlan }>("daily-plans", { method: "POST", body: JSON.stringify(input) });
  },

  async updateDailyPlan(plan: DailyPlan, input: { firstMove: string; primaryTaskId: number; secondaryTaskId: number | null }) {
    return request<{ dailyPlan: DailyPlan }>(`daily-plans/${plan.localDate}`, {
      method: "PUT",
      body: JSON.stringify({ ...input, expectedRevision: plan.revision, occurredAt: Date.now() }),
    });
  },

  async startDailyPlan(plan: DailyPlan) {
    return dailyPlanTransition(plan, "start");
  },

  async reviewDailyPlan(plan: DailyPlan, currentTaskId: number) {
    return dailyPlanTransition(plan, "review", currentTaskId);
  },

  async startExtraRound(plan: DailyPlan, currentTaskId: number) {
    return dailyPlanTransition(plan, "extra-round", currentTaskId);
  },

  async endDailyPlan(plan: DailyPlan) {
    return dailyPlanTransition(plan, "end");
  },

  async entries(startDate: string, endDate: string, itemId?: string, mode?: RecordMode): Promise<RecordEntry[]> {
    const params = new URLSearchParams({ startDate, endDate });
    if (itemId) {
      params.set("itemId", itemId);
    }
    if (mode) {
      params.set("mode", mode);
    }
    return (await request<{ entries: RecordEntry[] }>(`entries?${params}`)).entries;
  },

  async createEntry(input: {
    itemId: number;
    occurredAt: number;
    localDate: string;
    localMinute: number;
    value: number;
  }) {
    return request<{ entry: RecordEntry }>("entries", { method: "POST", body: JSON.stringify(input) });
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

function dailyPlanTransition(plan: DailyPlan, action: "start" | "review" | "extra-round" | "end", currentTaskId?: number) {
  return request<{ dailyPlan: DailyPlan }>(`daily-plans/${plan.localDate}/${action}`, {
    method: "PUT",
    body: JSON.stringify({
      expectedRevision: plan.revision,
      occurredAt: Date.now(),
      ...(currentTaskId === undefined ? {} : { currentTaskId }),
    }),
  });
}
