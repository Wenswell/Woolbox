import Fastify from "fastify";
import { openRecordsDatabase } from "./database.js";
import {
  ApiError,
  parseArchived,
  parseEntryInput,
  parseId,
  parseIds,
  parseIsoDate,
  parseItemInput,
} from "./domain.js";
import { RecordsService } from "./records-service.js";

export interface BuildAppOptions {
  databaseFile: string;
  now?: () => number;
  logger?: boolean;
}

export function buildApp(options: BuildAppOptions) {
  const app = Fastify({ logger: options.logger ?? false });
  const { db, sqlite } = openRecordsDatabase(options.databaseFile);
  const records = new RecordsService(db, options.now);

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

  app.get("/api/entries", (request) => {
    const query = request.query as Record<string, unknown>;
    const startDate = parseIsoDate(query.startDate, "Start date");
    const endDate = parseIsoDate(query.endDate, "End date");
    const itemId = query.itemId === undefined ? undefined : parseId(query.itemId, "Record item ID");
    return { entries: records.listEntries(startDate, endDate, itemId) };
  });

  app.post("/api/entries", (request, reply) => {
    const entry = records.createEntry(parseEntryInput(request.body));
    return reply.status(201).send({ entry });
  });

  app.put("/api/entries/:id", (request) => {
    const entry = records.updateEntry(parseId((request.params as { id?: unknown }).id), parseEntryInput(request.body));
    return { entry };
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
