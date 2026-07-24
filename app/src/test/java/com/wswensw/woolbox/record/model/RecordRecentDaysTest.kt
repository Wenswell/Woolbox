package com.wswensw.woolbox.record.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecordRecentDaysTest {
    private val today = LocalDate.of(2026, 7, 22)

    @Test
    fun recentActiveDaysOldestToToday() {
        val dates = setOf(today.minusDays(6), today.minusDays(2), today)
        assertEquals(
            listOf(true, false, false, false, true, false, true),
            recentActiveDays(dates, today),
        )
    }

    @Test
    fun itemRecentActiveDaysGroupByItemAndIgnoreOutsideWindow() {
        val entries = listOf(
            entry(1, "2026-07-22"),
            entry(1, "2026-07-21"),
            entry(1, "2026-07-14"),
            entry(2, "2026-07-10"),
            entry(3, "2026-07-20"),
        )
        val recent = itemRecentActiveDays(
            itemIds = listOf(1L, 2L, 3L),
            entries = entries,
            today = today,
        )
        assertEquals(
            listOf(false, false, false, false, false, true, true),
            recent.getValue(1L),
        )
        assertEquals(emptyRecentDays(), recent.getValue(2L))
        assertEquals(
            listOf(false, false, false, false, true, false, false),
            recent.getValue(3L),
        )
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
