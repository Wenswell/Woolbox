package com.wswensw.template.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordStreaksTest {
    private val today = LocalDate.of(2026, 7, 22)

    @Test
    fun currentStreakUsesTodayWhenActive() {
        val dates = setOf(
            today,
            today.minusDays(1),
            today.minusDays(2),
        )
        assertEquals(3, currentStreak(dates, today))
    }

    @Test
    fun currentStreakAllowsYesterdayGrace() {
        val dates = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, currentStreak(dates, today))
    }

    @Test
    fun currentStreakBreaksAfterMissedDay() {
        val dates = setOf(today.minusDays(2), today.minusDays(3))
        assertEquals(0, currentStreak(dates, today))
    }

    @Test
    fun bestStreakFindsLongestRunInRange() {
        val dates = setOf(
            today.minusDays(10),
            today.minusDays(9),
            today.minusDays(8),
            today.minusDays(7),
            today.minusDays(2),
            today.minusDays(1),
            today,
        )
        assertEquals(4, bestStreakInRange(dates, today.minusDays(20), today))
        val streak = computeItemStreak(dates, today, today.minusDays(20))
        assertEquals(3, streak.current)
        assertEquals(4, streak.best)
    }

    @Test
    fun itemStreaksGroupByItemAndIgnoreOutsideWindow() {
        val entries = listOf(
            entry(itemId = 1, date = "2026-07-22"),
            entry(itemId = 1, date = "2026-07-21"),
            entry(itemId = 1, date = "2026-04-01"),
            entry(itemId = 2, date = "2026-07-20"),
        )
        val streaks = itemStreaks(
            itemIds = listOf(1L, 2L, 3L),
            entries = entries,
            today = today,
            lookbackDays = 90,
        )
        assertEquals(2, streaks.getValue(1L).current)
        assertEquals(0, streaks.getValue(2L).current)
        assertEquals(0, streaks.getValue(3L).current)
    }

    @Test
    fun streakLabelFormatsCurrentAndBest() {
        assertNull(streakLabel(ItemStreak(0, 0)))
        assertEquals("1 day", streakLabel(ItemStreak(1, 1)))
        assertEquals("3 days", streakLabel(ItemStreak(3, 3)))
        assertEquals("2 days · best 5", streakLabel(ItemStreak(2, 5)))
    }

    private fun entry(itemId: Long, date: String) = RecordEntry(
        id = itemId * 100 + date.hashCode().toLong().and(0xffff),
        itemId = itemId,
        occurredAt = 0,
        localDate = date,
        localMinute = 0,
        value = 1.0,
        createdAt = 0,
        itemName = "Item",
        itemUnit = "count",
        itemValueType = RecordValueType.NUMBER,
    )
}
