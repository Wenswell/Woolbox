package com.wswensw.woolbox.record.ui

import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValueType
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsScreenTest {
    @Test
    fun orderedRecordItemsPreferGoalsThenPriority() {
        val ordered = orderedRecordItems(
            listOf(
                item(1, priority = 100),
                item(2, priority = 20, period = GoalPeriod.DAYS_7),
                item(3, priority = 50, period = GoalPeriod.DAY),
            ),
        )

        assertEquals(listOf(3L, 2L, 1L), ordered.map(RecordItem::id))
    }

    @Test
    fun todayValuesSumEntriesByItem() {
        val values = todayRecordValues(listOf(entry(1, 7, 2.5), entry(2, 7, 1.5), entry(3, 8, 3.0)))

        assertEquals(4.0, values[7] ?: 0.0, 0.0)
        assertEquals(3.0, values[8] ?: 0.0, 0.0)
    }

    @Test
    fun goalCrossingOnlyReportsFirstCrossing() {
        assertEquals(true, crossedGoalThreshold(9.0, 10.0, 10.0))
        assertEquals(false, crossedGoalThreshold(10.0, 12.0, 10.0))
        assertEquals(false, crossedGoalThreshold(0.0, 1.0, null))
    }

    private fun item(id: Long, priority: Int, period: GoalPeriod? = null) = RecordItem(
        id = id,
        name = "Item $id",
        description = "",
        mode = RecordMode.ACCUMULATE,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        minValue = null,
        maxValue = null,
        stepValue = 1.0,
        unit = "",
        goalValue = period?.let { 10.0 },
        goalPeriod = period,
        priority = priority,
        archived = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun entry(id: Long, itemId: Long, value: Double) = RecordEntry(
        id = id,
        itemId = itemId,
        occurredAt = 0,
        localDate = "2026-08-18",
        localMinute = 0,
        value = value,
        createdAt = 0,
        itemName = "Item",
        itemMode = RecordMode.ACCUMULATE,
        itemUnit = "",
        itemValueType = RecordValueType.NUMBER,
    )
}
