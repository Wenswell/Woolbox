package com.wswensw.woolbox.record.model

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordNowTest {
    @Test
    fun clockKeepsLocalDateAndMinuteOfDay() {
        val clock = RecordsClock.from(LocalDateTime.of(2026, 7, 26, 14, 32))
        assertEquals(14 * 60 + 32, clock.minuteOfDay)
        assertEquals(2026, clock.date.year)
        assertEquals(0.605f, clock.dayFraction, 0.001f)
        assertEquals(0f, RecordsClock.from(LocalDateTime.of(2026, 7, 26, 0, 0)).dayFraction, 0f)
    }

    @Test
    fun forwardMinutesWrapsAcrossMidnight() {
        assertEquals(0, forwardMinutes(600, 600))
        assertEquals(30, forwardMinutes(600, 630))
        assertEquals(60, forwardMinutes(23 * 60, 24 * 60 % MINUTES_PER_DAY))
        assertEquals(120, forwardMinutes(23 * 60, 60))
    }

    @Test
    fun activeSuggestionsOnlyIncludeOpenWindows() {
        val morning = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val evening = accumulate(id = 2, name = "Reading", windows = listOf(20 * 60 to 22 * 60))

        val active = activeNowSuggestions(listOf(morning, evening), minuteOfDay = 9 * 60)

        assertEquals(listOf(1L), active.map { it.item?.id })
        assertEquals(60, active.single().endsInMinutes)
        assertEquals(0, active.single().startsInMinutes)
    }

    @Test
    fun activeSuggestionsPutTheSoonestClosingWindowFirst() {
        val closingSoon = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val closingLater = accumulate(id = 2, name = "Walk", windows = listOf(8 * 60 to 12 * 60))

        val active = activeNowSuggestions(listOf(closingLater, closingSoon), minuteOfDay = 9 * 60)

        assertEquals(listOf(1L, 2L), active.map { it.item?.id })
    }

    @Test
    fun activeSuggestionsSpanMidnightAndRespectTheLimit() {
        val overnight = accumulate(id = 1, name = "Sleep", windows = listOf(23 * 60 to 2 * 60))
        val other = accumulate(id = 2, name = "Water", windows = listOf(0 to 6 * 60))

        assertEquals(
            listOf(1L, 2L),
            activeNowSuggestions(listOf(overnight, other), minuteOfDay = 30).map { it.item?.id },
        )
        assertEquals(
            1,
            activeNowSuggestions(listOf(overnight, other), minuteOfDay = 30, limit = 1).size,
        )
    }

    @Test
    fun activeSuggestionsSkipSatisfiedAndMeasurementItems() {
        val reached = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val measurement = accumulate(id = 2, name = "Weight", windows = emptyList())
            .copy(mode = RecordMode.MEASUREMENT, windows = listOf(RecordWindow(startMinute = 8 * 60, endMinute = 10 * 60)))

        val active = activeNowSuggestions(
            items = listOf(reached, measurement),
            minuteOfDay = 9 * 60,
            isSatisfied = { it.id == 1L },
        )

        assertTrue(active.isEmpty())
    }

    @Test
    fun activeSuggestionsDropWindowsThatAlreadyGotAnEntryToday() {
        val item = accumulate(
            id = 1,
            name = "Water",
            windows = listOf(8 * 60 to 10 * 60, 12 * 60 to 14 * 60),
        )
        val recorded = mapOf(1L to listOf(9 * 60 + 5))

        assertTrue(
            activeNowSuggestions(listOf(item), minuteOfDay = 9 * 60 + 30, recordedMinutes = recorded)
                .isEmpty(),
        )
        // A different window of the same item still stands on its own.
        assertEquals(
            listOf(1L),
            activeNowSuggestions(listOf(item), minuteOfDay = 13 * 60, recordedMinutes = recorded)
                .map { it.item?.id },
        )
    }

    @Test
    fun nextSuggestionSkipsWindowsThatAlreadyGotAnEntryToday() {
        val item = accumulate(id = 1, name = "Water", windows = listOf(10 * 60 to 11 * 60))

        assertNull(
            nextNowSuggestion(
                items = listOf(item),
                minuteOfDay = 9 * 60,
                recordedMinutes = mapOf(1L to listOf(10 * 60 + 30)),
            ),
        )
    }

    @Test
    fun nextSuggestionUsesTheNearestUpcomingWindowWithinLookahead() {
        val soon = accumulate(id = 1, name = "Water", windows = listOf(10 * 60 to 11 * 60))
        val later = accumulate(id = 2, name = "Reading", windows = listOf(20 * 60 to 22 * 60))

        val next = nextNowSuggestion(listOf(later, soon), minuteOfDay = 9 * 60)

        assertEquals(1L, next?.item?.id)
        assertEquals(60, next?.startsInMinutes)
        assertNull(nextNowSuggestion(listOf(later), minuteOfDay = 9 * 60))
    }

    @Test
    fun nextSuggestionIgnoresWindowsThatAreAlreadyOpen() {
        val open = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))

        assertNull(nextNowSuggestion(listOf(open), minuteOfDay = 9 * 60))
    }

    @Test
    fun goalSatisfactionNeedsAConfiguredGoal() {
        val goal = accumulate(id = 1, name = "Water", windows = emptyList())
            .copy(goalValue = 8.0, goalPeriod = GoalPeriod.DAY)
        assertTrue(isGoalSatisfied(goal, 8.0))
        assertEquals(false, isGoalSatisfied(goal, 7.5))
        assertEquals(false, isGoalSatisfied(goal.copy(goalValue = null, goalPeriod = null), 100.0))
    }

    @Test
    fun hasSuggestedWindowsCountsAccumulateItemsAndSubtasks() {
        val withWindow = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val withoutWindow = accumulate(id = 2, name = "Walk", windows = emptyList())

        assertTrue(hasSuggestedWindows(listOf(withoutWindow, withWindow)))
        assertEquals(false, hasSuggestedWindows(listOf(withoutWindow)))
        assertTrue(
            hasSuggestedWindows(
                items = listOf(withoutWindow),
                tasks = listOf(task(id = 1, stepWindows = listOf(listOf(8 * 60 to 9 * 60)))),
            ),
        )
    }

    @Test
    fun activeSuggestionsUseTheScheduledSubtaskNotTheFirstOne() {
        // Only the second subtask is scheduled, so it is the one suggested even
        // though the first is still unfinished.
        val scheduled = task(
            id = 5,
            title = "Morning routine",
            stepTitles = listOf("Stretch", "Shower"),
            stepWindows = listOf(emptyList(), listOf(8 * 60 to 9 * 60)),
        )

        val active = activeNowSuggestions(emptyList(), minuteOfDay = 8 * 60 + 30, tasks = listOf(scheduled))

        val target = active.single().target as NowTarget.Task
        assertEquals(5L, target.task.id)
        assertEquals("Shower", target.step.title)
        assertEquals(30, active.single().endsInMinutes)
    }

    @Test
    fun eachScheduledSubtaskIsItsOwnSuggestion() {
        val scheduled = task(
            id = 5,
            stepTitles = listOf("Morning pill", "Evening pill"),
            stepWindows = listOf(listOf(8 * 60 to 9 * 60), listOf(20 * 60 to 21 * 60)),
        )

        assertEquals(
            listOf("Morning pill"),
            activeNowSuggestions(emptyList(), minuteOfDay = 8 * 60 + 30, tasks = listOf(scheduled))
                .map { (it.target as NowTarget.Task).step.title },
        )
        assertEquals(
            listOf("Evening pill"),
            activeNowSuggestions(emptyList(), minuteOfDay = 20 * 60 + 30, tasks = listOf(scheduled))
                .map { (it.target as NowTarget.Task).step.title },
        )
    }

    @Test
    fun activeSuggestionsSkipCompletedAndUnscheduledSubtasks() {
        val done = task(
            id = 5,
            stepTitles = listOf("Stretch"),
            stepWindows = listOf(listOf(8 * 60 to 9 * 60)),
            completedStepCount = 1,
        )
        val unscheduled = task(id = 6, stepTitles = listOf("Later"), stepWindows = listOf(emptyList()))

        assertTrue(
            activeNowSuggestions(
                items = emptyList(),
                minuteOfDay = 8 * 60 + 30,
                tasks = listOf(done, unscheduled),
            ).isEmpty(),
        )
    }

    @Test
    fun subtaskSuggestionsRankAboveAccumulatesClosingAtTheSameTime() {
        val item = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 9 * 60))
        val scheduled = task(id = 5, stepWindows = listOf(listOf(8 * 60 to 9 * 60)))

        val active = activeNowSuggestions(
            items = listOf(item),
            minuteOfDay = 8 * 60 + 30,
            tasks = listOf(scheduled),
        )

        assertTrue(active.first().target is NowTarget.Task)
    }

    @Test
    fun nextSuggestionCoversUpcomingSubtaskWindows() {
        val scheduled = task(
            id = 5,
            stepTitles = listOf("Take meds"),
            stepWindows = listOf(listOf(10 * 60 to 11 * 60)),
        )

        val next = nextNowSuggestion(emptyList(), minuteOfDay = 9 * 60, tasks = listOf(scheduled))

        assertEquals(60, next?.startsInMinutes)
        assertEquals("Take meds", (next?.target as NowTarget.Task).step.title)
    }

    @Test
    fun windowCoverageCountsOverlapsOnceAndWrapsMidnight() {
        assertEquals(0, windowCoverageMinutes(emptyList()))
        assertEquals(120, windowCoverageMinutes(listOf(window(8 * 60, 10 * 60))))
        assertEquals(
            480,
            windowCoverageMinutes(listOf(window(8 * 60, 15 * 60), window(10 * 60, 16 * 60))),
        )
        assertEquals(360, windowCoverageMinutes(listOf(window(23 * 60, 5 * 60))))
    }

    @Test
    fun validatedWindowsRejectRepeatsEqualBoundsAndWideCoverage() {
        assertEquals(
            listOf(window(8 * 60, 10 * 60)),
            validatedWindows(listOf(window(8 * 60, 10 * 60))),
        )
        assertThrows { validatedWindows(listOf(window(8 * 60, 8 * 60))) }
        assertThrows {
            validatedWindows(listOf(window(8 * 60, 10 * 60), window(8 * 60, 10 * 60)))
        }
        assertThrows { validatedWindows(listOf(window(0, MAX_WINDOW_COVERAGE_MINUTES))) }
        // Exactly at the cap is rejected; one minute under is accepted.
        assertEquals(
            1,
            validatedWindows(listOf(window(0, MAX_WINDOW_COVERAGE_MINUTES - 1))).size,
        )
    }

    private fun assertThrows(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is RecordValidationException)
    }

    private fun window(startMinute: Int, endMinute: Int) =
        RecordWindow(startMinute = startMinute, endMinute = endMinute)

    private fun task(
        id: Long,
        title: String = "Task $id",
        stepTitles: List<String> = listOf("Step"),
        stepWindows: List<List<Pair<Int, Int>>> = emptyList(),
        completedStepCount: Int = 0,
    ) = TodoTask(
        id = id,
        title = title,
        horizon = TaskHorizon.TODAY,
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

    @Test
    fun minuteSpansAreCompact() {
        assertEquals("0m", com.wswensw.woolbox.record.ui.formatMinuteSpan(0))
        assertEquals("45m", com.wswensw.woolbox.record.ui.formatMinuteSpan(45))
        assertEquals("1h 00m", com.wswensw.woolbox.record.ui.formatMinuteSpan(60))
        assertEquals("2h 05m", com.wswensw.woolbox.record.ui.formatMinuteSpan(125))
    }

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
