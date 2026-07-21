package com.wswensw.template.record.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecordRulesTest {
    @Test
    fun validatesAndNormalizesRecordItem() {
        val result = draft(
            name = "  Water  ",
            description = "  Drink water  ",
            unit = " ml ",
        ).validated()

        assertEquals("Water", result.name)
        assertEquals("Drink water", result.description)
        assertEquals("ml", result.unit)
    }

    @Test
    fun rejectsNonPositiveMeasurementValues() {
        assertThrows(RecordValidationException::class.java) {
            draft(stepValue = 0.0).validated()
        }
        assertThrows(RecordValidationException::class.java) {
            draft(defaultValue = -1.0).validated()
        }
    }

    @Test
    fun clearsPeriodWhenGoalIsAbsent() {
        val result = draft(goalValue = null, goalPeriod = GoalPeriod.WEEK).validated()

        assertNull(result.goalPeriod)
    }

    @Test
    fun rejectsDuplicateSuggestedWindows() {
        val window = RecordWindow(startMinute = 9 * 60, endMinute = 10 * 60)

        assertThrows(RecordValidationException::class.java) {
            draft(windows = listOf(window, window)).validated()
        }
    }

    @Test
    fun suggestionsPreferTextThenCurrentWindow() {
        val current = item(1, "Read", "books", 21 * 60, 22 * 60)
        val later = item(2, "Reading notes", "write", 8 * 60, 9 * 60)

        val textResults = getRecordSuggestions(listOf(later, current), "read", 21 * 60)
        val timeResults = getRecordSuggestions(listOf(later, current), "", 21 * 60)

        assertEquals(listOf("Read", "Reading notes"), textResults.map { it.item.name })
        assertEquals("Read", timeResults.first().item.name)
    }

    @Test
    fun matchesSuggestedWindowAcrossMidnight() {
        assertTrue(isMinuteInWindow(30, 23 * 60, 2 * 60))
    }

    @Test
    fun stepsDecimalValuesWithoutFloatingPointNoise() {
        assertEquals("0.6", stepRecordValue("0.4", 0.2, 1))
        assertEquals("0.2", stepRecordValue("0.2", 0.2, -1))
    }

    @Test
    fun calculatesDailyAndWeeklyGoalValues() {
        val daily = item(1, "Water", "", 0, 1).copy(goalValue = 8.0, goalPeriod = GoalPeriod.DAY)
        val weekly = item(2, "Read", "", 0, 1).copy(goalValue = 10.0, goalPeriod = GoalPeriod.WEEK)
        val values = goalValues(
            items = listOf(daily, weekly),
            date = LocalDate.parse("2026-07-21"),
            dayEntries = listOf(entry(1, "2026-07-21", 2.0), entry(2, "2026-07-21", 3.0)),
            weekEntries = listOf(
                entry(2, "2026-07-20", 4.0),
                entry(2, "2026-07-21", 3.0),
                entry(2, "2026-07-26", 5.0),
            ),
        )

        assertEquals(2.0, values[1])
        assertEquals(12.0, values[2])
    }

    private fun draft(
        name: String = "Water",
        description: String = "",
        valueType: RecordValueType = RecordValueType.NUMBER,
        defaultValue: Double? = 1.0,
        stepValue: Double = 1.0,
        unit: String = "次",
        goalValue: Double? = null,
        goalPeriod: GoalPeriod? = null,
        priority: Int = 1,
        windows: List<RecordWindow> = emptyList(),
    ) = RecordItemDraft(
        icon = RecordIcon.CHECK_CIRCLE,
        name = name,
        description = description,
        valueType = valueType,
        defaultValue = defaultValue,
        stepValue = stepValue,
        unit = unit,
        goalValue = goalValue,
        goalPeriod = goalPeriod,
        priority = priority,
        windows = windows,
    )

    private fun item(id: Long, name: String, description: String, start: Int, end: Int) = RecordItem(
        id = id,
        icon = RecordIcon.CHECK_CIRCLE,
        name = name,
        description = description,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "次",
        goalValue = null,
        goalPeriod = null,
        priority = 1,
        archived = false,
        windows = listOf(RecordWindow(startMinute = start, endMinute = end)),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun entry(itemId: Long, localDate: String, value: Double) = RecordEntry(
        id = 0,
        itemId = itemId,
        occurredAt = 0,
        localDate = localDate,
        localMinute = 0,
        value = value,
        createdAt = 0,
        itemName = "",
        itemUnit = "次",
        itemValueType = RecordValueType.NUMBER,
    )
}
