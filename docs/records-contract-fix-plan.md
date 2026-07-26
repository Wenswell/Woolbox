# Records contract deployment fix

Status: in progress

## Scope

- Correct the production deployment check for the current overview contract.
- Deploy the existing server implementation before changing the Android client.
- Verify a cold app start can load Records without `No value for windows`.

## Steps

1. Validate record item `windows` and every task step's `windows` in the deploy health check.
2. Run server type checking, API tests, Web tests, and production build.
3. Commit the verified deployment change, deploy it, and verify the production schema and overview response.
4. Cold-start version `0.1.22` on the connected device and confirm Records loads.

## Acceptance

- `pnpm deploy` completes its current schema and overview contract checks.
- Production `/api/overview` returns a `windows` array for every record item and task step.
- App version `0.1.22` no longer displays `No value for windows` after a cold start.
