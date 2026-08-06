# Records scheduling simplification

Status: in progress

## Goal

Use one scheduling concept for tasks: an optional planned date. Remove manual
time windows from tasks, subtasks and accumulate items. Keep actual timestamps
for entries and completions because they describe events rather than plans.

## Decisions

| Concern | Decision |
| --- | --- |
| Task schedule | One optional `plannedDate`; new tasks default to unscheduled. |
| Date meaning | The local day the user intends to work on the task. |
| Date UI | One date row opens the picker; a separate clear action removes it. |
| Task groups | `Overdue`, `Today`, `Planned`, `Unscheduled`, `Completed`. |
| Focus task target | One candidate per task: its first unfinished subtask. |
| Missed tasks | Overdue tasks remain in Focus until completed or rescheduled. |
| Manual times | Delete record and subtask window schema, API fields and UI. |
| Focus timing | Daily goals, learned record habits and task dates drive ranking. |
| Existing windows | Destructively remove them; a range cannot be converted to one truthful date or time. |

## Scope

1. Add a forward server migration that drops `record_windows` and
   `todo_step_windows`; remove their schema, API, validation and audit fields.
2. Remove `RecordWindow` from Android models, repository payloads, forms and
   Focus rules.
3. Split future-dated and undated tasks in the domain model and task lists.
4. Replace Today/Later chips plus date button with one optional date control.
5. Rank each overdue or today task once in Focus, using its first unfinished
   subtask; overdue sorts ahead of today.
6. Update targeted server and Android tests, then synchronize the durable
   product, architecture, design and development documents.
7. Increment the Android version, deploy the changed server contract, publish
   the debug APK and verify both remote results.

## Non-goals

- Reminders, notifications, recurring tasks, deadlines or calendar views.
- Subtask dates, task start/end times, day-part presets or automatic rescheduling.
- Changing event timestamps, measurement history or completion timestamps.
- Compatibility fields, dual-read contracts or inferred migration values.

## Acceptance

- No task, subtask or accumulate form exposes a manual time-window control.
- A task has exactly one optional date input and new tasks start unscheduled.
- Future-dated and undated tasks never share one `Later` label or bucket.
- Each overdue or today task contributes at most one Focus candidate and shows
  its first unfinished subtask; completing it advances to the next.
- Record habits remain learned from entry timestamps without manual windows.
- The production server is deployed before the new APK is published.
- Server checks, Android unit tests, debug assembly and `git diff --check` pass.

## Closeout

Move lasting rules into the topic documents and delete this file after the
implementation, deployment and publication are verified.
