package com.wswensw.woolbox.record.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

const val MINUTES_PER_DAY = 24 * 60

/** Suggested windows further ahead than this are not announced on the root page. */
const val NEXT_WINDOW_LOOKAHEAD_MINUTES = 6 * 60

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

    /** Item id whose today entries retire this target's windows, if any. */
    val recordKey: Long?

    data class Accumulate(val item: RecordItem) : NowTarget {
        override val name: String get() = item.name
        override val priority: Int get() = item.priority
        override val recordKey: Long get() = item.id
    }

    data class Task(val task: TodoTask, val step: TodoStep) : NowTarget {
        // The subtask is what gets done, so it leads the label and the sort.
        override val name: String get() = step.title
        // Subtasks carry no priority field; rank them above equal-time items so
        // a scheduled subtask is not buried under accumulates.
        override val priority: Int get() = 1

        // Subtasks retire through completion, not through entries.
        override val recordKey: Long? get() = null
    }
}

/**
 * A target selected by its suggested window.
 * [startsInMinutes] is `0` while the window is active; [endsInMinutes] counts
 * down to its end and is `0` for windows that have not started.
 */
data class RecordNowSuggestion(
    val target: NowTarget,
    val window: RecordWindow,
    val startsInMinutes: Int,
    val endsInMinutes: Int,
) {
    val item: RecordItem? get() = (target as? NowTarget.Accumulate)?.item
}

fun hasSuggestedWindows(items: List<RecordItem>, tasks: List<TodoTask> = emptyList()): Boolean =
    items.any { it.mode == RecordMode.ACCUMULATE && it.windows.isNotEmpty() } ||
        tasks.any { task -> task.steps.any { it.windows.isNotEmpty() } }

/**
 * Accumulate items and scheduled subtasks as one list of candidates. Each
 * unfinished subtask carrying windows stands on its own, so a task with a
 * morning and an evening subtask is suggested at both times. Satisfied
 * accumulates and completed subtasks are left out.
 */
private fun nowTargets(
    items: List<RecordItem>,
    tasks: List<TodoTask>,
    isSatisfied: (RecordItem) -> Boolean,
): List<Pair<NowTarget, List<RecordWindow>>> = buildList {
    for (item in items) {
        if (item.mode != RecordMode.ACCUMULATE || isSatisfied(item)) continue
        if (item.windows.isNotEmpty()) add(NowTarget.Accumulate(item) to item.windows)
    }
    for (task in tasks) {
        for (step in task.steps) {
            if (step.isCompleted || step.windows.isEmpty()) continue
            add(NowTarget.Task(task, step) to step.windows)
        }
    }
}

/**
 * Accumulate items and subtasks whose suggested window contains [minuteOfDay],
 * soonest to close first. An accumulate window that already received one of
 * [recordedMinutes] today is done and drops out, so a suggestion never
 * survives acting on it.
 */
fun activeNowSuggestions(
    items: List<RecordItem>,
    minuteOfDay: Int,
    tasks: List<TodoTask> = emptyList(),
    recordedMinutes: Map<Long, List<Int>> = emptyMap(),
    limit: Int = 3,
    isSatisfied: (RecordItem) -> Boolean = { false },
): List<RecordNowSuggestion> {
    require(limit >= 0)
    return nowTargets(items, tasks, isSatisfied)
        .mapNotNull { (target, windows) ->
            windows
                .filter { isMinuteInWindow(minuteOfDay, it.startMinute, it.endMinute) }
                .filterNot { window ->
                    isWindowRecorded(window, target.recordKey?.let(recordedMinutes::get))
                }
                .minByOrNull { forwardMinutes(minuteOfDay, it.endMinute) }
                ?.let { window ->
                    RecordNowSuggestion(
                        target = target,
                        window = window,
                        startsInMinutes = 0,
                        endsInMinutes = forwardMinutes(minuteOfDay, window.endMinute),
                    )
                }
        }
        .sortedWith(
            compareBy<RecordNowSuggestion> { it.endsInMinutes }
                .thenByDescending { it.target.priority }
                .thenBy { it.target.name.lowercase(Locale.ROOT) },
        )
        .take(limit)
}

/** Nearest suggested window that starts within [lookaheadMinutes] and is not active yet. */
fun nextNowSuggestion(
    items: List<RecordItem>,
    minuteOfDay: Int,
    tasks: List<TodoTask> = emptyList(),
    recordedMinutes: Map<Long, List<Int>> = emptyMap(),
    lookaheadMinutes: Int = NEXT_WINDOW_LOOKAHEAD_MINUTES,
    isSatisfied: (RecordItem) -> Boolean = { false },
): RecordNowSuggestion? = nowTargets(items, tasks, isSatisfied)
    .asSequence()
    .flatMap { (target, windows) -> windows.asSequence().map { window -> target to window } }
    .filterNot { (_, window) -> isMinuteInWindow(minuteOfDay, window.startMinute, window.endMinute) }
    .filterNot { (target, window) ->
        isWindowRecorded(window, target.recordKey?.let(recordedMinutes::get))
    }
    .map { (target, window) ->
        RecordNowSuggestion(
            target = target,
            window = window,
            startsInMinutes = forwardMinutes(minuteOfDay, window.startMinute),
            endsInMinutes = 0,
        )
    }
    .filter { it.startsInMinutes in 1..lookaheadMinutes }
    .minWithOrNull(
        compareBy<RecordNowSuggestion> { it.startsInMinutes }
            .thenByDescending { it.target.priority }
            .thenBy { it.target.name.lowercase(Locale.ROOT) },
    )

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
