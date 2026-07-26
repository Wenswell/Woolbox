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

        assertEquals(listOf(1L), active.map { it.item.id })
        assertEquals(60, active.single().endsInMinutes)
        assertEquals(0, active.single().startsInMinutes)
    }

    @Test
    fun activeSuggestionsPutTheSoonestClosingWindowFirst() {
        val closingSoon = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val closingLater = accumulate(id = 2, name = "Walk", windows = listOf(8 * 60 to 12 * 60))

        val active = activeNowSuggestions(listOf(closingLater, closingSoon), minuteOfDay = 9 * 60)

        assertEquals(listOf(1L, 2L), active.map { it.item.id })
    }

    @Test
    fun activeSuggestionsSpanMidnightAndRespectTheLimit() {
        val overnight = accumulate(id = 1, name = "Sleep", windows = listOf(23 * 60 to 2 * 60))
        val other = accumulate(id = 2, name = "Water", windows = listOf(0 to 6 * 60))

        assertEquals(
            listOf(1L, 2L),
            activeNowSuggestions(listOf(overnight, other), minuteOfDay = 30).map { it.item.id },
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
    fun hasSuggestedWindowsOnlyCountsAccumulateItems() {
        val withWindow = accumulate(id = 1, name = "Water", windows = listOf(8 * 60 to 10 * 60))
        val withoutWindow = accumulate(id = 2, name = "Walk", windows = emptyList())

        assertTrue(hasSuggestedWindows(listOf(withoutWindow, withWindow)))
        assertEquals(false, hasSuggestedWindows(listOf(withoutWindow)))
    }

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
