import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import { migrate } from "drizzle-orm/better-sqlite3/migrator";
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { buildApp } from "../src/app.js";

describe("Records migrations", () => {
  let directory: string | undefined;

  afterEach(() => {
    if (directory) rmSync(directory, { recursive: true, force: true });
  });

  it("upgrades a database that already applied task windows migration 0009", async () => {
    directory = mkdtempSync(join(tmpdir(), "records-migration-test-"));
    const databaseFile = join(directory, "records.sqlite");
    const publishedMigrations = join(directory, "published-migrations");
    const sourceMigrations = resolve(import.meta.dirname, "../drizzle");
    mkdirSync(join(publishedMigrations, "meta"), { recursive: true });

    for (let index = 0; index <= 9; index += 1) {
      const filename = readMigrationFilename(sourceMigrations, index);
      copyFileSync(join(sourceMigrations, filename), join(publishedMigrations, filename));
    }
    const journal = JSON.parse(readFileSync(join(sourceMigrations, "meta/_journal.json"), "utf8"));
    writeFileSync(
      join(publishedMigrations, "meta/_journal.json"),
      JSON.stringify({ ...journal, entries: journal.entries.slice(0, 10) }),
    );

    const sqlite = new Database(databaseFile);
    migrate(drizzle(sqlite), { migrationsFolder: publishedMigrations });
    expect(tableNames(sqlite)).toContain("todo_task_windows");
    expect(tableNames(sqlite)).not.toContain("todo_step_windows");
    sqlite.close();

    const app = buildApp({ databaseFile });
    const response = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2000-01-01&endDate=2000-01-01",
    });
    expect(response.statusCode).toBe(200);
    await app.close();

    const migrated = new Database(databaseFile, { readonly: true });
    expect(tableNames(migrated)).toContain("todo_step_windows");
    expect(tableNames(migrated)).not.toContain("todo_task_windows");
    migrated.close();
  });
});

function readMigrationFilename(migrationsDirectory: string, index: number): string {
  const journal = JSON.parse(readFileSync(join(migrationsDirectory, "meta/_journal.json"), "utf8"));
  return `${journal.entries[index].tag}.sql`;
}

function tableNames(database: Database.Database): string[] {
  return database.prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
    .all()
    .map((row) => (row as { name: string }).name);
}
