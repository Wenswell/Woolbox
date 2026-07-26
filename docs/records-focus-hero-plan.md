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
   `recordProgressColors`: name, `cumulative / goal unit`, reason, then a row of
   `[−] amount unit [+]` and a submit control. Amount seeds from `defaultValue`
   and steps by `stepValue` through `stepRecordValue`. Only submit writes.

3. **Subtask hero.** Same footprint using `SegmentedProgressFill` for the parent
   task's progress: subtask title, task title, reason, and a checkbox as the
   commit target. No stepper — a subtask is boolean.

4. **Queue rows.** Rows navigate: accumulate opens the record Sheet, subtask
   opens the task progress Sheet. Subtask rows carry a leading checkbox as an
   independent target. Deferral moves to the hero's overflow, keeping one primary
   and one secondary action per surface.

5. **Reason labels.** Drop `FocusReason.Queued`'s label; render nothing when a
   candidate has no informative reason. Keep the reason in the model, since it
   still drives ordering.

6. **Tests.** Amount stepping and submission are pure functions where possible;
   cover seed value, stepping bounds and that a mis-stepped amount never becomes
   an entry. Ranking tests stay as they are.

7. **Docs and version.** Rewrite the `Focus` rows in `docs/product.md` and
   `docs/design-system.md`, update `README.md`, bump `versionCode`/`versionName`
   and sync the README version row in the same commit.

8. **Verify.** `./gradlew :app:testDebugUnitTest :app:assembleDebug` and
   `git diff --check`. The server is untouched.

## Acceptance

- The hero shows the amount before it is written, and the amount can be changed
  without writing anything.
- Exactly one control per hero writes to the server; stepping and scrolling never
  produce an entry.
- No queue row writes on a body tap; subtask checkboxes are the only in-row
  commit target.
- The focus surface introduces no shape that the sections do not already use.
- Day state occupies one line and one rule.
- Android unit tests and `assembleDebug` pass; the working tree is clean.

## Device acceptance

Mis-tap resistance, the hero's weight against the sections, and whether the
stepper is comfortable one-handed cannot be judged by JVM tests. All three need
the connected device before publishing.
