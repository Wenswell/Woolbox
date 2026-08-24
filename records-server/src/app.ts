import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import { resolve } from "node:path";
import { openRecordsDatabase } from "./database.js";
import {
  ApiError,
  parseArchived,
  parseDailyFocusCreateInput,
  parseDailyFocusReviewInput,
  parseDailyFocusSelectionInput,
  parseDailyFocusTransitionInput,
  parseDailyFocusUpdateInput,
  parseDailyCheckinWakeInput,
  parseEntryInput,
  parseGoalEntryInput,
  parseGoalInput,
  parseGoalSubgoalCompletionInput,
  parseId,
  parseIds,
  parseIsoDate,
  parseItemInput,
  parseOptionalId,
  parseOptionalRecordMode,
  parseTemplateWriteInput,
  parseTodayTaskCompletionInput,
  parseTodayTaskCreateInput,
  parseTodayTaskFromTaskInput,
  parseTodayTaskFromTemplateInput,
  parseHourlyCheckinInput,
} from "./domain.js";
import { RecordsService } from "./records-service.js";

export interface BuildAppOptions {
  databaseFile: string;
  now?: () => number;
  logger?: boolean;
  webRoot?: string;
}

export function buildApp(options: BuildAppOptions) {
  const app = Fastify({ logger: options.logger ?? false });
  const { db, sqlite } = openRecordsDatabase(options.databaseFile);
  const records = new RecordsService(db, options.now);

  if (options.webRoot) {
    app.register(fastifyStatic, { root: resolve(options.webRoot), wildcard: false });
  }

  app.addHook("onClose", (_instance, done) => {
    sqlite.close();
    done();
  });

  app.setErrorHandler((error, _request, reply) => {
    if (error instanceof ApiError) {
      void reply.status(error.statusCode).send({ error: { message: error.message } });
      return;
    }
    if (
      error instanceof Error
      && "statusCode" in error
      && typeof error.statusCode === "number"
      && error.statusCode >= 400
      && error.statusCode < 500
    ) {
      void reply.status(error.statusCode).send({ error: { message: error.message } });
      return;
    }
    app.log.error(error);
    void reply.status(500).send({ error: { message: "Internal server error." } });
  });

  app.get("/api/items", (request) => {
    const query = request.query as Record<string, unknown>;
    const archived = query.archived === undefined ? false : query.archived === "true"
      ? true
      : query.archived === "false"
        ? false
        : invalidBoolean();
    return { items: records.listItems(archived) };
  });

  app.post("/api/items", (request, reply) => {
    const item = records.createItem(parseItemInput(request.body));
    return reply.status(201).send({ item });
  });

  app.put("/api/items/:id", (request) => {
    const item = records.updateItem(parseId((request.params as { id?: unknown }).id), parseItemInput(request.body));
    return { item };
  });

  app.post("/api/items/batch-archive", (request) => {
    const items = records.setItemsArchived(parseIds(request.body), parseArchived(request.body));
    return { items };
  });

  app.delete("/api/items/:id", (request) => {
    const item = records.deleteArchivedItem(parseId((request.params as { id?: unknown }).id));
    return { item };
  });

  app.get("/api/goals", (request) => {
    const query = request.query as Record<string, unknown>;
    const archived = query.archived === undefined ? false : query.archived === "true"
      ? true
      : query.archived === "false"
        ? false
        : invalidBoolean();
    return { goals: records.listGoals(archived) };
  });

  app.post("/api/goals", (request, reply) => {
    const goal = records.createGoal(parseGoalInput(request.body));
    return reply.status(201).send({ goal });
  });

  app.put("/api/goals/:id", (request) => ({
    goal: records.updateGoal(parseId((request.params as { id?: unknown }).id, "Goal ID"), parseGoalInput(request.body)),
  }));

  app.post("/api/goals/batch-archive", (request) => ({
    goals: records.setGoalsArchived(parseIds(request.body), parseArchived(request.body)),
  }));

  app.delete("/api/goals/:id", (request) => ({
    goal: records.deleteArchivedGoal(parseId((request.params as { id?: unknown }).id, "Goal ID")),
  }));

  app.post("/api/goal-entries", (request, reply) => {
    const entry = records.createGoalEntry(parseGoalEntryInput(request.body));
    return reply.status(201).send({ entry });
  });

  app.put("/api/goal-subgoals/:id/completion", (request) => ({
    goal: records.setGoalSubgoalCompleted(
      parseId((request.params as { id?: unknown }).id, "Subgoal ID"),
      parseGoalSubgoalCompletionInput(request.body),
    ),
  }));

  app.get("/api/templates", (request) => {
    const query = request.query as Record<string, unknown>;
    const archived = query.archived === undefined ? false : query.archived === "true"
      ? true
      : query.archived === "false"
        ? false
        : invalidBoolean();
    return { templates: records.listTemplates(archived) };
  });

  app.get("/api/overview", (request) => {
    const query = request.query as Record<string, unknown>;
    return records.overview(
      parseIsoDate(query.startDate, "Start date"),
      parseIsoDate(query.endDate, "End date"),
      parseIsoDate(query.planDate, "Plan date"),
    );
  });

  app.get("/api/today/:localDate", (request) => ({
    today: records.getToday(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
    ),
  }));

  app.post("/api/today-tasks", (request, reply) => {
    const today = records.createTodayTask(parseTodayTaskCreateInput(request.body));
    return reply.status(201).send({ today });
  });

  app.post("/api/today-tasks/from-template", (request, reply) => {
    const today = records.createTodayTaskFromTemplate(parseTodayTaskFromTemplateInput(request.body));
    return reply.status(201).send({ today });
  });

  app.post("/api/today-tasks/from-task", (request, reply) => {
    const today = records.createTodayTaskFromTask(parseTodayTaskFromTaskInput(request.body));
    return reply.status(201).send({ today });
  });

  app.put("/api/today-task-steps/:id/completion", (request) => ({
    today: records.setTodayTaskStepCompleted(
      parseId((request.params as { id?: unknown }).id, "Today task step ID"),
      parseTodayTaskCompletionInput(request.body),
    ),
  }));

  app.post("/api/daily-checkins/wake", (request, reply) => {
    const today = records.markAwake(parseDailyCheckinWakeInput(request.body));
    return reply.status(201).send({ today });
  });

  app.post("/api/daily-checkins/hourly", (request, reply) => {
    const today = records.markHourlyCheckin(parseHourlyCheckinInput(request.body));
    return reply.status(201).send({ today });
  });

  app.post("/api/daily-focus", (request, reply) => {
    const focus = records.createDailyFocus(parseDailyFocusCreateInput(request.body));
    return reply.status(201).send({ dailyFocus: focus });
  });

  app.put("/api/daily-focus/:localDate", (request) => ({
    dailyFocus: records.updateDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusUpdateInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/start", (request) => ({
    dailyFocus: records.startDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusTransitionInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/review", (request) => ({
    dailyFocus: records.reviewDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusReviewInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/review-now", (request) => ({
    dailyFocus: records.reviewDailyFocusNow(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusReviewInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/switch", (request) => ({
    dailyFocus: records.switchDailyFocusTask(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusReviewInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/refocus", (request) => ({
    dailyFocus: records.refocusDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusSelectionInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/continue", (request) => ({
    dailyFocus: records.continueDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusSelectionInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/end", (request) => ({
    dailyFocus: records.endDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusTransitionInput(request.body),
    ),
  }));

  app.put("/api/daily-focus/:localDate/reset", (request) => ({
    dailyFocus: records.resetDailyFocus(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyFocusTransitionInput(request.body),
    ),
  }));

  app.post("/api/templates", (request, reply) => {
    const template = records.createTemplate(parseTemplateWriteInput(request.body));
    return reply.status(201).send({ template });
  });

  app.put("/api/templates/:id", (request) => ({
    template: records.updateTemplate(
      parseId((request.params as { id?: unknown }).id, "Template ID"),
      parseTemplateWriteInput(request.body),
    ),
  }));

  app.post("/api/templates/batch-archive", (request) => {
    const templates = records.setTemplatesArchived(parseIds(request.body), parseArchived(request.body));
    return { templates };
  });

  app.delete("/api/templates/:id", (request) => {
    const template = records.deleteArchivedTemplate(
      parseId((request.params as { id?: unknown }).id, "Template ID"),
    );
    return { template };
  });

  app.get("/api/audit-logs", (request) => {
    const query = request.query as Record<string, unknown>;
    return records.listAuditLogs(parseOptionalId(query.beforeId, "Audit log cursor"));
  });

  app.get("/api/entries", (request) => {
    const query = request.query as Record<string, unknown>;
    const startDate = parseIsoDate(query.startDate, "Start date");
    const endDate = parseIsoDate(query.endDate, "End date");
    const itemId = query.itemId === undefined ? undefined : parseId(query.itemId, "Record item ID");
    return { entries: records.listEntries(startDate, endDate, itemId, parseOptionalRecordMode(query.mode)) };
  });

  app.get("/api/entries/latest", () => ({ entries: records.listLatestEntries() }));

  app.get("/api/entries/:id", (request) => ({
    entry: records.getEntry(parseId((request.params as { id?: unknown }).id, "Record ID")),
  }));

  app.post("/api/entries", (request, reply) => {
    const entry = records.createEntry(parseEntryInput(request.body));
    return reply.status(201).send({ entry });
  });

  app.post("/api/entries/batch-delete", (request) => {
    const entries = records.deleteEntries(parseIds(request.body));
    return { entries };
  });

  app.delete("/api/entries/:id", (request) => {
    const entry = records.deleteEntry(parseId((request.params as { id?: unknown }).id));
    return { entry };
  });

  return app;
}

function invalidBoolean(): never {
  throw new ApiError(400, "Archived must be true or false.");
}
