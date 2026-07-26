import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import { resolve } from "node:path";
import { openRecordsDatabase } from "./database.js";
import {
  ApiError,
  parseArchived,
  parseCompleted,
  parseEntryInput,
  parseId,
  parseIds,
  parseIsoDate,
  parseItemInput,
  parseOptionalId,
  parseOptionalRecordMode,
  parseTaskCreateInput,
  parseStepInput,
  parseTaskWriteInput,
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

  app.get("/api/tasks", () => ({ tasks: records.listTasks() }));

  app.get("/api/overview", (request) => {
    const query = request.query as Record<string, unknown>;
    return records.overview(
      parseIsoDate(query.startDate, "Start date"),
      parseIsoDate(query.endDate, "End date"),
    );
  });

  app.post("/api/tasks", (request, reply) => {
    const task = records.createTask(parseTaskCreateInput(request.body));
    return reply.status(201).send({ task });
  });

  app.put("/api/tasks/:id", (request) => ({
    task: records.updateTask(
      parseId((request.params as { id?: unknown }).id, "Task ID"),
      parseTaskWriteInput(request.body),
    ),
  }));

  app.delete("/api/tasks/:id", (request) => ({
    task: records.deleteTask(parseId((request.params as { id?: unknown }).id, "Task ID")),
  }));

  app.post("/api/tasks/:id/steps", (request, reply) => {
    const step = records.createStep(
      parseId((request.params as { id?: unknown }).id, "Task ID"),
      parseStepInput(request.body),
    );
    return reply.status(201).send({ step });
  });

  app.put("/api/tasks/:id/steps/order", (request) => ({
    task: records.reorderSteps(
      parseId((request.params as { id?: unknown }).id, "Task ID"),
      parseIds(request.body),
    ),
  }));


  app.put("/api/steps/:id", (request) => ({
    step: records.updateStep(
      parseId((request.params as { id?: unknown }).id, "Step ID"),
      parseStepInput(request.body),
    ),
  }));

  app.put("/api/steps/:id/completion", (request) => ({
    step: records.setStepCompleted(
      parseId((request.params as { id?: unknown }).id, "Step ID"),
      parseCompleted(request.body),
    ),
  }));

  app.delete("/api/steps/:id", (request) => ({
    step: records.deleteStep(parseId((request.params as { id?: unknown }).id, "Step ID")),
  }));

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
