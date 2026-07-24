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
/home/ravvss/.local/node/bin/node - <<'NODE'
const Database = require("better-sqlite3");
const database = new Database("/srv/records/records.sqlite", { readonly: true });
const boundColumns = database.prepare("SELECT name FROM pragma_table_info('record_items') WHERE name IN ('min_value', 'max_value') ORDER BY name").all();
if (boundColumns.length !== 2) throw new Error("Measurement bounds migration is missing.");
const positionColumn = database.prepare("SELECT name FROM pragma_table_info('todo_steps') WHERE name = 'position'").get();
if (!positionColumn) throw new Error("Todo step position migration is missing.");
const horizonColumn = database.prepare("SELECT name FROM pragma_table_info('todo_tasks') WHERE name = 'horizon'").get();
if (!horizonColumn) throw new Error("Todo task horizon migration is missing.");
console.log("Database schema verified.");
NODE
for attempt in {1..20}; do
  if curl -fsS http://127.0.0.1:4320/api/items > /dev/null; then
    echo "Records deployment verified."
    exit 0
  fi
  sleep 1
done
echo "Records API did not become ready within 20 seconds." >&2
exit 1
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
