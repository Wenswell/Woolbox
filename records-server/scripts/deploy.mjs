import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const remoteTarget = "cloud";
const releasePath = "/home/ravvss/records";

function run(command, argumentsList) {
  const result = spawnSync(command, argumentsList, {
    cwd: projectRoot,
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

const remoteScript = `set -euo pipefail
cd ${releasePath}
PATH="$HOME/.local/node/bin:$HOME/.local/share/pnpm:$PATH" pnpm install --prod --frozen-lockfile
systemctl --user daemon-reload
systemctl --user restart records.service
systemctl --user is-active records.service
for attempt in {1..20}; do
  if /home/ravvss/.local/node/bin/node - <<'NODE'
const response = await fetch("http://127.0.0.1:4320/api/overview?startDate=2000-01-01&endDate=2000-01-01&planDate=2000-01-01").catch(() => null);
if (!response?.ok) process.exit(1);
const overview = await response.json();
if (
  !Array.isArray(overview.items)
  || !Array.isArray(overview.goals)
  || !Array.isArray(overview.templates)
  || !Array.isArray(overview.todayTasks)
  || !Array.isArray(overview.dailySuggestions)
  || !Array.isArray(overview.unfinishedTasks)
  || !Object.hasOwn(overview, "dailyCheckin")
) process.exit(1);
if (!overview.templates.every(
  (template) =>
    template.archived === false
    && ["MANUAL", "DAILY"].includes(template.cadence)
    && Array.isArray(template.steps)
    && template.steps.every((step) => typeof step.title === "string"),
)) process.exit(1);
NODE
  then
    break
  fi
  if [[ $attempt -eq 20 ]]; then
    echo "Records API did not become ready within 20 seconds." >&2
    exit 1
  fi
  sleep 1
done
/home/ravvss/.local/node/bin/node - <<'NODE'
const Database = require("better-sqlite3");
const database = new Database("/srv/records/records.sqlite", { readonly: true });
const boundColumns = database.prepare("SELECT name FROM pragma_table_info('record_items') WHERE name IN ('min_value', 'max_value') ORDER BY name").all();
if (boundColumns.length !== 2) throw new Error("Measurement bounds migration is missing.");
const tables = database.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").all().map(({ name }) => name);
for (const table of [
  "record_items",
  "record_entries",
  "goals",
  "goal_subgoals",
  "goal_entries",
  "task_templates",
  "task_template_steps",
  "today_tasks",
  "today_task_steps",
  "daily_checkins",
  "hourly_checkins",
  "record_audit_logs",
]) {
  if (!tables.includes(table)) throw new Error(\`Database is missing \${table}.\`);
}
for (const table of ["todo_tasks", "todo_steps", "todo_step_completions", "record_windows", "todo_step_windows", "daily_plans", "today_commitments", "today_commitment_steps", "daily_focus"]) {
  if (tables.includes(table)) throw new Error(\`Removed table still exists: \${table}.\`);
}
console.log("Database schema verified.");
NODE
echo "Records deployment verified."
`;

run("pnpm", ["typecheck"]);
run("pnpm", ["test"]);
run("pnpm", ["test:web"]);
run("pnpm", ["build"]);
run("rsync", [
  "-a",
  "--delete",
  "--exclude",
  "node_modules",
  "--exclude",
  "*.sqlite*",
  "./",
  `${remoteTarget}:${releasePath}/`,
]);
run("ssh", [
  remoteTarget,
  `echo ${Buffer.from(remoteScript).toString("base64")} | base64 --decode | bash`,
]);
