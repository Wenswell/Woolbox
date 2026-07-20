import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import { migrate } from "drizzle-orm/better-sqlite3/migrator";
import { resolve } from "node:path";
import * as schema from "./schema.js";

export function openRecordsDatabase(filename: string) {
  const sqlite = new Database(filename);
  sqlite.pragma("journal_mode = WAL");
  sqlite.pragma("foreign_keys = ON");
  const db = drizzle(sqlite, { schema });
  migrate(db, { migrationsFolder: resolve(import.meta.dirname, "../drizzle") });
  return { db, sqlite };
}

export type RecordsDatabase = ReturnType<typeof openRecordsDatabase>["db"];
