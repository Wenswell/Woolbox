import { buildApp } from "./app.js";

const portValue = Number(process.env.PORT ?? "4320");
if (!Number.isSafeInteger(portValue) || portValue < 1 || portValue > 65535) {
  throw new Error("PORT must be an integer from 1 to 65535.");
}

const app = buildApp({
  databaseFile: process.env.RECORDS_DATABASE_PATH ?? "/srv/records/records.sqlite",
  logger: true,
  webRoot: process.env.RECORDS_WEB_ROOT ?? "./dist/web",
});

await app.listen({ host: "127.0.0.1", port: portValue });
