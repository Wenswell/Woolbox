package com.wswensw.woolbox.record.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

const val DEFAULT_NUMBER_UNIT = "次"
const val DEFAULT_DURATION_UNIT = "分"

val DURATION_UNITS = listOf("秒", "分", "小时")

class RecordValidationException(message: String) : IllegalArgumentException(message)

data class RecordSuggestion(
    val item: RecordItem,
    val matchingWindow: RecordWindow?,
    val textRank: Int,
    val timeRank: Int,
    val timeDistance: Int,
)

fun RecordItemDraft.validated(): RecordItemDraft {
    val normalizedName = name.trim()
    val normalizedDescription = description.trim()
    val normalizedUnit = unit.trim()

    requireRecord(normalizedName.isNotEmpty(), "Name is required.")
    requireRecord(normalizedUnit.isNotEmpty(), "Unit is required.")
    requirePositive(stepValue, "Step")

    val normalizedWindows = windows.map { window ->
        requireRecord(window.startMinute in 0..1439, "Start time is invalid.")
        requireRecord(window.endMinute in 0..1439, "End time is invalid.")
        requireRecord(window.startMinute != window.endMinute, "Start and end times must differ.")
        window.copy(id = 0)
    }
    requireRecord(
        normalizedWindows.distinctBy { it.startMinute to it.endMinute }.size == normalizedWindows.size,
        "Suggested time windows must be unique.",
    )

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
            requireRecord(normalizedWindows.isEmpty(), "Measurements do not support suggested time windows.")
        }
    }

    return copy(
        name = normalizedName,
        description = normalizedDescription,
        unit = normalizedUnit,
        goalPeriod = goalPeriod.takeIf { mode == RecordMode.ACCUMULATE && goalValue != null },
        windows = normalizedWindows,
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

fun getRecordSuggestions(
    items: List<RecordItem>,
    query: String,
    currentMinute: Int,
    limit: Int = 5,
): List<RecordSuggestion> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return items
        .map { item ->
            val textRank = textMatchRank(item, normalizedQuery)
            val windowScore = bestWindowScore(item.windows, currentMinute)
            RecordSuggestion(
                item = item,
                matchingWindow = windowScore.window,
                textRank = textRank,
                timeRank = windowScore.rank,
                timeDistance = windowScore.distance,
            )
        }
        .filter { normalizedQuery.isEmpty() || it.textRank > 0 }
        .sortedWith { left, right ->
            if (normalizedQuery.isNotEmpty()) {
                right.textRank.compareTo(left.textRank)
                    .takeIf { it != 0 }
                    ?: compareSuggestionTime(left, right)
            } else {
                compareSuggestionTime(left, right)
            }
        }
        .take(limit)
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
    weekEntries: List<RecordEntry>,
): Map<Long, Double> {
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    val weekEnd = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString()
    return items.mapNotNull { item ->
        if (item.mode != RecordMode.ACCUMULATE) return@mapNotNull null
        val period = item.goalPeriod ?: return@mapNotNull null
        item.goalValue ?: return@mapNotNull null
        val entries = if (period == GoalPeriod.DAY) dayEntries else weekEntries.filter {
            it.localDate in weekStart..weekEnd
        }
        item.id to entries.filter { it.itemId == item.id }.sumOf(RecordEntry::value)
    }.toMap()
}

fun isMinuteInWindow(minute: Int, startMinute: Int, endMinute: Int): Boolean {
    val normalized = normalizeMinute(minute)
    val start = normalizeMinute(startMinute)
    val end = normalizeMinute(endMinute)
    return if (start < end) {
        normalized in start until end
    } else {
        normalized >= start || normalized < end
    }
}

private data class WindowScore(
    val window: RecordWindow?,
    val rank: Int,
    val distance: Int,
)

private fun textMatchRank(item: RecordItem, query: String): Int {
    if (query.isEmpty()) return 0
    val name = item.name.lowercase(Locale.ROOT)
    val description = item.description.lowercase(Locale.ROOT)
    return when {
        name.startsWith(query) -> 3
        query in name -> 2
        query in description -> 1
        else -> 0
    }
}

private fun bestWindowScore(windows: List<RecordWindow>, currentMinute: Int): WindowScore {
    if (windows.isEmpty()) return WindowScore(window = null, rank = 1, distance = 0)
    return windows
        .map { window ->
            val distance = distanceToWindow(currentMinute, window.startMinute, window.endMinute)
            WindowScore(
                window = window,
                rank = when {
                    distance == 0 -> 2
                    distance <= 60 -> 1
                    else -> 0
                },
                distance = distance,
            )
        }
        .minWith(
            compareByDescending<WindowScore> { it.rank }
                .thenBy { it.distance }
                .thenBy { it.window?.startMinute },
        )
}

private fun compareSuggestionTime(left: RecordSuggestion, right: RecordSuggestion): Int =
    right.timeRank.compareTo(left.timeRank)
        .takeIf { it != 0 }
        ?: left.timeDistance.compareTo(right.timeDistance).takeIf { it != 0 }
        ?: right.item.priority.compareTo(left.item.priority).takeIf { it != 0 }
        ?: left.item.name.compareTo(right.item.name, ignoreCase = true)

private fun distanceToWindow(minute: Int, startMinute: Int, endMinute: Int): Int {
    if (isMinuteInWindow(minute, startMinute, endMinute)) return 0
    return minOf(circularDistance(minute, startMinute), circularDistance(minute, endMinute))
}

private fun circularDistance(left: Int, right: Int): Int {
    val difference = kotlin.math.abs(normalizeMinute(left) - normalizeMinute(right))
    return minOf(difference, 24 * 60 - difference)
}

private fun normalizeMinute(minute: Int): Int = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)

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
