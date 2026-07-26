# Records focus queue completion

Status: done

## Why

Commit `14e0386` shipped the focus queue's ranking model but left three gaps
between what the design claims and what the code does.

The queue's reason ranking, habit learning, day ring and streaks are in place
and tested. What is missing is everything that makes the queue behave like a
queue: the promotion it describes is not animated, a lead entry that cannot be
done right now has no exit, and the minute tick now recomposes the whole root
page instead of the card alone.

## Scope

- Restore per-minute recomposition to the focus card alone.
- Animate promotion so acting on the lead visibly advances the queue.
- Give the lead entry a same-day dismissal, so being unable to act is not a
  dead end.
- Keep the root page shape: focus card as the header, sections directly after.

Out of scope, and deliberately so: points, levels and any stored score. A score
derived from a sliding history window falls as history ages, which inverts the
feedback it exists to give. The day ring and streaks already carry the
gamification load without that defect.

## Decisions

| Question | Decision | Why |
| --- | --- | --- |
| Lead entry cannot be done now | Push to the back of today's queue, in memory | Costs one gesture and no schema. A dismissal that outlives the day would need to be revisited, stored and audited, and nothing yet shows that it should |
| Card footprint | Header, sections immediately after | One screen answers both "what now" and "where am I", which a full-screen card cannot |
| Items with no time signal but due today | Stay queued, labelled `queued` | A today subtask is a commitment, not a filler. Clearing them is what closes the ring, so they cannot be outside the queue the ring measures |

## Steps

1. **Confine the clock to the focus card.** Move the queue, ring and window-mark
   computation out of `RecordsScreen`'s body into `RecordsFocusCard`, so the
   minute tick recomposes the card and not the `LazyColumn` content lambda. Pass
   the snapshot-derived inputs; read `clock.value` in one place. Regression:
   commits `09ff3c8`/`3fe12df` exist to keep per-frame work off this list, and
   `14e0386` undid part of that.

2. **Animate promotion.** Give the lead action and the queue rows a keyed
   `AnimatedContent`/`animateItem` transition so acting on the lead slides the
   next entry up rather than swapping text in place. Suppress it under
   `prefersReducedMotion` and while the root list scrolls, matching the existing
   `LocalPauseDecorativeMotion` contract.

3. **Same-day dismissal.** Add a secondary action to the lead block that sends
   that target to the back of today's queue. Hold dismissed target keys in the
   ViewModel keyed by local date, so a date change clears them without extra
   bookkeeping. A dismissed entry stays visible at the tail with its reason
   intact — it is deferred, not hidden.

4. **Tests.** Pure-Kotlin coverage for dismissal ordering (dismissed entries sort
   last regardless of reason strength), for date-change clearing, and for the
   queue surviving a dismissal of every entry. Compose behaviour stays out of
   unit tests, as elsewhere in this project.

5. **Docs and version.** Update `docs/product.md`, `docs/design-system.md` and
   `README.md` for the dismissal gesture and promotion motion; bump
   `versionCode`/`versionName` and sync the README version row in the same
   commit.

6. **Verify.** `./gradlew :app:testDebugUnitTest :app:assembleDebug` and
   `git diff --check`. The server is untouched, so its checks are not re-run.

## Acceptance

- A minute tick recomposes the focus card only; the sections and their
  decorative motion are unaffected.
- Acting on the lead entry animates the next entry into its place; reduced
  motion and active scrolling both fall back to an instant swap.
- Dismissing the lead entry moves it to the queue's tail and promotes the next;
  it returns to normal ranking the following local day.
- Dismissing every entry leaves the queue populated in dismissal order, never
  empty while commitments remain.
- Android unit tests and `assembleDebug` pass; the working tree is clean.

## Device acceptance

Promotion motion and the dismissal gesture cannot be judged by JVM tests. Both
need a look on the connected device before publishing.
