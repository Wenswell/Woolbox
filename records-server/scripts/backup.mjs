#!/usr/bin/env node

import Database from "better-sqlite3";
import { mkdir, readdir, rm } from "node:fs/promises";
import { join } from "node:path";

const databaseFile = "/srv/records/records.sqlite";
const backupDirectory = "/srv/records/backups";
const retainedBackups = 14;
const timestamp = new Date().toISOString().replaceAll(":", "-").replace(".000Z", "Z");
const backupFile = join(backupDirectory, `records-${timestamp}.sqlite`);

await mkdir(backupDirectory, { recursive: true });

const source = new Database(databaseFile, { readonly: true, fileMustExist: true });
try {
  await source.backup(backupFile);
} finally {
  source.close();
}

const backup = new Database(backupFile, { readonly: true, fileMustExist: true });
try {
  const result = backup.pragma("integrity_check", { simple: true });
  if (result !== "ok") throw new Error(`Backup integrity check failed: ${String(result)}`);
} finally {
  backup.close();
}

const backups = (await readdir(backupDirectory))
  .filter((name) => /^records-\d{4}-\d{2}-\d{2}T.*Z\.sqlite$/.test(name))
  .sort()
  .reverse();
await Promise.all(backups.slice(retainedBackups).map((name) => rm(join(backupDirectory, name))));

console.log(backupFile);
