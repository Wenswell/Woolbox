# Records home redesign

Status: ready for implementation

## Objective

Make the Records root page answer three questions in order:

1. What should I do now?
2. What else needs attention today?
3. What can I inspect or record next?

The redesign removes structural noise instead of adding decoration. Queue rules,
record calculations, writes, navigation destinations and server contracts remain
unchanged.

## Confirmed decisions

- Root-page copy stays English; user-entered names and units remain unchanged.
- `Later` becomes one compact summary row that opens Manage tasks.
- `Completed` is absent from the root page and remains available in Manage tasks.
- Root section order remains Focus, Tasks, Accumulate, Measurement.
- The bottom navigation, Manage pages, forms and Sheets are outside this slice.

## Design rationale

- Primary job: identify and execute the highest-priority action with minimal scanning.
- Screenshot evidence: empty task structure and context-free queue rows consume more
  space than actionable content; this is the highest-probability source of clutter.
- Smallest sufficient change: reorganize existing content and remove low-value root
  states. Do not add tabs, filters, configuration, data fields or dependencies.
- Use progressive disclosure: current work stays on the root page; future and
  historical work moves behind existing Manage routes.

## Information architecture

### Focus

- Use a compact status row: localized date on the left; current time and
  `closed/total today` on the right. Keep the elapsed-day rule directly below it.
- Keep exactly one Hero as the page's strongest surface. Preserve liquid/segmented
  progress, `Not now`, stepper, submit and task-completion behavior.
- Reduce competing emphasis: neutral text hierarchy, one progress accent and an
  `8dp` Records-specific card shape.
- When the queue has remaining items, render one `Up next` label followed by at
  most three compact rows in a single list group.
- Accumulate row: familiar record icon, item name, current value/unit or effective
  reason as supporting text, and trailing chevron. Whole row opens its Sheet.
- Task row: checkbox, step title, parent task or effective reason as supporting
  text, and trailing chevron. Checkbox writes; row body opens task progress.
- Keep queued items visible even when they have no recommendation reason; type and
  supporting context must prevent bare-name rows.

### Tasks

- Keep one root `Tasks` section header whose row opens Manage tasks.
- Replace the settings gear with a trailing chevron; use the same header treatment
  for Accumulate and Measurement.
- Render `Overdue` only when non-empty, followed by its existing preview cards.
- Render `Today` only when non-empty, followed by its existing preview cards.
- Remove `Today (0)` and `No today tasks.` from the root page.
- Render one compact `Later` summary row when the count is non-zero; tapping it opens
  Manage tasks. It does not expand on the root page.
- Do not calculate or render the root-page Completed group.
- If there are no Overdue, Today or Later tasks, show one compact neutral
  `No active tasks` row so the section and Manage entry remain discoverable.

### Trackers

- Preserve the existing two-column Accumulate and Measurement card data and actions.
- Use neutral section titles instead of primary-colored headings.
- Keep progress color inside the cards; section chrome and Manage actions stay
  neutral so progress regains semantic emphasis.
- Apply the same `8dp` Records-specific card shape and tighter internal hierarchy to
  task, accumulate and measurement cards without changing their stable dimensions.

## Layout rules

- Keep `16dp` page margins and the fixed section order.
- Use spacing to express hierarchy: tight inside a group, larger only between
  Focus, Tasks, Accumulate and Measurement.
- Do not use blank rows as separators. Use list grouping, dividers or section space.
- Do not introduce cards inside cards. Hero, Up next group and repeated tracker
  cards are siblings.
- Keep all compact icon targets at least `48dp`; visible icons may remain `18-24dp`.
- Initial 1080x2400 viewport should show Focus, the compact Tasks state and a clear
  hint of Accumulate content with the current test data.

## Implementation slices

1. Update `RecordsFocusCard.kt`: status row, Hero hierarchy and grouped `Up next`
   rows while preserving callbacks and queue limits.
2. Update `RecordsScreen.kt`: conditional task groups, Later summary, remove root
   Completed content and unify section headers.
3. Update Records cards in `RecordsScreen.kt` and `RecordGoalCards.kt` only where
   required for the neutral hierarchy and Records-specific shape.
4. Update `docs/product.md` and `docs/design-system.md` as the final behavior and
   visual sources of truth; remove superseded root-page rules.
5. Increment the app version, run focused checks, then run the repository's full
   debug verification once before commit and publication.

## Acceptance

- The root page has no `Today (0)`, `No today tasks.` or Completed group.
- Later appears once as a compact Manage entry and never expands in place.
- Queue items are visually grouped and identifiable as task or accumulate content;
  no context-free bare text rows remain.
- Focus Hero actions, task checkbox writes, queue navigation and `Not now` retain
  their current behavior.
- Overdue and Today task ordering, preview limits and progress calculations remain
  unchanged when those groups contain data.
- Accumulate and Measurement data, liquid progress, Sheets and Manage routes remain
  unchanged.
- Light and dark screenshots at 1080x2400 show a clear primary action, consistent
  alignment, no text overlap and a visible transition into Accumulate content.
- `:app:testDebugUnitTest`, `:app:assembleDebug`, lint publication checks and
  `git diff --check` pass; debug APK metadata and remote publication are verified.

## Out of scope

- Queue ranking, recommendation reasons, day-ring calculations or task status rules.
- New navigation destinations, tabs, filters, widgets or customization.
- Bottom navigation redesign or app-wide typography/theme changes.
- Server, database, API, Manage page, form or Sheet changes.
