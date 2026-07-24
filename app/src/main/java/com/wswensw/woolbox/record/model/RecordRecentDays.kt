package com.wswensw.woolbox.record.model

import java.time.LocalDate

/** Inclusive recent window shown on accumulate cards, oldest → today. */
const val RECENT_ACTIVE_DAYS = 7

fun itemRecentActiveDays(
    itemIds: Collection<Long>,
    entries: List<RecordEntry>,
    today: LocalDate,
    days: Int = RECENT_ACTIVE_DAYS,
): Map<Long, List<Boolean>> {
    require(days > 0)
    val windowStart = today.minusDays((days - 1).toLong())
    val activeDates = linkedMapOf<Long, MutableSet<LocalDate>>()
    for (entry in entries) {
        val date = LocalDate.parse(entry.localDate)
        if (date.isBefore(windowStart) || date.isAfter(today)) continue
        activeDates.getOrPut(entry.itemId) { linkedSetOf() }.add(date)
    }
    return itemIds.associateWith { itemId ->
        recentActiveDays(activeDates[itemId].orEmpty(), today, days)
    }
}

fun recentActiveDays(
    activeDates: Set<LocalDate>,
    today: LocalDate,
    days: Int = RECENT_ACTIVE_DAYS,
): List<Boolean> {
    require(days > 0)
    return (days - 1 downTo 0).map { offset ->
        today.minusDays(offset.toLong()) in activeDates
    }
}

fun emptyRecentDays(days: Int = RECENT_ACTIVE_DAYS): List<Boolean> = List(days) { false }
