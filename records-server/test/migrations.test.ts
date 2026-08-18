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

  it("replaces legacy planning data while preserving record data and audits", async () => {
    directory = mkdtempSync(join(tmpdir(), "records-migration-test-"));
    const databaseFile = join(directory, "records.sqlite");
    const publishedMigrations = join(directory, "published-migrations");
    const sourceMigrations = resolve(import.meta.dirname, "../drizzle");
    mkdirSync(join(publishedMigrations, "meta"), { recursive: true });

    for (let index = 0; index <= 10; index += 1) {
      const filename = readMigrationFilename(sourceMigrations, index);
      copyFileSync(join(sourceMigrations, filename), join(publishedMigrations, filename));
    }
    const journal = JSON.parse(readFileSync(join(sourceMigrations, "meta/_journal.json"), "utf8"));
    writeFileSync(
      join(publishedMigrations, "meta/_journal.json"),
      JSON.stringify({ ...journal, entries: journal.entries.slice(0, 11) }),
    );

    const sqlite = new Database(databaseFile);
    migrate(drizzle(sqlite), { migrationsFolder: publishedMigrations });
    const taskId = sqlite.prepare(
      "INSERT INTO todo_tasks (title, horizon, completed_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
    ).run("Legacy task", "TODAY", 2, 1, 2).lastInsertRowid;
    const stepId = sqlite.prepare(
      "INSERT INTO todo_steps (task_id, title, position, completed_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).run(taskId, "Legacy step", 0, 2, 1, 2).lastInsertRowid;
    sqlite.prepare(
      "INSERT INTO todo_step_windows (step_id, start_minute, end_minute) VALUES (?, ?, ?)",
    ).run(stepId, 480, 540);
    const itemId = sqlite.prepare(
      "INSERT INTO record_items (name, description, value_type, default_value, step_value, unit, priority, archived, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ).run("Water", "", "NUMBER", 1, 1, "times", 0, 0, 1, 1).lastInsertRowid;
    sqlite.prepare(
      "INSERT INTO record_windows (item_id, start_minute, end_minute) VALUES (?, ?, ?)",
    ).run(itemId, 480, 540);
    sqlite.prepare(
      "INSERT INTO record_audit_logs (action, entity_type, entity_id, before_json, after_json, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).run("TASK_CREATED", "TASK", taskId, null, "{}", 2);
    sqlite.prepare(
      "INSERT INTO record_audit_logs (action, entity_type, entity_id, before_json, after_json, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).run("ITEM_CREATED", "ITEM", itemId, null, "{}", 2);
    sqlite.close();

    const app = buildApp({ databaseFile });
    const response = await app.inject({
      method: "GET",
      url: "/api/overview?startDate=2000-01-01&endDate=2000-01-01&planDate=2000-01-01",
    });
    expect(response.statusCode).toBe(200);
    await app.close();

    const migrated = new Database(databaseFile, { readonly: true });
    expect(tableNames(migrated)).not.toContain("todo_step_windows");
    expect(tableNames(migrated)).not.toContain("record_windows");
    expect(tableNames(migrated)).not.toContain("todo_task_windows");
    expect(tableNames(migrated)).not.toContain("todo_tasks");
    expect(tableNames(migrated)).not.toContain("todo_steps");
    expect(tableNames(migrated)).not.toContain("todo_step_completions");
    expect(tableNames(migrated)).toEqual(expect.arrayContaining([
      "task_templates",
      "task_template_steps",
      "daily_plans",
      "today_commitments",
      "today_commitment_steps",
    ]));
    expect(migrated.prepare("SELECT COUNT(*) AS count FROM daily_plans").get()).toEqual({ count: 0 });
    expect(migrated.prepare("SELECT name FROM record_items WHERE id = ?").get(itemId)).toEqual({ name: "Water" });
    expect(migrated.prepare("SELECT entity_type FROM record_audit_logs ORDER BY id").all()).toEqual([
      { entity_type: "ITEM" },
    ]);
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
