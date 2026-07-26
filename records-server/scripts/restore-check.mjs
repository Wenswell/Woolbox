#!/usr/bin/env node

import Database from "better-sqlite3";
import { copyFile, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, join, resolve } from "node:path";

const backupArgument = process.argv.slice(2).find((argument) => argument !== "--");
if (!backupArgument) throw new Error("Usage: pnpm restore-check -- <backup.sqlite>");

const backupFile = resolve(backupArgument);
const restoreDirectory = await mkdtemp(join(tmpdir(), "records-restore-"));
const restoredFile = join(restoreDirectory, basename(backupFile));

try {
  await copyFile(backupFile, restoredFile);
  const restored = new Database(restoredFile, { readonly: true, fileMustExist: true });
  try {
    const integrity = restored.pragma("integrity_check", { simple: true });
    if (integrity !== "ok") throw new Error(`Restore integrity check failed: ${String(integrity)}`);

    const tables = restored
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
      .all()
      .map(({ name }) => name);
    for (const table of [
      "record_items",
      "record_windows",
      "record_entries",
      "todo_tasks",
      "todo_steps",
      "todo_step_windows",
      "record_audit_logs",
    ]) {
      if (!tables.includes(table)) throw new Error(`Restored database is missing ${table}.`);
    }
  } finally {
    restored.close();
  }
} finally {
  await rm(restoreDirectory, { recursive: true, force: true });
}

console.log(`Restore check passed: ${backupFile}`);
