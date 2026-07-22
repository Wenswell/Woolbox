package com.wswensw.template.record.model

import java.time.LocalDate

/** Inclusive lookback window size ending at the selected date. */
const val STREAK_LOOKBACK_DAYS = 90

data class ItemStreak(
    val current: Int,
    val best: Int,
) {
    init {
        require(current >= 0)
        require(best >= 0)
        require(best >= current)
    }
}

fun itemStreaks(
    itemIds: Collection<Long>,
    entries: List<RecordEntry>,
    today: LocalDate,
    lookbackDays: Int = STREAK_LOOKBACK_DAYS,
): Map<Long, ItemStreak> {
    require(lookbackDays > 0)
    val lookbackStart = today.minusDays((lookbackDays - 1).toLong())
    val activeDates = linkedMapOf<Long, MutableSet<LocalDate>>()
    for (entry in entries) {
        val date = LocalDate.parse(entry.localDate)
        if (date.isBefore(lookbackStart) || date.isAfter(today)) continue
        activeDates.getOrPut(entry.itemId) { linkedSetOf() }.add(date)
    }
    return itemIds.associateWith { itemId ->
        computeItemStreak(
            activeDates = activeDates[itemId].orEmpty(),
            today = today,
            lookbackStart = lookbackStart,
        )
    }
}

fun computeItemStreak(
    activeDates: Set<LocalDate>,
    today: LocalDate,
    lookbackStart: LocalDate,
): ItemStreak {
    val current = currentStreak(activeDates, today)
    val bestInWindow = bestStreakInRange(activeDates, lookbackStart, today)
    val best = maxOf(bestInWindow, current)
    return ItemStreak(current = current, best = best)
}

fun currentStreak(activeDates: Set<LocalDate>, today: LocalDate): Int {
    val start = when {
        today in activeDates -> today
        today.minusDays(1) in activeDates -> today.minusDays(1)
        else -> return 0
    }
    var count = 0
    var day = start
    while (day in activeDates) {
        count += 1
        day = day.minusDays(1)
    }
    return count
}

fun bestStreakInRange(
    activeDates: Set<LocalDate>,
    start: LocalDate,
    end: LocalDate,
): Int {
    val days = activeDates
        .filter { !it.isBefore(start) && !it.isAfter(end) }
        .sorted()
    if (days.isEmpty()) return 0
    var best = 1
    var run = 1
    for (index in 1 until days.size) {
        val previous = days[index - 1]
        val current = days[index]
        if (current == previous) continue
        if (current == previous.plusDays(1)) {
            run += 1
            if (run > best) best = run
        } else {
            run = 1
        }
    }
    return best
}

fun streakLabel(streak: ItemStreak): String? {
    if (streak.current <= 0) return null
    val current = if (streak.current == 1) "1 day" else "${streak.current} days"
    return if (streak.best > streak.current) {
        "$current · best ${streak.best}"
    } else {
        current
    }
}
