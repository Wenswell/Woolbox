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
if (!Array.isArray(overview.items) || !Array.isArray(overview.tasks) || overview.dailyPlan !== null) process.exit(1);
if (!overview.tasks.every(
  (task) =>
    task.plannedDate === undefined
    && Array.isArray(task.steps)
    && task.steps.every((step) => typeof step.title === "string"),
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
const positionColumn = database.prepare("SELECT name FROM pragma_table_info('todo_steps') WHERE name = 'position'").get();
if (!positionColumn) throw new Error("Todo step position migration is missing.");
const plannedDateColumn = database.prepare("SELECT name FROM pragma_table_info('todo_tasks') WHERE name = 'planned_date'").get();
if (plannedDateColumn) throw new Error("Removed todo task planned date still exists.");
const repeatModeColumn = database.prepare("SELECT name FROM pragma_table_info('todo_tasks') WHERE name = 'repeat_mode'").get();
if (!repeatModeColumn) throw new Error("Todo task repeat mode migration is missing.");
const removedCompletionColumns = database.prepare(
  "SELECT m.name AS table_name, p.name AS column_name " +
  "FROM sqlite_master AS m " +
  "JOIN pragma_table_info(m.name) AS p " +
  "WHERE m.name IN ('todo_tasks', 'todo_steps') AND p.name = 'completed_at'"
).all();
if (removedCompletionColumns.length !== 0) throw new Error("Removed todo completion columns still exist.");
const completionsTable = database.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'todo_step_completions'").get();
if (!completionsTable) throw new Error("Todo step completion migration is missing.");
const dailyPlansTable = database.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'daily_plans'").get();
if (!dailyPlansTable) throw new Error("Daily plan migration is missing.");
const removedWindowTables = database.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('record_windows', 'todo_step_windows')").all();
if (removedWindowTables.length !== 0) throw new Error("Removed scheduling tables still exist.");
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
