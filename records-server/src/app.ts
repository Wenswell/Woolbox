import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import { resolve } from "node:path";
import { openRecordsDatabase } from "./database.js";
import {
  ApiError,
  parseArchived,
  parseCommitmentStepCompletionInput,
  parseDailyPlanAddCommitmentInput,
  parseDailyPlanCreateInput,
  parseDailyPlanReviewInput,
  parseDailyPlanTransitionInput,
  parseDailyPlanUpdateInput,
  parseEntryInput,
  parseId,
  parseIds,
  parseIsoDate,
  parseItemInput,
  parseOptionalId,
  parseOptionalRecordMode,
  parseTemplateWriteInput,
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

  app.get("/api/daily-plans/:localDate", (request) => ({
    dailyPlan: records.getDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
    ),
  }));

  app.post("/api/daily-plans", (request, reply) => {
    const dailyPlan = records.createDailyPlan(parseDailyPlanCreateInput(request.body));
    return reply.status(201).send({ dailyPlan });
  });

  app.put("/api/daily-plans/:localDate", (request) => ({
    dailyPlan: records.updateDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanUpdateInput(request.body),
    ),
  }));

  app.put("/api/daily-plans/:localDate/start", (request) => ({
    dailyPlan: records.startDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanTransitionInput(request.body),
    ),
  }));

  app.put("/api/daily-plans/:localDate/review", (request) => ({
    dailyPlan: records.reviewDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanReviewInput(request.body),
    ),
  }));

  app.put("/api/daily-plans/:localDate/review-now", (request) => ({
    dailyPlan: records.reviewDailyPlanNow(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanReviewInput(request.body),
    ),
  }));

  app.put("/api/daily-plans/:localDate/switch", (request) => ({
    dailyPlan: records.switchDailyPlanCommitment(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanReviewInput(request.body),
    ),
  }));

  app.put("/api/daily-plans/:localDate/continue", (request) => ({
    dailyPlan: records.continueDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanReviewInput(request.body),
    ),
  }));

  app.post("/api/daily-plans/:localDate/commitments", (request, reply) => {
    const dailyPlan = records.addDailyPlanCommitment(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanAddCommitmentInput(request.body),
    );
    return reply.status(201).send({ dailyPlan });
  });

  app.put("/api/daily-plans/:localDate/end", (request) => ({
    dailyPlan: records.endDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanTransitionInput(request.body),
    ),
  }));

  app.put("/api/daily-plans/:localDate/reset", (request) => ({
    dailyPlan: records.resetDailyPlan(
      parseIsoDate((request.params as { localDate?: unknown }).localDate, "Local date"),
      parseDailyPlanTransitionInput(request.body),
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

  app.put("/api/commitment-steps/:id/completion", (request) => {
    const input = parseCommitmentStepCompletionInput(request.body);
    return {
      dailyPlan: records.setCommitmentStepCompleted(
        parseId((request.params as { id?: unknown }).id, "Commitment step ID"),
        input,
      ),
    };
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
