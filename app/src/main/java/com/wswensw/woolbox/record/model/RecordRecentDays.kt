package com.wswensw.woolbox.record.model

import java.time.LocalDate

/** Inclusive recent window shown on accumulate cards, oldest → today. */
const val RECENT_ACTIVE_DAYS = 7

/**
 * Per-item daily totals for the recent window.
 * Each list has [RECENT_ACTIVE_DAYS] values, oldest → today.
 */
fun itemRecentDayValues(
    itemIds: Collection<Long>,
    entries: List<RecordEntry>,
    today: LocalDate,
    days: Int = RECENT_ACTIVE_DAYS,
): Map<Long, List<Double>> {
    require(days > 0)
    val windowStart = today.minusDays((days - 1).toLong())
    val totals = linkedMapOf<Long, DoubleArray>()
    for (itemId in itemIds) {
        totals[itemId] = DoubleArray(days)
    }
    for (entry in entries) {
        val values = totals[entry.itemId] ?: continue
        val date = LocalDate.parse(entry.localDate)
        if (date.isBefore(windowStart) || date.isAfter(today)) continue
        val index = (days - 1) - (today.toEpochDay() - date.toEpochDay()).toInt()
        if (index in 0 until days) {
            values[index] += entry.value
        }
    }
    return totals.mapValues { (_, values) -> values.toList() }
}

/**
 * Pie fill 0..1 for each recent day.
 * Day goal uses goal value; week goal uses goal/7 pace; no goal uses window max day.
 */
fun recentDayPieProgress(
    dayValues: List<Double>,
    goalValue: Double?,
    goalPeriod: GoalPeriod?,
    days: Int = RECENT_ACTIVE_DAYS,
): List<Float> {
    require(dayValues.size == days) { "dayValues must contain $days values." }
    val target = when {
        goalValue != null && goalValue > 0.0 && goalPeriod == GoalPeriod.DAY -> goalValue
        goalValue != null && goalValue > 0.0 && goalPeriod == GoalPeriod.WEEK -> goalValue / days.toDouble()
        else -> dayValues.maxOrNull()?.takeIf { it > 0.0 }
    } ?: return List(days) { 0f }
    return dayValues.map { value ->
        (value / target).toFloat().coerceIn(0f, 1f)
    }
}

fun emptyRecentDayValues(days: Int = RECENT_ACTIVE_DAYS): List<Double> = List(days) { 0.0 }

fun emptyRecentDayPies(days: Int = RECENT_ACTIVE_DAYS): List<Float> = List(days) { 0f }
