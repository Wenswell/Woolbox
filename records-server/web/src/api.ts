import type {
  AuditLogPage,
  DailyFocus,
  Goal,
  GoalInput,
  ItemInput,
  RecordEntry,
  RecordItem,
  RecordMode,
  TaskTemplate,
  TemplateWriteInput,
  TodayState,
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

  async goals(archived: boolean): Promise<Goal[]> {
    return (await request<{ goals: Goal[] }>(`goals?archived=${archived}`)).goals;
  },

  async saveGoal(input: GoalInput, id?: number) {
    return request<{ goal: Goal }>(id ? `goals/${id}` : "goals", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(input),
    });
  },

  async archiveGoals(ids: number[], archived: boolean) {
    return request("goals/batch-archive", { method: "POST", body: JSON.stringify({ ids, archived }) });
  },

  async deleteGoal(id: number) {
    return request(`goals/${id}`, { method: "DELETE" });
  },

  async createGoalEntry(goalId: number, value: number) {
    const now = new Date();
    return request("goal-entries", {
      method: "POST",
      body: JSON.stringify({
        goalId,
        occurredAt: now.getTime(),
        localDate: localDate(now),
        localMinute: now.getHours() * 60 + now.getMinutes(),
        value,
      }),
    });
  },

  async setGoalSubgoalCompleted(id: number, completed: boolean) {
    const now = new Date();
    return request<{ goal: Goal }>(`goal-subgoals/${id}/completion`, {
      method: "PUT",
      body: JSON.stringify({ completed, occurredAt: now.getTime(), localDate: localDate(now) }),
    });
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

  async today(localDate: string): Promise<TodayState> {
    return (await request<{ today: TodayState }>(`today/${localDate}`)).today;
  },

  async createTodayTask(input: { localDate: string; title: string; steps: Array<{ title: string }> }) {
    return request<{ today: TodayState }>("today-tasks", { method: "POST", body: JSON.stringify(input) });
  },

  async createTodayTaskFromTemplate(localDate: string, templateId: number) {
    return request<{ today: TodayState }>("today-tasks/from-template", { method: "POST", body: JSON.stringify({ localDate, templateId }) });
  },

  async createTodayTaskFromTask(localDate: string, sourceTaskId: number) {
    return request<{ today: TodayState }>("today-tasks/from-task", { method: "POST", body: JSON.stringify({ localDate, sourceTaskId }) });
  },

  async setTodayTaskCompleted(taskId: number, expectedRevision: number, completed: boolean) {
    return todayTaskCompletion(`today-tasks/${taskId}/completion`, expectedRevision, completed);
  },

  async setTodayTaskStepCompleted(stepId: number, expectedRevision: number, completed: boolean) {
    return todayTaskCompletion(`today-task-steps/${stepId}/completion`, expectedRevision, completed);
  },

  async createDailyFocus(input: { localDate: string; firstMove: string; primaryTaskId: number; secondaryTaskId: number | null }) {
    return request<{ dailyFocus: DailyFocus }>("daily-focus", { method: "POST", body: JSON.stringify(input) });
  },

  async updateDailyFocus(focus: DailyFocus, input: { firstMove: string; primaryTaskId: number; secondaryTaskId: number | null }) {
    return request<{ dailyFocus: DailyFocus }>(`daily-focus/${focus.localDate}`, {
      method: "PUT",
      body: JSON.stringify({ ...input, expectedRevision: focus.revision, occurredAt: Date.now() }),
    });
  },

  async startDailyFocus(focus: DailyFocus) {
    return dailyFocusTransition(focus, "start");
  },

  async reviewDailyFocus(focus: DailyFocus, currentTaskId: number) {
    return dailyFocusTransition(focus, "review", { currentTaskId });
  },

  async reviewDailyFocusNow(focus: DailyFocus, currentTaskId: number) {
    return dailyFocusTransition(focus, "review-now", { currentTaskId });
  },

  async switchDailyFocusTask(focus: DailyFocus, currentTaskId: number) {
    return dailyFocusTransition(focus, "switch", { currentTaskId });
  },

  async refocusDailyFocus(focus: DailyFocus, primaryTaskId: number, secondaryTaskId: number | null) {
    return dailyFocusTransition(focus, "refocus", { primaryTaskId, secondaryTaskId });
  },

  async continueDailyFocus(focus: DailyFocus, primaryTaskId: number, secondaryTaskId: number | null) {
    return dailyFocusTransition(focus, "continue", { primaryTaskId, secondaryTaskId });
  },

  async endDailyFocus(focus: DailyFocus) {
    return dailyFocusTransition(focus, "end");
  },

  async resetDailyFocus(focus: DailyFocus) {
    return dailyFocusTransition(focus, "reset");
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

function localDate(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function dailyFocusTransition(
  focus: DailyFocus,
  action: "start" | "review" | "review-now" | "switch" | "refocus" | "continue" | "end" | "reset",
  fields: Record<string, unknown> = {},
) {
  return request<{ dailyFocus: DailyFocus }>(`daily-focus/${focus.localDate}/${action}`, {
    method: "PUT",
    body: JSON.stringify({
      expectedRevision: focus.revision,
      occurredAt: Date.now(),
      ...fields,
    }),
  });
}

function todayTaskCompletion(path: string, expectedRevision: number, completed: boolean) {
  return request<{ today: TodayState }>(path, {
    method: "PUT",
    body: JSON.stringify({ completed, expectedRevision, occurredAt: Date.now() }),
  });
}
