# Records server deployment

The service runs as `ravvss`, listens on `127.0.0.1:4320`, and stores data outside the release directory.

| Path | Purpose |
| --- | --- |
| `/home/ravvss/records` | Application release and production dependencies. |
| `/srv/records/records.sqlite` | Live SQLite database. |
| `/srv/records/backups` | Fourteen daily consistent backups. |
| `~/.config/systemd/user/records.service` | Server unit. |
| `~/.config/systemd/user/records-backup.{service,timer}` | Backup units. |

The production runtime is the current Node 22 LTS installed at `~/.local/node`. This keeps native module installation independent from the host's non-LTS Node release.

## First-time setup

On the cloud server, create the data directories and install the user units:

```bash
ssh cloud '
  sudo install -d -o ravvss -g ravvss -m 750 /srv/records /srv/records/backups
  cd /home/ravvss/records
  PATH="$HOME/.local/node/bin:$HOME/.local/share/pnpm:$PATH" pnpm install --prod --frozen-lockfile
  install -m 644 deploy/records.service ~/.config/systemd/user/records.service
  install -m 644 deploy/records-backup.service ~/.config/systemd/user/records-backup.service
  install -m 644 deploy/records-backup.timer ~/.config/systemd/user/records-backup.timer
  systemctl --user daemon-reload
  systemctl --user enable --now records.service records-backup.timer
'
```

The Caddy route is maintained by `debian-cloud-server`. Install and validate that route before exposing the service.

## Release

From the app repository, run:

```bash
cd records-server
pnpm deploy
```

`pnpm deploy` runs type checking, API and Web tests, production build, release synchronization, production dependency installation, service restart, migration-schema verification, and a local API health check that waits up to 20 seconds. It targets the `cloud` SSH host and `/home/ravvss/records` release path.

Deploy a server contract change before distributing an Android APK that depends on it. The deployment check validates required schema objects, confirms removed scheduling fields and tables are absent, and verifies the task inventory plus `dailyPlan` overview contract.

## Verify

```bash
ssh cloud '
  systemctl --user is-active records.service records-backup.timer
  curl -fsS http://127.0.0.1:4320/api/items
  systemctl --user start records-backup.service
  latest=$(find /srv/records/backups -maxdepth 1 -name "records-*.sqlite" -print | sort | tail -1)
  cd /home/ravvss/records
  /home/ravvss/.local/node/bin/node scripts/restore-check.mjs -- "$latest"
'
```

The restore check copies the selected backup to a temporary directory, runs SQLite integrity validation, verifies the seven Records tables, and removes the temporary copy.
