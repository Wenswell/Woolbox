package com.wswensw.template.record.ui

import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordValueType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsScreenTest {
    @Test
    fun firstRecordCharacterKeepsTextCharacter() {
        assertEquals("阅", firstRecordCharacter("阅读"))
    }

    @Test
    fun firstRecordCharacterKeepsEmojiCharacter() {
        assertEquals("💧", firstRecordCharacter("💧 Water"))
    }

    @Test
    fun firstRecordCharacterKeepsCombiningCharacterSequence() {
        assertEquals("e\u0301", firstRecordCharacter("e\u0301 Accent"))
    }

    @Test
    fun firstRecordCharacterHandlesEmptyName() {
        assertEquals("", firstRecordCharacter(""))
    }

    @Test
    fun dropFirstRecordCharacterRemovesTextCharacter() {
        assertEquals("读", dropFirstRecordCharacter("阅读"))
    }

    @Test
    fun dropFirstRecordCharacterRemovesEmojiCharacter() {
        assertEquals(" Water", dropFirstRecordCharacter("💧 Water"))
    }

    @Test
    fun dropFirstRecordCharacterRemovesCombiningCharacterSequence() {
        assertEquals(" Accent", dropFirstRecordCharacter("e\u0301 Accent"))
    }

    @Test
    fun dropFirstRecordCharacterHandlesEmptyName() {
        assertEquals("", dropFirstRecordCharacter(""))
    }

    @Test
    fun displayRecordNameRemovesIconCharacterAndSeparator() {
        assertEquals("Water", displayRecordName("💧 Water"))
        assertEquals("阅", displayRecordName("阅"))
    }

    @Test
    fun focusGoalsUsePriorityLimitAndIgnoreItemsWithoutGoals() {
        val focus = focusGoalItems(
            listOf(
                recordItem(id = 1, name = "A", priority = 100, period = null),
                recordItem(id = 2, name = "B", priority = 20, period = GoalPeriod.WEEK),
                recordItem(id = 3, name = "C", priority = 50, period = GoalPeriod.DAY),
                recordItem(id = 4, name = "D", priority = 40, period = GoalPeriod.DAY),
                recordItem(id = 5, name = "E", priority = 10, period = GoalPeriod.WEEK),
            ),
        )

        assertEquals(listOf(3L, 4L, 2L), focus.map(RecordItem::id))
    }

    @Test
    fun goalItemsSeparatePeriodsAndKeepPriorityOrder() {
        val items = listOf(
            recordItem(id = 1, name = "A", priority = 10, period = GoalPeriod.DAY),
            recordItem(id = 2, name = "B", priority = 30, period = GoalPeriod.WEEK),
            recordItem(id = 3, name = "C", priority = 20, period = GoalPeriod.DAY),
        )

        assertEquals(listOf(3L, 1L), goalItems(items, GoalPeriod.DAY).map(RecordItem::id))
        assertEquals(listOf(2L), goalItems(items, GoalPeriod.WEEK).map(RecordItem::id))
    }

    @Test
    fun goalProgressFractionClampsAtBounds() {
        assertEquals(0f, goalProgressFraction(-2.0, 10.0), 0f)
        assertEquals(0.5f, goalProgressFraction(5.0, 10.0), 0f)
        assertEquals(1f, goalProgressFraction(15.0, 10.0), 0f)
    }

    @Test
    fun todayRecordValuesSumEntriesByItem() {
        val values = todayRecordValues(
            listOf(
                recordEntry(id = 1, itemId = 7, value = 2.5),
                recordEntry(id = 2, itemId = 7, value = 1.5),
                recordEntry(id = 3, itemId = 8, value = 3.0),
            ),
        )

        assertEquals(4.0, values[7] ?: 0.0, 0.0)
        assertEquals(3.0, values[8] ?: 0.0, 0.0)
    }

    @Test
    fun recordsDateLabelIsStableEnglishText() {
        assertEquals("Wednesday, July 22", recordsDateLabel(LocalDate.of(2026, 7, 22)))
    }

    private fun recordItem(
        id: Long,
        name: String,
        priority: Int,
        period: GoalPeriod?,
    ) = RecordItem(
        id = id,
        name = name,
        description = "",
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "count",
        goalValue = period?.let { 10.0 },
        goalPeriod = period,
        priority = priority,
        archived = false,
        windows = emptyList(),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun recordEntry(
        id: Long,
        itemId: Long,
        value: Double,
    ) = RecordEntry(
        id = id,
        itemId = itemId,
        occurredAt = 0,
        localDate = "2026-07-22",
        localMinute = 0,
        value = value,
        createdAt = 0,
        itemName = "Item",
        itemUnit = "count",
        itemValueType = RecordValueType.NUMBER,
    )
}
