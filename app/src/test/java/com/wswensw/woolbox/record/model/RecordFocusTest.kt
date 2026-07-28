package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordFocusTest {
    private val today: LocalDate = LocalDate.of(2026, 7, 26)

    @Test
    fun openWindowLeadsAndClosesSoonestFirst() {
        val closingSoon = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val closingLater = accumulate(id = 2, name = "Walk", windows = listOf(8 * 60 to 12 * 60))

        val queue = focusQueue(
            items = listOf(closingLater, closingSoon),
            tasks = emptyList(),
            clock = clock(9 * 60),
        )

        assertEquals(listOf(1L, 2L), queue.map { itemId(it) })
        assertEquals(FocusReason.DueNow(60), queue.first().reason)
        assertEquals(8 * 60, queue.first().window?.startMinute)
    }

    @Test
    fun dueNowOutranksEveryOtherReason() {
        val due = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val habitual = accumulate(id = 2, name = "Walk", windows = emptyList())

        val queue = focusQueue(
            items = listOf(habitual, due),
            tasks = emptyList(),
            clock = clock(9 * 60),
            habitMinutes = mapOf(2L to 9 * 60),
        )

        assertEquals(1L, itemId(queue.first()))
        assertTrue(queue.first().reason is FocusReason.DueNow)
        assertTrue(queue[1].reason is FocusReason.Habit)
    }

    @Test
    fun windowsAlreadyRecordedTodayStopLeading() {
        val item = accumulate(
            id = 1,
            name = "Water",
            windows = listOf(8 * 60 to 10 * 60, 12 * 60 to 14 * 60),
        )
        val recorded = mapOf(1L to listOf(9 * 60 + 5))

        // The window it was recorded in is done, and nothing else claims 09:30.
        assertTrue(
            focusQueue(
                items = listOf(item),
                tasks = emptyList(),
                clock = clock(9 * 60 + 30),
                todayMinutes = recorded,
            ).isEmpty(),
        )
        // A different window of the same item still stands on its own.
        assertEquals(
            listOf(1L),
            focusQueue(
                items = listOf(item),
                tasks = emptyList(),
                clock = clock(13 * 60),
                todayMinutes = recorded,
            ).map { itemId(it) },
        )
    }

    @Test
    fun windowsSpanMidnight() {
        val overnight = accumulate(id = 1, name = "Sleep", windows = listOf(23 * 60 to 2 * 60))

        val queue = focusQueue(listOf(overnight), emptyList(), clock(30))

        assertEquals(FocusReason.DueNow(90), queue.single().reason)
    }

    @Test
    fun habitTimeLeadsNearThatTimeAndNotOutsideIt() {
        val item = accumulate(id = 1, name = "Walk", windows = emptyList())
        val habits = mapOf(1L to 9 * 60)

        assertEquals(
            FocusReason.Habit(habitMinute = 9 * 60, distanceMinutes = 20),
            focusQueue(listOf(item), emptyList(), clock(9 * 60 + 20), habitMinutes = habits)
                .single().reason,
        )
        // Beyond reach the item has no claim on now, so it leaves the queue.
        assertTrue(
            focusQueue(listOf(item), emptyList(), clock(13 * 60), habitMinutes = habits).isEmpty(),
        )
    }

    @Test
    fun habitStopsMatteringOnceTodayHasAnEntry() {
        val item = accumulate(id = 1, name = "Walk", windows = emptyList())

        assertTrue(
            focusQueue(
                items = listOf(item),
                tasks = emptyList(),
                clock = clock(9 * 60),
                todayMinutes = mapOf(1L to listOf(8 * 60)),
                habitMinutes = mapOf(1L to 9 * 60),
            ).isEmpty(),
        )
    }

    @Test
    fun streakRiskOnlyAppliesLateInTheDayAndWithARunToLose() {
        val item = accumulate(id = 1, name = "Walk", windows = emptyList())
        val streaks = mapOf(1L to 12)

        assertTrue(focusQueue(listOf(item), emptyList(), clock(9 * 60), itemStreaks = streaks).isEmpty())
        assertEquals(
            FocusReason.StreakRisk(12),
            focusQueue(listOf(item), emptyList(), clock(18 * 60), itemStreaks = streaks).single().reason,
        )
        // A one-day run is not yet a streak worth protecting.
        assertTrue(
            focusQueue(listOf(item), emptyList(), clock(18 * 60), itemStreaks = mapOf(1L to 1)).isEmpty(),
        )
    }

    @Test
    fun longerStreaksAreProtectedFirst() {
        val short = accumulate(id = 1, name = "Walk", windows = emptyList())
        val long = accumulate(id = 2, name = "Read", windows = emptyList())

        val queue = focusQueue(
            items = listOf(short, long),
            tasks = emptyList(),
            clock = clock(18 * 60),
            itemStreaks = mapOf(1L to 3, 2L to 20),
        )

        assertEquals(listOf(2L, 1L), queue.map { itemId(it) })
    }

    @Test
    fun behindPaceUsesTheElapsedShareOfTheDay() {
        val item = dailyGoal(id = 1, name = "Water", goal = 8.0)

        // Half the day gone with an eighth done is behind; the percent is of goal.
        assertEquals(
            FocusReason.BehindPace(13),
            focusQueue(
                items = listOf(item),
                tasks = emptyList(),
                clock = clock(12 * 60),
                goalValues = mapOf(1L to 1.0),
            ).single().reason,
        )
        // Exactly one quarter of the goal behind pace is inside the tolerance.
        assertEquals(
            FocusReason.Queued,
            focusQueue(
                items = listOf(item),
                tasks = emptyList(),
                clock = clock(12 * 60),
                goalValues = mapOf(1L to 2.0),
            ).single().reason,
        )
        // Keeping pace is still queued, just without the behind reason.
        assertEquals(
            FocusReason.Queued,
            focusQueue(
                items = listOf(item),
                tasks = emptyList(),
                clock = clock(12 * 60),
                goalValues = mapOf(1L to 4.0),
            ).single().reason,
        )
    }

    @Test
    fun earlyMorningIsNeverBehind() {
        val item = dailyGoal(id = 1, name = "Water", goal = 8.0)

        assertEquals(
            FocusReason.Queued,
            focusQueue(listOf(item), emptyList(), clock(60), goalValues = mapOf(1L to 0.0))
                .single().reason,
        )
    }

    @Test
    fun satisfiedGoalsAndMeasurementsLeaveTheQueue() {
        val reached = dailyGoal(id = 1, name = "Water", goal = 8.0)
        val measurement = accumulate(id = 2, name = "Weight", windows = listOf(8 * 60 to 10 * 60))
            .copy(mode = RecordMode.MEASUREMENT)

        assertTrue(
            focusQueue(
                items = listOf(reached, measurement),
                tasks = emptyList(),
                clock = clock(9 * 60),
                goalValues = mapOf(1L to 9.0),
            ).isEmpty(),
        )
    }

    @Test
    fun goallessItemsWithoutAnyTimeSignalStayOut() {
        // No goal, no window, no habit: nothing says to do this right now.
        val item = accumulate(id = 1, name = "Walk", windows = emptyList())

        assertTrue(focusQueue(listOf(item), emptyList(), clock(9 * 60)).isEmpty())
    }

    @Test
    fun scheduledSubtaskLeadsInItsOwnWindowRegardlessOfPosition() {
        val scheduled = task(
            id = 5,
            title = "Morning routine",
            stepTitles = listOf("Stretch", "Shower"),
            stepWindows = listOf(emptyList(), listOf(8 * 60 to 9 * 60)),
        )

        val queue = focusQueue(emptyList(), listOf(scheduled), clock(8 * 60 + 30))

        val lead = queue.first().target as NowTarget.Task
        assertEquals("Shower", lead.step.title)
        assertEquals(FocusReason.DueNow(30), queue.first().reason)
        // The unscheduled subtask of a today task is still today's work.
        assertEquals(FocusReason.Queued, queue[1].reason)
    }

    @Test
    fun eachScheduledSubtaskAppearsAtItsOwnTime() {
        val scheduled = task(
            id = 5,
            stepTitles = listOf("Morning pill", "Evening pill"),
            stepWindows = listOf(listOf(8 * 60 to 9 * 60), listOf(20 * 60 to 21 * 60)),
        )

        assertEquals(
            "Morning pill",
            stepTitle(focusQueue(emptyList(), listOf(scheduled), clock(8 * 60 + 30)).single()),
        )
        assertEquals(
            "Evening pill",
            stepTitle(focusQueue(emptyList(), listOf(scheduled), clock(20 * 60 + 30)).single()),
        )
    }

    @Test
    fun completedSubtasksAndOutOfWindowLaterTasksStayOut() {
        val done = task(
            id = 5,
            stepTitles = listOf("Stretch"),
            stepWindows = listOf(listOf(8 * 60 to 9 * 60)),
            completedStepCount = 1,
        )
        val later = task(id = 6, stepTitles = listOf("Someday"), plannedDate = null)

        assertTrue(focusQueue(emptyList(), listOf(done, later), clock(8 * 60 + 30)).isEmpty())
    }

    @Test
    fun scheduledSubtaskOutsideItsWindowIsNotTodayGeneralWork() {
        val scheduled = task(
            id = 5,
            stepTitles = listOf("Evening pill"),
            stepWindows = listOf(listOf(20 * 60 to 21 * 60)),
        )

        assertTrue(focusQueue(emptyList(), listOf(scheduled), clock(9 * 60)).isEmpty())
    }

    @Test
    fun taskWindowsAreAnchoredToTheirPlannedDate() {
        val scheduled = task(
            id = 5,
            stepWindows = listOf(listOf(8 * 60 to 9 * 60)),
        )

        assertTrue(
            focusQueue(
                items = emptyList(),
                tasks = listOf(scheduled),
                clock = clock(8 * 60 + 30, today.plusDays(1)),
            ).isEmpty(),
        )
    }

    @Test
    fun overnightTaskWindowsCarryOnlyIntoTheNextDay() {
        val overnight = task(
            id = 5,
            stepWindows = listOf(listOf(23 * 60 to 60)),
        )

        assertEquals(
            FocusReason.DueNow(30),
            focusQueue(emptyList(), listOf(overnight), clock(30, today.plusDays(1))).single().reason,
        )
        assertTrue(
            focusQueue(emptyList(), listOf(overnight), clock(61, today.plusDays(1))).isEmpty(),
        )
    }

    @Test
    fun onlyTasksPlannedForTodayBecomeGeneralQueueWork() {
        val todayTask = task(id = 1)
        val overdue = task(id = 2, plannedDate = today.minusDays(1))
        val future = task(id = 3, plannedDate = today.plusDays(1))
        val later = task(id = 4, plannedDate = null)

        assertEquals(
            listOf("Step"),
            focusQueue(emptyList(), listOf(todayTask, overdue, future, later), clock(9 * 60))
                .map(::stepTitle),
        )
    }

    @Test
    fun subtasksOutrankAccumulatesOnTheSameSignalStrength() {
        val item = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 9 * 60))
        val scheduled = task(id = 5, stepWindows = listOf(listOf(8 * 60 to 9 * 60)))

        val queue = focusQueue(listOf(item), listOf(scheduled), clock(8 * 60 + 30))

        assertTrue(queue.first().target is NowTarget.Task)
    }

    @Test
    fun deferredEntriesSortLastNoMatterHowStrongTheirReason() {
        val due = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 9 * 60))
        val queued = dailyGoal(id = 2, name = "Walk", goal = 4.0)

        val queue = focusQueue(
            items = listOf(due, queued),
            tasks = emptyList(),
            clock = clock(8 * 60 + 30),
            goalValues = mapOf(2L to 4.0 * 0.5),
            deferred = DeferredTargets(date = today, keys = setOf("item:1")),
        )

        // Due-now is the strongest reason and still goes last once deferred.
        assertEquals(listOf(2L, 1L), queue.map { itemId(it) })
        assertTrue(queue.last().isDeferred)
        // The reason survives deferral; it is postponed, not reclassified.
        assertTrue(queue.last().reason is FocusReason.DueNow)
    }

    @Test
    fun deferringEveryEntryLeavesTheQueuePopulated() {
        val first = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 9 * 60))
        val second = accumulate(id = 2, name = "Walk", windows = listOf(8 * 60 to 10 * 60))

        val queue = focusQueue(
            items = listOf(first, second),
            tasks = emptyList(),
            clock = clock(8 * 60 + 30),
            deferred = DeferredTargets(date = today, keys = setOf("item:1", "item:2")),
        )

        assertEquals(2, queue.size)
        assertTrue(queue.all { it.isDeferred })
        // Relative order among deferred entries still follows their reasons.
        assertEquals(listOf(1L, 2L), queue.map { itemId(it) })
    }

    @Test
    fun deferralExpiresWithTheLocalDay() {
        val due = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 9 * 60))
        val yesterday = DeferredTargets(date = today.minusDays(1), keys = setOf("item:1"))

        assertTrue(yesterday.on(today).isEmpty())
        assertEquals(
            false,
            focusQueue(listOf(due), emptyList(), clock(8 * 60 + 30), deferred = yesterday)
                .single().isDeferred,
        )
    }

    @Test
    fun subtaskDeferralUsesTheSubtaskIdentity() {
        val scheduled = task(
            id = 5,
            stepTitles = listOf("Morning pill", "Evening pill"),
            stepWindows = listOf(listOf(8 * 60 to 9 * 60), listOf(8 * 60 to 10 * 60)),
        )
        val firstStepKey = focusTargetKey(NowTarget.Task(scheduled, scheduled.steps.first()))

        val queue = focusQueue(
            items = emptyList(),
            tasks = listOf(scheduled),
            clock = clock(8 * 60 + 30),
            deferred = DeferredTargets(date = today, keys = setOf(firstStepKey)),
        )

        // Deferring one subtask leaves its sibling in the same task untouched.
        assertEquals(listOf("Evening pill", "Morning pill"), queue.map { stepTitle(it) })
        assertEquals(false, queue.first().isDeferred)
    }

    @Test
    fun targetKeysSeparateItemsFromSubtasks() {
        val item = accumulate(id = 7, name = "Water", windows = emptyList())
        val step = task(id = 7, stepTitles = listOf("Step")).steps.single()

        assertEquals("item:7", focusTargetKey(NowTarget.Accumulate(item)))
        assertEquals("step:700", focusTargetKey(NowTarget.Task(task(id = 7), step)))
    }

    @Test
    fun focusCommitmentsDoNotDependOnTheClock() {
        val windowed = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 9 * 60))

        // True at every hour, so the card never appears or vanishes on a tick.
        assertTrue(hasFocusCommitments(listOf(windowed), emptyList(), today))
        assertTrue(
            hasFocusCommitments(
                items = listOf(accumulate(id = 2, name = "Walk", windows = emptyList())),
                tasks = emptyList(),
                today = today,
                habitMinutes = mapOf(2L to 9 * 60),
            ),
        )
        assertTrue(hasFocusCommitments(emptyList(), listOf(task(id = 5)), today))
    }

    @Test
    fun focusCommitmentsIgnoreWhatCanNeverQueue() {
        val goalless = accumulate(id = 1, name = "Walk", windows = emptyList())
        val reached = dailyGoal(id = 2, name = "Water", goal = 8.0)
        val measurement = accumulate(id = 3, name = "Weight", windows = listOf(8 * 60 to 9 * 60))
            .copy(mode = RecordMode.MEASUREMENT)
        val doneTask = task(id = 5, stepTitles = listOf("Stretch"), completedStepCount = 1)
        val laterTask = task(id = 6, stepTitles = listOf("Someday"), plannedDate = null)

        assertEquals(
            false,
            hasFocusCommitments(
                items = listOf(goalless, reached, measurement),
                tasks = listOf(doneTask, laterTask),
                today = today,
                goalValues = mapOf(2L to 9.0),
            ),
        )
    }

    @Test
    fun dayRingCountsTodaySubtasksAndDailyGoals() {
        val goal = dailyGoal(id = 1, name = "Water", goal = 8.0)
        val weekly = dailyGoal(id = 2, name = "Run", goal = 20.0).copy(goalPeriod = GoalPeriod.DAYS_7)
        val todayTask = task(id = 5, stepTitles = listOf("A", "B"), completedStepCount = 1)
        val completedTodayTask = task(id = 6, stepTitles = listOf("C"), completedStepCount = 1)
        val later = task(id = 7, stepTitles = listOf("D"), plannedDate = null)

        val ring = dayRing(
            items = listOf(goal, weekly),
            tasks = listOf(todayTask, completedTodayTask, later),
            goalValues = mapOf(1L to 8.0, 2L to 0.0),
            today = today,
        )

        // Three subtasks plus one daily goal; completed task steps remain in the
        // ring, while the 7-day goal and Later task stay out.
        assertEquals(4, ring.total)
        assertEquals(3, ring.closed)
        assertEquals(false, ring.isComplete)
        assertTrue(ring.isVisible)
    }

    @Test
    fun emptyDayRingIsHiddenRatherThanComplete() {
        val ring = dayRing(emptyList(), emptyList(), emptyMap(), today)

        assertEquals(false, ring.isVisible)
        assertEquals(false, ring.isComplete)
        assertEquals(0f, ring.fraction, 0f)
    }

    @Test
    fun goalStreakCountsBackAndLeavesTodayOpen() {
        val goal = dailyGoal(id = 1, name = "Water", goal = 2.0)
        // Yesterday and the day before met the goal; today has nothing yet.
        val entries = listOf(
            entry(itemId = 1, date = today.minusDays(1), value = 2.0),
            entry(itemId = 1, date = today.minusDays(2), value = 2.0),
        )

        val streak = dailyGoalStreak(listOf(goal), entries, today, days = 7)

        assertEquals(2, streak.days)
        assertEquals(false, streak.atHistoryLimit)
        assertTrue(streak.isVisible)
    }

    @Test
    fun goalStreakBreaksOnAMissedDayAndNeedsEveryGoal() {
        val water = dailyGoal(id = 1, name = "Water", goal = 2.0)
        val walk = dailyGoal(id = 2, name = "Walk", goal = 1.0)
        val entries = listOf(
            entry(itemId = 1, date = today.minusDays(1), value = 2.0),
            entry(itemId = 1, date = today.minusDays(3), value = 2.0),
            entry(itemId = 2, date = today.minusDays(1), value = 1.0),
            entry(itemId = 2, date = today.minusDays(3), value = 1.0),
        )

        // Day -2 is missing for both, so the run stops at one day.
        assertEquals(1, dailyGoalStreak(listOf(water, walk), entries, today, days = 7).days)
        // Dropping one item's entry breaks the shared run entirely.
        assertEquals(
            0,
            dailyGoalStreak(
                items = listOf(water, walk),
                entries = entries.filterNot { it.itemId == 2L && it.localDate == today.minusDays(1).toString() },
                today = today,
                days = 7,
            ).days,
        )
    }

    @Test
    fun goalStreakFlagsRunsThatFillTheWholeHistory() {
        val goal = dailyGoal(id = 1, name = "Water", goal = 2.0)
        val entries = (0..6).map { back -> entry(itemId = 1, date = today.minusDays(back.toLong()), value = 2.0) }

        val streak = dailyGoalStreak(listOf(goal), entries, today, days = 7)

        assertEquals(7, streak.days)
        assertTrue(streak.atHistoryLimit)
    }

    @Test
    fun noDailyGoalsMeansNoStreak() {
        val goalless = accumulate(id = 1, name = "Walk", windows = emptyList())

        assertEquals(0, dailyGoalStreak(listOf(goalless), emptyList(), today, days = 7).days)
        assertEquals(false, dailyGoalStreak(listOf(goalless), emptyList(), today, days = 7).isVisible)
    }

    @Test
    fun itemStreaksUseAnyEntryWhenThereIsNoDailyGoal() {
        val goalless = accumulate(id = 1, name = "Walk", windows = emptyList())
        val goal = dailyGoal(id = 2, name = "Water", goal = 8.0)
        val entries = listOf(
            entry(itemId = 1, date = today.minusDays(1), value = 0.5),
            entry(itemId = 1, date = today.minusDays(2), value = 0.5),
            // Under the goal, so it does not extend the goal item's run.
            entry(itemId = 2, date = today.minusDays(1), value = 1.0),
        )

        val streaks = itemStreaks(listOf(goalless, goal), entries, today, days = 7)

        assertEquals(2, streaks[1L])
        assertEquals(0, streaks[2L])
    }

    @Test
    fun habitTimeNeedsEnoughEvidenceToPredictAnything() {
        // Five entries across five days, all near 09:00.
        val consistent = (1..5).map { back ->
            entry(itemId = 1, date = today.minusDays(back.toLong()), minute = 9 * 60 + back)
        }

        assertEquals(9 * 60 + 3, habitMinutes(consistent, today)[1L])
        // Four entries is not yet evidence.
        assertNull(habitMinutes(consistent.take(4), today)[1L])
        // Five entries on two days is not evidence either.
        assertNull(
            habitMinutes(
                (1..5).map { index -> entry(itemId = 1, date = today.minusDays(1), minute = 9 * 60 + index) },
                today,
            )[1L],
        )
    }

    @Test
    fun scatteredTimesDoNotBecomeAHabit() {
        val scattered = listOf(6 * 60, 11 * 60, 15 * 60, 19 * 60, 23 * 60).mapIndexed { index, minute ->
            entry(itemId = 1, date = today.minusDays(index.toLong() + 1), minute = minute)
        }

        assertNull(habitMinutes(scattered, today)[1L])
    }

    @Test
    fun habitTimesAverageAcrossMidnightInsteadOfLandingAtNoon() {
        // 23:40 and 00:20 average to midnight, not to 12:00.
        val overnight = listOf(23 * 60 + 40, 10, 23 * 60 + 50, 20, 0).mapIndexed { index, minute ->
            entry(itemId = 1, date = today.minusDays(index.toLong() + 1), minute = minute)
        }

        val habit = habitMinutes(overnight, today)[1L]

        assertTrue("expected a habit near midnight, got $habit", habit != null)
        assertTrue(circularMinuteDistance(checkNotNull(habit), 0) <= 5)
    }

    @Test
    fun todayEntriesDoNotTeachTheHabitTime() {
        val history = (1..5).map { back -> entry(itemId = 1, date = today.minusDays(back.toLong()), minute = 9 * 60) }
        val withToday = history + entry(itemId = 1, date = today, minute = 3 * 60)

        assertEquals(habitMinutes(history, today)[1L], habitMinutes(withToday, today)[1L])
    }

    @Test
    fun measurementEntriesNeverTeachHabitTimes() {
        val measurements = (1..5).map { back ->
            entry(itemId = 1, date = today.minusDays(back.toLong()), minute = 9 * 60)
                .copy(itemMode = RecordMode.MEASUREMENT)
        }

        assertTrue(habitMinutes(measurements, today).isEmpty())
    }

    private fun clock(minuteOfDay: Int, date: LocalDate = today) =
        RecordsClock(date = date, minuteOfDay = minuteOfDay)

    private fun itemId(candidate: FocusCandidate): Long? =
        (candidate.target as? NowTarget.Accumulate)?.item?.id

    private fun stepTitle(candidate: FocusCandidate): String =
        (candidate.target as NowTarget.Task).step.title

    private fun entry(
        itemId: Long,
        date: LocalDate,
        minute: Int = 9 * 60,
        value: Double = 1.0,
    ) = RecordEntry(
        id = itemId * 1000 + date.dayOfYear * 10 + minute % 10,
        itemId = itemId,
        occurredAt = 0,
        localDate = date.toString(),
        localMinute = minute,
        value = value,
        createdAt = 0,
        itemName = "Item $itemId",
        itemMode = RecordMode.ACCUMULATE,
        itemUnit = "count",
        itemValueType = RecordValueType.NUMBER,
    )

    private fun task(
        id: Long,
        title: String = "Task $id",
        stepTitles: List<String> = listOf("Step"),
        stepWindows: List<List<Pair<Int, Int>>> = emptyList(),
        completedStepCount: Int = 0,
        plannedDate: LocalDate? = today,
    ) = TodoTask(
        id = id,
        title = title,
        plannedDate = plannedDate,
        completedAt = if (completedStepCount >= stepTitles.size) 1L else null,
        createdAt = 0,
        updatedAt = 0,
        steps = stepTitles.mapIndexed { index, stepTitle ->
            TodoStep(
                id = id * 100 + index,
                taskId = id,
                title = stepTitle,
                completedAt = if (index < completedStepCount) 1L else null,
                createdAt = 0,
                updatedAt = 0,
                windows = stepWindows.getOrElse(index) { emptyList() }
                    .map { (start, end) -> RecordWindow(startMinute = start, endMinute = end) },
            )
        },
    )

    private fun dailyGoal(id: Long, name: String, goal: Double) =
        accumulate(id = id, name = name, windows = emptyList())
            .copy(goalValue = goal, goalPeriod = GoalPeriod.DAY)

    private fun accumulate(
        id: Long,
        name: String,
        windows: List<Pair<Int, Int>>,
        priority: Int = 0,
    ) = RecordItem(
        id = id,
        name = name,
        description = "",
        mode = RecordMode.ACCUMULATE,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "count",
        goalValue = null,
        goalPeriod = null,
        priority = priority,
        archived = false,
        windows = windows.map { (start, end) -> RecordWindow(startMinute = start, endMinute = end) },
        createdAt = 0,
        updatedAt = 0,
    )
}
