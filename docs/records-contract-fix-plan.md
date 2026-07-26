# Records contract deployment fix

Status: in progress

## Scope

- Correct the production deployment check for the current overview contract.
- Restore the published migration history and add a forward migration for step windows.
- Deploy the existing server implementation before changing the Android client.
- Verify a cold app start can load Records without `No value for windows`.

## Steps

1. Validate record item `windows` and every task step's `windows` in the deploy health check.
2. Restore immutable migration `0009_todo_task_windows` and add `0010_todo_step_windows`.
3. Cover an upgrade from a database that already applied migration 0009.
4. Run server type checking, API tests, Web tests, and production build.
5. Commit the verified deployment change, deploy it, and verify the production schema and overview response.
6. Cold-start version `0.1.22` on the connected device and confirm Records loads.

## Acceptance

- `pnpm deploy` completes its current schema and overview contract checks.
- A database that already applied migration 0009 upgrades to the current schema.
- Production `/api/overview` returns a `windows` array for every record item and task step.
- App version `0.1.22` no longer displays `No value for windows` after a cold start.
