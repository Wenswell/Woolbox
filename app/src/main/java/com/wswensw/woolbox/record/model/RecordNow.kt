package com.wswensw.woolbox.record.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

const val MINUTES_PER_DAY = 24 * 60

/** Suggested windows further ahead than this are not announced on the root page. */
const val NEXT_WINDOW_LOOKAHEAD_MINUTES = 6 * 60

/** Local wall-clock snapshot driving every time-aware Records surface. */
data class RecordsClock(
    val date: LocalDate,
    val minuteOfDay: Int,
) {
    /** Share of the local day already elapsed. */
    val dayFraction: Float
        get() = (minuteOfDay.toFloat() / MINUTES_PER_DAY).coerceIn(0f, 1f)

    companion object {
        fun from(dateTime: LocalDateTime) = RecordsClock(
            date = dateTime.toLocalDate(),
            minuteOfDay = dateTime.hour * 60 + dateTime.minute,
        )

        fun now(): RecordsClock = from(LocalDateTime.now())
    }
}

/**
 * An accumulate item selected by its suggested window.
 * [startsInMinutes] is `0` while the window is active; [endsInMinutes] counts
 * down to its end and is `0` for windows that have not started.
 */
data class RecordNowSuggestion(
    val item: RecordItem,
    val window: RecordWindow,
    val startsInMinutes: Int,
    val endsInMinutes: Int,
)

fun hasSuggestedWindows(items: List<RecordItem>): Boolean =
    items.any { it.mode == RecordMode.ACCUMULATE && it.windows.isNotEmpty() }

/** Accumulate items whose suggested window contains [minuteOfDay], soonest to close first. */
fun activeNowSuggestions(
    items: List<RecordItem>,
    minuteOfDay: Int,
    limit: Int = 3,
    isSatisfied: (RecordItem) -> Boolean = { false },
): List<RecordNowSuggestion> {
    require(limit >= 0)
    return items
        .asSequence()
        .filter { it.mode == RecordMode.ACCUMULATE && !isSatisfied(it) }
        .mapNotNull { item ->
            item.windows
                .filter { isMinuteInWindow(minuteOfDay, it.startMinute, it.endMinute) }
                .minByOrNull { forwardMinutes(minuteOfDay, it.endMinute) }
                ?.let { window ->
                    RecordNowSuggestion(
                        item = item,
                        window = window,
                        startsInMinutes = 0,
                        endsInMinutes = forwardMinutes(minuteOfDay, window.endMinute),
                    )
                }
        }
        .sortedWith(
            compareBy<RecordNowSuggestion> { it.endsInMinutes }
                .thenByDescending { it.item.priority }
                .thenBy { it.item.name.lowercase(Locale.ROOT) },
        )
        .take(limit)
        .toList()
}

/** Nearest suggested window that starts within [lookaheadMinutes] and is not active yet. */
fun nextNowSuggestion(
    items: List<RecordItem>,
    minuteOfDay: Int,
    lookaheadMinutes: Int = NEXT_WINDOW_LOOKAHEAD_MINUTES,
    isSatisfied: (RecordItem) -> Boolean = { false },
): RecordNowSuggestion? = items
    .asSequence()
    .filter { it.mode == RecordMode.ACCUMULATE && !isSatisfied(it) }
    .flatMap { item -> item.windows.asSequence().map { window -> item to window } }
    .filterNot { (_, window) -> isMinuteInWindow(minuteOfDay, window.startMinute, window.endMinute) }
    .map { (item, window) ->
        RecordNowSuggestion(
            item = item,
            window = window,
            startsInMinutes = forwardMinutes(minuteOfDay, window.startMinute),
            endsInMinutes = 0,
        )
    }
    .filter { it.startsInMinutes in 1..lookaheadMinutes }
    .minWithOrNull(
        compareBy<RecordNowSuggestion> { it.startsInMinutes }
            .thenByDescending { it.item.priority }
            .thenBy { it.item.name.lowercase(Locale.ROOT) },
    )

/** Whether an accumulate item already met its configured goal. */
fun isGoalSatisfied(item: RecordItem, cumulative: Double): Boolean {
    val target = item.goalValue ?: return false
    if (target <= 0.0 || item.goalPeriod == null) return false
    return cumulative >= target
}

/** Minutes from [from] forward to [to] on a circular day. */
fun forwardMinutes(from: Int, to: Int): Int {
    val span = (to - from) % MINUTES_PER_DAY
    return if (span < 0) span + MINUTES_PER_DAY else span
}
