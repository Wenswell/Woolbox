# Records home widgets

Status: planned

## Goal

Add four configurable Android home-screen widgets for Records: `Focus`,
`Today`, `Trackers`, and `Quick add`. Each widget must remain useful after the
app process is removed, adapt its information hierarchy to the space supplied
by the launcher, and preserve the server as the only business source of truth.

Completion means all four providers are published in the system widget picker,
independent instances retain their own content and style configuration, reads
and writes use the existing Records rules, and the debug APK is published and
verified through the existing update channel.

## Scope

| Widget | Default / minimum | Content and action |
| --- | --- | --- |
| `Focus` | `2x2` / `2x2` | Current focus target, reason and progress; larger sizes add up to three queued targets. The lead task can complete and an eligible accumulate can record its default value. |
| `Today` | `4x2` / `2x2` | Today's selected tasks and closure progress; a taller layout becomes a checklist whose steps can complete directly. |
| `Trackers` | `4x2` / `2x2` | Selected and ordered accumulate/measurement values. Tapping an item opens its existing app surface; this widget does not create entries. |
| `Quick add` | `2x1` / `2x1` | One selected accumulate with a configured default value; one explicit action records that value. |

All providers open the same configuration Activity when added and remain
reconfigurable from the launcher. The form starts with valid defaults, but the
user always confirms the instance before it is created.

Configuration is deliberately bounded:

- content selection and order, plus `All` / `Tasks` / `Accumulates` for Focus;
- show completed items, header and last-updated time where relevant;
- palette: `Dynamic`, `Woolbox`, or `Monochrome`;
- appearance: `Follow system`, `Light`, or `Dark`;
- surface: `Filled` or `Tonal`; density: `Compact` or `Comfortable`.

## Non-goals

- No universal block dashboard, arbitrary per-breakpoint layout editor, custom
  fonts, arbitrary colors, opacity control, animation or chart rendering.
- No lock-screen widgets, exact alarms, push service, offline write queue,
  app-owned widget gallery, legacy `RemoteViews` implementation, or compatibility
  implementation beside Glance.
- No Global Copy or OLED widget. Starting either from the launcher loses the
  intended external foreground target; Quick Settings remains their shortcut.
- No reuse of application Compose UI. Glance has its own static, remote layout
  and reuses domain models, rules, labels, colors and vector resources only.

## Decisions

| Concern | Decision |
| --- | --- |
| UI | Jetpack Glance with Material 3 `1.1.1` and WorkManager `2.11.2`. Use one shared widget shell and provider-specific content; do not adopt prerelease Glance APIs. |
| Sizing | `SizeMode.Responsive` with three canonical layouts per provider at most. Branch on actual `DpSize`, not launcher cell count. |
| Persistent read model | A small Room database under `record/widget/` stores one validated overview response with local date and sync time, plus widget instance configuration and ordered selections. It is a projection, not an alternate Records source. |
| State | Glance state stores only transient presentation state such as `submitting`; business content and configuration use the shared Room read model. |
| Sync | Set `updatePeriodMillis=0`. One unique WorkManager job refreshes every 30 minutes while any Records widget exists. Configuration save, successful app refresh/write, widget write, manual refresh, and system date/time/time-zone changes request immediate refresh. |
| Failure | Never silently substitute invented data. With a prior projection, display it with its actual update time and a visible sync error; without one, display the concrete connection or refresh action. Writes are never queued offline. |
| Navigation | Explicit MainActivity intent extras open the existing Records root, task progress, accumulate record, or measurement preview surface. The same contract handles cold and existing Activity instances. |
| Writes | Task completion keeps the existing idempotent `PUT`. Accumulate creation first gains a server-side operation ID with a uniqueness constraint; only then may Focus and Quick add expose direct entry creation. |

The overview response is persisted only after it parses and passes existing
domain validation. Extract the current overview loading/parsing and credential
access from `RecordRepository` so the app repository and widget worker call one
implementation. Do not duplicate focus ranking, goal, streak or value rules.

## Size contract

| Available size | Required layout |
| --- | --- |
| about `120x48dp` | Quick add single row only: identity, value and one `48dp` action target. |
| about `120x115dp` | One primary record or task; omit header and update time before truncating the primary value. |
| about `256x115dp` | Header and two or three concise rows, or a two-column tracker summary. |
| width `>=256dp`, height `>=185dp` | Header, status and scrollable checklist/list. Width adds metadata; height adds rows. |

Text remains static, capped by explicit line counts and ellipsis. Use Glance
checkboxes and determinate linear progress; application liquid waves, task
stripes, Vico charts and transition motion have no widget equivalent.

## Delivery slices

Each slice ends with targeted tests, the repository's standard Android checks,
documentation synchronization and one local commit.

1. **Foundation plus Focus.** Add stable Glance/Material 3, WorkManager and
   Glance testing dependencies; bump the app version. Extract the shared
   overview client/codec and credential store, add the widget Room read model,
   implement synchronization, configuration, previews, deep-link contract and
   the read-only Focus presentation. Verify process removal, resize and
   reconfiguration before proceeding.
2. **Today and Trackers.** Add both providers on the shared shell. Implement
   ordered selection, Today completion, item navigation and explicit empty,
   connection, syncing and error states. No new data or scheduling layer.
3. **Idempotent accumulate writes and Quick add.** Add `operationId` to the
   entry-create contract and a server uniqueness constraint; repeated requests
   return the original entry. Cover migration and API behavior, then enable the
   shared widget entry action in Focus and Quick add. No direct accumulate
   action is rendered before this contract exists.
4. **Completion.** Verify all providers together, remove duplicated widget-only
   code, publish the debug APK, verify the remote manifest and APK, move durable
   rules into the topic documents, and delete this plan.

## Tests and acceptance

- Pure JVM tests cover size selection, configuration validation, ordered
  selections, overview persistence/validation, focus projection and action
  parameters. Server tests prove duplicate operation IDs create one entry.
- Glance tests cover each provider's empty, content, syncing and error semantics
  at its canonical sizes. They do not attempt pixel-perfect application UI.
- Android 12 and the connected current device show four correctly labelled
  picker entries with previews; add, cancel, reconfigure, resize and delete all
  preserve or remove only the addressed instance.
- Two instances of the same provider keep independent content and style.
  Killing the app process and restarting the launcher preserves the last
  validated projection and its update time.
- App refreshes and mutations update affected widgets. Periodic and manual
  refreshes do not overlap. Loss of network is visible and never creates a
  local mutation.
- A task completion and accumulate quick action each change server state once,
  disable duplicate submission while active, and refresh every affected widget.
- Widget taps open the intended existing Records surface whether MainActivity
  is cold, backgrounded or already resumed.
- `:app:testDebugUnitTest`, `:app:assembleDebug`, server checks when its contract
  changes, and `git diff --check` pass. Final debug publishing satisfies the
  repository's manifest, HTTP status and APK size checks.

## Documentation closeout

Before implementation, this file is the scope and sequencing authority. During
completion, move user-visible behavior to `product.md`, data and worker contracts
to `architecture.md`, widget tokens and breakpoints to `design-system.md`, and
source paths, tests and device checks to `development.md`. Update README only
after the widgets are shipped, then delete this temporary plan.
