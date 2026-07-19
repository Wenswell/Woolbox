package com.wswensw.template.record.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

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
}
