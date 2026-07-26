# Records focus hero rebuild

Status: in progress

## Why

The focus card conflated recommendation with input. Recommending answers what to
do; input carries an amount. One button doing both loses the amount, because a
button can only express a single preset value.

Three consequences, all of them structural rather than tuning:

- **Amounts are unreachable.** An accumulate entry is a scalar; 1.2 L and 0.2 L
  are different facts. The card can only write `defaultValue`, so the one number
  that matters is the one the user cannot touch.
- **Taps are destructive.** A full-width transparent row that silently completes
  a subtask looks like a list item. Committing must be a small, deliberate target
  — the row body navigates, a control writes.
- **The card invented a visual language.** A ring, a timeline and colour blocks
  sit above liquid-fill cards and segmented bars. Two vocabularies in one screen,
  and three competing focal points inside the card, so nothing leads.

## Scope

Rebuild the focus surface from the page's existing material. Replace the card,
keep the ranking engine (`focusQueue`, reasons, habits, streaks, deferral) —
that model is sound and tested; only its presentation and its write path change.

## Decisions

| Question | Decision | Why |
| --- | --- | --- |
| Hero commit | Inline stepper plus a separate submit control | The amount is visible and adjustable before it is written. Mis-tapping `−`/`+` only changes a number; one target writes. Reuses `stepRecordValue` and the Sheet's existing controls, adding no concept |
| Hero vs sections | Standalone hero built from the same material | The hero is an enlarged liquid card (accumulate) or segmented bar (subtask), not a new shape. Sections stay; one duplicated card is cheaper than a second visual vocabulary |
| Day state | One text line plus a thin elapsed rule | A 44dp ring duplicates every card's own progress and only works at Fitness scale; 6dp timeline marks are unreadable. Streak stays as text on the same line |
| Queue rows | Navigate only | Rows open the item or task. Subtasks get a real checkbox as a separate small target, matching the Google Tasks split between checkbox and row body |
| Reason labels | Real information or nothing | `queued` tells the user nothing. Only `剩 20 分钟` / `平时这个点` / `落后` / `12 天连续` earn a label |

## Steps

1. **Day line.** Replace `DayRingBadge`, `DayTimeline` and `StreakBadge` with one
   `labelLarge` line (`EEE d MMM · HH:mm · 3/5 today`, streak appended when
   present) over a thin elapsed rule. Keep `dayRing` and `dailyGoalStreak` as
   the data behind it — only the drawing goes.

2. **Accumulate hero.** Full-width card reusing `LiquidProgressFill` and
   `recordProgressColors`: name, value, reason, then a row of `[−] amount unit
   [+]` and a submit control. The value is today's total for a daily goal or no
   goal, and the rolling 7-day total for a 7-day goal; show `/ goal` only when a
   goal exists. Amount seeds from `defaultValue` and steps by `stepValue` through
   `stepRecordValue`. Only a valid, idle submit writes. Disable all hero writes
   while its request is in flight; on failure keep the draft amount and show the
   server error, and on success reset it to the default.

3. **Subtask hero.** Same footprint using `SegmentedProgressFill` for the parent
   task's progress: subtask title, task title, reason, and a checkbox as the
   commit target. The checkbox is disabled while completion is in flight. No
   stepper — a subtask is boolean.

4. **Queue rows.** Rows navigate: accumulate opens the record Sheet, subtask
   opens the task progress Sheet. Subtask rows carry a leading checkbox as an
   independent target. Deferral moves to the hero's overflow, keeping one primary
   and one secondary action per surface. Show `Not now` only for a non-deferred
   hero; it keeps the existing same-day tail ordering and automatically expires
   on the next local date.

5. **Reason labels.** Drop `FocusReason.Queued`'s label; render nothing when a
   candidate has no informative reason. Keep the reason in the model, since it
   still drives ordering.

6. **Tests.** Keep amount stepping and submit eligibility pure where possible;
   cover default seeding, the positive step floor, invalid input, and that an
   amount change never writes. Cover an in-flight submit being accepted once,
   preserving its value on failure, and a successful submit resetting it to the
   default. Ranking tests stay as they are.

7. **Docs and version.** Rewrite the `Focus` rows in `docs/product.md` and
   `docs/design-system.md`, update `README.md`, bump `versionCode`/`versionName`
   and sync the README version row in the same commit.

8. **Verify and publish.** Run `:app:testDebugUnitTest`, `:app:assembleDebug`,
   `git diff --check`, and inspect the debug `output-metadata.json` for the
   bumped version. Install the debug APK on the connected device and complete the
   device acceptance below. Commit the finished change, then run
   `pnpm run publish:debug`; verify the remote debug `latest.json`, versioned APK
   `200`, and `content-length`. Record the manifest URL and size result in the
   README release row or commit message. The server is untouched.

## Acceptance

- The hero shows the amount before it is written, and the amount can be changed
  without writing anything.
- Exactly one control per hero writes to the server; stepping and scrolling never
  produce an entry.
- A submit remains single-shot until its server request completes; a failed
  request keeps its selected amount, while a successful one resets to the default.
- A no-goal accumulate hero shows today's total without a missing goal value; a
  daily and a 7-day goal use their respective accumulation periods.
- No queue row writes on a body tap; subtask checkboxes are the only in-row
  commit target.
- `Not now` appears only for a non-deferred hero and still expires at the next
  local date.
- The focus surface introduces no shape that the sections do not already use.
- Day state occupies one line and one rule.
- Android unit tests and `assembleDebug` pass; the working tree is clean.

## Device acceptance

On the connected debug build, adjust an accumulate value without submitting,
then submit it once and confirm the exact value appears in today's entries. Also
check a fast repeated tap during submission, a failed request, subtask checkbox
vs row-body navigation, deferral expiry after a date change, and one-handed
stepper reach. Confirm the hero remains visually subordinate to the three
sections. These checks are required before publishing.
