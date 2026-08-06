package com.wswensw.woolbox.record.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

const val DEFAULT_NUMBER_UNIT = "次"
const val DEFAULT_DURATION_UNIT = "分"

val DURATION_UNITS = listOf("秒", "分", "小时")

class RecordValidationException(message: String) : IllegalArgumentException(message)

fun RecordItemDraft.validated(): RecordItemDraft {
    val normalizedName = name.trim()
    val normalizedDescription = description.trim()
    val normalizedUnit = unit.trim()

    requireRecord(normalizedName.isNotEmpty(), "Name is required.")
    requireRecord(normalizedUnit.isNotEmpty(), "Unit is required.")
    requirePositive(stepValue, "Step")

    when (mode) {
        RecordMode.ACCUMULATE -> {
            requirePositive(defaultValue, "Default value")
            requirePositive(goalValue, "Goal")
            requireRecord(minValue == null && maxValue == null, "Accumulate items do not support measurement bounds.")
            requireRecord(
                goalValue == null || goalPeriod != null,
                "Goal period is required when a goal is set.",
            )
            requireRecord(
                valueType != RecordValueType.DURATION || normalizedUnit in DURATION_UNITS,
                "Duration unit must be 秒, 分, or 小时.",
            )
        }
        RecordMode.MEASUREMENT -> {
            requireRecord(valueType == RecordValueType.NUMBER, "Measurements must use Number.")
            requireRecord(defaultValue == null, "Measurements do not support default values.")
            val measurementMin = minValue ?: throw RecordValidationException("Measurement minimum and maximum are required.")
            val measurementMax = maxValue ?: throw RecordValidationException("Measurement minimum and maximum are required.")
            measurementStepCount(measurementMin, measurementMax, stepValue)
            requireRecord(goalValue == null && goalPeriod == null, "Measurements do not support goals.")
        }
    }

    return copy(
        name = normalizedName,
        description = normalizedDescription,
        unit = normalizedUnit,
        goalPeriod = goalPeriod.takeIf { mode == RecordMode.ACCUMULATE && goalValue != null },
    )
}

fun measurementStepCount(minValue: Double, maxValue: Double, stepValue: Double): Int {
    requireRecord(minValue.isFinite() && maxValue.isFinite(), "Measurement bounds must be finite numbers.")
    requireRecord(minValue < maxValue, "Measurement minimum must be less than maximum.")
    requirePositive(stepValue, "Step")
    val span = BigDecimal.valueOf(maxValue).subtract(BigDecimal.valueOf(minValue))
    val step = BigDecimal.valueOf(stepValue)
    val (count, remainder) = span.divideAndRemainder(step)
    requireRecord(remainder.compareTo(BigDecimal.ZERO) == 0, "Measurement range must divide evenly by step.")
    requireRecord(count > BigDecimal.ZERO && count <= BigDecimal.valueOf(Int.MAX_VALUE.toLong()), "Measurement step count is invalid.")
    return count.intValueExact()
}

fun isMeasurementValueOnGrid(value: Double, minValue: Double, maxValue: Double, stepValue: Double): Boolean {
    if (!value.isFinite() || value < minValue || value > maxValue) return false
    return runCatching {
        measurementStepCount(minValue, maxValue, stepValue)
        BigDecimal.valueOf(value)
            .subtract(BigDecimal.valueOf(minValue))
            .divideAndRemainder(BigDecimal.valueOf(stepValue))[1]
            .compareTo(BigDecimal.ZERO) == 0
    }.getOrDefault(false)
}

fun snapMeasurementValue(value: Double, minValue: Double, maxValue: Double, stepValue: Double): Double {
    val stepCount = measurementStepCount(minValue, maxValue, stepValue)
    val minimum = BigDecimal.valueOf(minValue)
    val step = BigDecimal.valueOf(stepValue)
    val bounded = value.coerceIn(minValue, maxValue)
    val index = BigDecimal.valueOf(bounded)
        .subtract(minimum)
        .divide(step, 0, RoundingMode.HALF_UP)
        .toInt()
        .coerceIn(0, stepCount)
    return minimum.add(step.multiply(BigDecimal.valueOf(index.toLong()))).toDouble()
}

fun stepRecordValue(value: String, step: Double, direction: Int): String {
    require(direction == -1 || direction == 1)
    val stepValue = BigDecimal.valueOf(step)
    val current = value.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
    val next = (current + stepValue * BigDecimal.valueOf(direction.toLong()))
        .max(stepValue)
    return next.stripTrailingZeros().toPlainString()
}

fun goalValues(
    items: List<RecordItem>,
    date: LocalDate,
    dayEntries: List<RecordEntry>,
    recentEntries: List<RecordEntry>,
): Map<Long, Double> {
    // DAYS_7 goals sum a rolling last-N-days window that advances every day.
    val rollingStart = date.minusDays((RECENT_ACTIVE_DAYS - 1).toLong()).toString()
    val today = date.toString()
    return items.mapNotNull { item ->
        if (item.mode != RecordMode.ACCUMULATE) return@mapNotNull null
        val period = item.goalPeriod ?: return@mapNotNull null
        item.goalValue ?: return@mapNotNull null
        val entries = when (period) {
            GoalPeriod.DAY -> dayEntries
            GoalPeriod.DAYS_7 -> recentEntries.filter { it.localDate in rollingStart..today }
        }
        item.id to entries.filter { it.itemId == item.id }.sumOf(RecordEntry::value)
    }.toMap()
}

private fun requirePositive(value: Double?, label: String) {
    if (value != null) {
        requireRecord(value.isFinite() && value > 0, "$label must be a positive number.")
    }
}

private fun requireRecord(condition: Boolean, message: String) {
    if (!condition) {
        throw RecordValidationException(message)
    }
}
