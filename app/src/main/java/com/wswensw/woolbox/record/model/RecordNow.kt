package com.wswensw.woolbox.record.model

import java.time.LocalDate
import java.time.LocalDateTime

const val MINUTES_PER_DAY = 24 * 60

/** Suggested windows must leave most of the day uncovered to stay meaningful. */
const val MAX_WINDOW_COVERAGE_MINUTES = 12 * 60

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
 * What a suggestion acts on. Accumulate items write their default value;
 * subtasks are completed. Windows belong to the subtask, not the task, so a
 * task can be suggested at several different times of day.
 */
sealed interface NowTarget {
    val name: String
    val priority: Int

    data class Accumulate(val item: RecordItem) : NowTarget {
        override val name: String get() = item.name
        override val priority: Int get() = item.priority
    }

    data class Task(val task: TodoTask, val step: TodoStep) : NowTarget {
        // The subtask is what gets done, so it leads the label and the sort.
        override val name: String get() = step.title
        // Subtasks carry no priority field; rank them above equal-signal items
        // so a scheduled subtask is not buried under accumulates.
        override val priority: Int get() = 1
    }
}

/** Whether an accumulate item already met its configured goal. */
fun isGoalSatisfied(item: RecordItem, cumulative: Double): Boolean {
    val target = item.goalValue ?: return false
    if (target <= 0.0 || item.goalPeriod == null) return false
    return cumulative >= target
}

/**
 * Whether today already has an entry inside [window]. Overnight windows only
 * match the part that falls on the current local date, because entries are
 * fixed to the local day they were recorded on.
 */
fun isWindowRecorded(window: RecordWindow, minutes: List<Int>?): Boolean =
    minutes?.any { isMinuteInWindow(it, window.startMinute, window.endMinute) } == true

/** Minutes from [from] forward to [to] on a circular day. */
fun forwardMinutes(from: Int, to: Int): Int {
    val span = (to - from) % MINUTES_PER_DAY
    return if (span < 0) span + MINUTES_PER_DAY else span
}

/** Shortest distance between two minutes on a circular day. */
fun circularMinuteDistance(left: Int, right: Int): Int {
    val forward = forwardMinutes(left, right)
    return minOf(forward, MINUTES_PER_DAY - forward)
}

/** Length of a window in minutes; a full-day window is impossible by validation. */
fun windowLengthMinutes(window: RecordWindow): Int =
    forwardMinutes(window.startMinute, window.endMinute)
