package com.wswensw.woolbox.record.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecordRecentDaysTest {
    private val today = LocalDate.of(2026, 7, 22)

    @Test
    fun itemRecentDayValuesOldestToToday() {
        val entries = listOf(
            entry(1, "2026-07-16", 1.0),
            entry(1, "2026-07-20", 2.0),
            entry(1, "2026-07-20", 1.0),
            entry(1, "2026-07-22", 4.0),
            entry(1, "2026-07-15", 9.0),
            entry(2, "2026-07-10", 5.0),
        )
        val values = itemRecentDayValues(
            itemIds = listOf(1L, 2L),
            entries = entries,
            today = today,
        )
        assertEquals(
            listOf(1.0, 0.0, 0.0, 0.0, 3.0, 0.0, 4.0),
            values.getValue(1L),
        )
        assertEquals(emptyRecentDayValues(), values.getValue(2L))
    }

    @Test
    fun recentDayPieProgressUsesDayGoal() {
        val values = listOf(0.0, 2.0, 4.0, 8.0, 10.0, 0.0, 4.0)
        assertEquals(
            listOf(0f, 0.25f, 0.5f, 1f, 1f, 0f, 0.5f),
            recentDayPieProgress(values, goalValue = 8.0),
        )
    }

    private fun entry(itemId: Long, date: String, value: Double) = RecordEntry(
        id = itemId * 100 + date.hashCode().toLong().and(0xffff),
        itemId = itemId,
        occurredAt = 0,
        localDate = date,
        localMinute = 0,
        value = value,
        createdAt = 0,
        itemName = "Item",
        itemUnit = "count",
        itemValueType = RecordValueType.NUMBER,
    )
}
