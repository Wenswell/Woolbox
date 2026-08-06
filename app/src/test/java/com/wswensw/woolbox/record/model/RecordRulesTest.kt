package com.wswensw.woolbox.record.model

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
    fun rejectsNonPositiveAccumulateValues() {
        assertThrows(RecordValidationException::class.java) {
            draft(stepValue = 0.0).validated()
        }
        assertThrows(RecordValidationException::class.java) {
            draft(defaultValue = -1.0).validated()
        }
    }

    @Test
    fun validatesSignedMeasurementBoundsAndRejectsMeasurementOnlyConflicts() {
        val result = draft(
            mode = RecordMode.MEASUREMENT,
            defaultValue = null,
            goalValue = null,
            goalPeriod = null,
            minValue = -20.0,
            maxValue = 20.0,
            stepValue = 0.5,
        ).validated()

        assertEquals(RecordMode.MEASUREMENT, result.mode)
        assertEquals(-20.0, result.minValue)
        assertEquals(20.0, result.maxValue)
        assertThrows(RecordValidationException::class.java) {
            draft(
                mode = RecordMode.MEASUREMENT,
                valueType = RecordValueType.DURATION,
                unit = "分",
            ).validated()
        }
    }

    @Test
    fun clearsPeriodWhenGoalIsAbsent() {
        val result = draft(goalValue = null, goalPeriod = GoalPeriod.DAYS_7).validated()

        assertNull(result.goalPeriod)
    }

    @Test
    fun stepsDecimalValuesWithoutFloatingPointNoise() {
        assertEquals("0.6", stepRecordValue("0.4", 0.2, 1))
        assertEquals("0.2", stepRecordValue("0.2", 0.2, -1))
    }

    @Test
    fun snapsSignedMeasurementValuesToConfiguredGrid() {
        assertEquals(-0.2, snapMeasurementValue(-0.18, -1.0, 1.0, 0.2), 0.0)
        assertEquals(1.0, snapMeasurementValue(1.1, -1.0, 1.0, 0.2), 0.0)
        assertTrue(isMeasurementValueOnGrid(0.6, -1.0, 1.0, 0.2))
        assertTrue(!isMeasurementValueOnGrid(0.5, -1.0, 1.0, 0.2))
    }

    @Test
    fun calculatesDailyAndDays7GoalValues() {
        val daily = item(1, "Water").copy(goalValue = 8.0, goalPeriod = GoalPeriod.DAY)
        val days7 = item(2, "Read").copy(goalValue = 10.0, goalPeriod = GoalPeriod.DAYS_7)
        // 2026-07-21 rolling window: 2026-07-15 .. 2026-07-21
        val values = goalValues(
            items = listOf(daily, days7),
            date = LocalDate.parse("2026-07-21"),
            dayEntries = listOf(entry(1, "2026-07-21", 2.0), entry(2, "2026-07-21", 3.0)),
            recentEntries = listOf(
                entry(2, "2026-07-14", 9.0), // outside rolling window
                entry(2, "2026-07-15", 1.0),
                entry(2, "2026-07-20", 4.0),
                entry(2, "2026-07-21", 3.0),
                entry(2, "2026-07-26", 5.0), // outside rolling window
            ),
        )

        assertEquals(2.0, values[1])
        assertEquals(8.0, values[2])
    }

    private fun draft(
        name: String = "Water",
        description: String = "",
        mode: RecordMode = RecordMode.ACCUMULATE,
        valueType: RecordValueType = RecordValueType.NUMBER,
        defaultValue: Double? = 1.0,
        stepValue: Double = 1.0,
        unit: String = "次",
        goalValue: Double? = null,
        goalPeriod: GoalPeriod? = null,
        priority: Int = 1,
        minValue: Double? = null,
        maxValue: Double? = null,
    ) = RecordItemDraft(
        name = name,
        description = description,
        mode = mode,
        valueType = valueType,
        defaultValue = defaultValue,
        stepValue = stepValue,
        unit = unit,
        goalValue = goalValue,
        goalPeriod = goalPeriod,
        priority = priority,
        minValue = minValue,
        maxValue = maxValue,
    )

    private fun item(id: Long, name: String) = RecordItem(
        id = id,
        name = name,
        description = "",
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "次",
        goalValue = null,
        goalPeriod = null,
        priority = 1,
        archived = false,
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
