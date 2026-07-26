package com.wswensw.woolbox.record.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Days of entry history the focus queue and streaks are derived from. */
const val FOCUS_HISTORY_DAYS = 30

/** Entries needed before a recorded time of day counts as a habit. */
private const val MIN_HABIT_ENTRIES = 5

/** Distinct days needed before a recorded time of day counts as a habit. */
private const val MIN_HABIT_DAYS = 3

/** A habit time must be this consistent, measured as mean absolute deviation. */
private const val MAX_HABIT_SPREAD_MINUTES = 90

/** How close to a habit time counts as being at that time. */
const val HABIT_REACH_MINUTES = 30

/** A day must be this far along before falling behind a daily goal matters. */
private const val BEHIND_PACE_FLOOR_FRACTION = 0.25f

/** Share of a daily goal that may be missing before pace counts as behind. */
private const val BEHIND_PACE_DEFICIT = 0.25f

/** A day must be this far along before an unbroken streak counts as at risk. */
private const val STREAK_RISK_FRACTION = 0.6f

/** Days of history a streak needs before it is worth protecting. */
private const val MIN_STREAK_AT_RISK = 2

/**
 * Why something is in the focus queue. Higher [rank] wins; within one rank the
 * lower [urgency] wins. Every commitment carries exactly one reason, the
 * strongest that applies, so the queue reads as a single ordered list.
 */
sealed interface FocusReason {
    val rank: Int
    val urgency: Int

    /** Inside a suggested window that has not been acted on. Closes soonest first. */
    data class DueNow(val endsInMinutes: Int) : FocusReason {
        override val rank: Int get() = 4
        override val urgency: Int get() = endsInMinutes
    }

    /** Near the time of day this item is normally recorded at. */
    data class Habit(val habitMinute: Int, val distanceMinutes: Int) : FocusReason {
        override val rank: Int get() = 3
        override val urgency: Int get() = distanceMinutes
    }

    /** An unbroken run of days that today has not extended yet. Longest run first. */
    data class StreakRisk(val days: Int) : FocusReason {
        override val rank: Int get() = 2
        override val urgency: Int get() = FOCUS_HISTORY_DAYS - days
    }

    /** A daily goal is further behind than the elapsed share of the day. */
    data class BehindPace(val percent: Int) : FocusReason {
        override val rank: Int get() = 1
        override val urgency: Int get() = 100 - percent
    }

    /** An open commitment with no signal pointing at this moment. */
    data object Queued : FocusReason {
        override val rank: Int get() = 0
        override val urgency: Int get() = 0
    }
}

/**
 * One entry in the focus queue: what to do, and why it is here.
 * A deferred entry keeps its reason and sorts last, because being unable to act
 * now is not the same as having nothing to do.
 */
data class FocusCandidate(
    val target: NowTarget,
    val reason: FocusReason,
    val window: RecordWindow? = null,
    val isDeferred: Boolean = false,
)

/**
 * Targets pushed to the queue's tail, and the local day that was true for.
 * Deferral is a statement about today, so a new day discards it rather than
 * carrying a stale decision forward.
 */
data class DeferredTargets(
    val date: LocalDate,
    val keys: Set<String>,
) {
    /** The keys still in force on [today]; a different day defers nothing. */
    fun on(today: LocalDate): Set<String> = if (date == today) keys else emptySet()

    companion object {
        fun none(): DeferredTargets = DeferredTargets(date = LocalDate.MIN, keys = emptySet())
    }
}

/**
 * Whether anything could enter the queue today, decided without the clock so
 * the card never appears or disappears on a minute tick.
 */
fun hasFocusCommitments(
    items: List<RecordItem>,
    tasks: List<TodoTask>,
    goalValues: Map<Long, Double> = emptyMap(),
    todayValues: Map<Long, Double> = emptyMap(),
    habitMinutes: Map<Long, Int> = emptyMap(),
): Boolean {
    val hasItem = items.any { item ->
        if (item.mode != RecordMode.ACCUMULATE) return@any false
        val hasGoal = item.goalValue != null && item.goalPeriod != null
        if (hasGoal && isGoalSatisfied(item, goalValues[item.id] ?: todayValues[item.id] ?: 0.0)) {
            return@any false
        }
        hasGoal || item.windows.isNotEmpty() || habitMinutes.containsKey(item.id)
    }
    if (hasItem) return true
    return tasks.any { task ->
        task.steps.any { step ->
            !step.isCompleted && (step.windows.isNotEmpty() || task.horizon == TaskHorizon.TODAY)
        }
    }
}

/**
 * Everything open, strongest reason first. Only commitments take part: an
 * accumulate item earns a place through an unmet goal, a suggested window it is
 * inside of, or a learned habit time; a subtask through being open today or
 * carrying a window. Items whose only claim is time bound drop out once that
 * time passes, so the queue never pads itself with things there is no reason to
 * do now.
 */
fun focusQueue(
    items: List<RecordItem>,
    tasks: List<TodoTask>,
    clock: RecordsClock,
    goalValues: Map<Long, Double> = emptyMap(),
    todayValues: Map<Long, Double> = emptyMap(),
    todayMinutes: Map<Long, List<Int>> = emptyMap(),
    habitMinutes: Map<Long, Int> = emptyMap(),
    itemStreaks: Map<Long, Int> = emptyMap(),
    deferred: DeferredTargets = DeferredTargets.none(),
): List<FocusCandidate> {
    val deferredKeys = deferred.on(clock.date)
    return buildList {
        for (item in items) {
            if (item.mode != RecordMode.ACCUMULATE) continue
            addAll(accumulateCandidate(item, clock, goalValues, todayValues, todayMinutes, habitMinutes, itemStreaks))
        }
        for (task in tasks) {
            for (step in task.steps) {
                if (step.isCompleted) continue
                addAll(stepCandidate(task, step, clock))
            }
        }
    }
        .map { candidate ->
            if (focusTargetKey(candidate.target) in deferredKeys) {
                candidate.copy(isDeferred = true)
            } else {
                candidate
            }
        }
        .sortedWith(
            // Deferral outranks every reason: it is the user's own call about
            // right now, and reasons only estimate that.
            compareBy<FocusCandidate> { it.isDeferred }
                .thenByDescending { it.reason.rank }
                .thenBy { it.reason.urgency }
                .thenByDescending { it.target.priority }
                .thenBy { it.target.name.lowercase(Locale.ROOT) },
        )
}

/** Stable identity for a target across snapshots, for deferral and animation. */
fun focusTargetKey(target: NowTarget): String = when (target) {
    is NowTarget.Accumulate -> "item:${target.item.id}"
    is NowTarget.Task -> "step:${target.step.id}"
}

private fun accumulateCandidate(
    item: RecordItem,
    clock: RecordsClock,
    goalValues: Map<Long, Double>,
    todayValues: Map<Long, Double>,
    todayMinutes: Map<Long, List<Int>>,
    habitMinutes: Map<Long, Int>,
    itemStreaks: Map<Long, Int>,
): List<FocusCandidate> {
    val hasGoal = item.goalValue != null && item.goalPeriod != null
    val cumulative = goalValues[item.id] ?: todayValues[item.id] ?: 0.0
    if (hasGoal && isGoalSatisfied(item, cumulative)) return emptyList()

    val minutes = todayMinutes[item.id]
    val target = NowTarget.Accumulate(item)
    val openWindow = item.windows
        .filter { isMinuteInWindow(clock.minuteOfDay, it.startMinute, it.endMinute) }
        .filterNot { isWindowRecorded(it, minutes) }
        .minByOrNull { forwardMinutes(clock.minuteOfDay, it.endMinute) }
    if (openWindow != null) {
        return listOf(
            FocusCandidate(
                target = target,
                reason = FocusReason.DueNow(forwardMinutes(clock.minuteOfDay, openWindow.endMinute)),
                window = openWindow,
            ),
        )
    }

    val streak = itemStreaks[item.id] ?: 0
    val recordedToday = minutes?.isNotEmpty() == true
    val habitMinute = habitMinutes[item.id]
    if (habitMinute != null && !recordedToday) {
        val distance = circularMinuteDistance(clock.minuteOfDay, habitMinute)
        if (distance <= HABIT_REACH_MINUTES) {
            return listOf(FocusCandidate(target, FocusReason.Habit(habitMinute, distance)))
        }
    }
    if (streak >= MIN_STREAK_AT_RISK && !recordedToday && clock.dayFraction >= STREAK_RISK_FRACTION) {
        return listOf(FocusCandidate(target, FocusReason.StreakRisk(streak)))
    }
    if (!hasGoal) {
        // Windows and habits are the only claim a goalless item has on right
        // now, and neither applies, so it stays out of the queue.
        return emptyList()
    }
    val goal = checkNotNull(item.goalValue)
    if (item.goalPeriod == GoalPeriod.DAY && clock.dayFraction >= BEHIND_PACE_FLOOR_FRACTION) {
        val expected = goal * clock.dayFraction
        if (cumulative < expected - goal * BEHIND_PACE_DEFICIT) {
            val percent = ((cumulative / goal) * 100.0).roundToInt().coerceIn(0, 100)
            return listOf(FocusCandidate(target, FocusReason.BehindPace(percent)))
        }
    }
    return listOf(FocusCandidate(target, FocusReason.Queued))
}

private fun stepCandidate(task: TodoTask, step: TodoStep, clock: RecordsClock): List<FocusCandidate> {
    val target = NowTarget.Task(task, step)
    val openWindow = step.windows
        .filter { isMinuteInWindow(clock.minuteOfDay, it.startMinute, it.endMinute) }
        .minByOrNull { forwardMinutes(clock.minuteOfDay, it.endMinute) }
    if (openWindow != null) {
        return listOf(
            FocusCandidate(
                target = target,
                reason = FocusReason.DueNow(forwardMinutes(clock.minuteOfDay, openWindow.endMinute)),
                window = openWindow,
            ),
        )
    }
    // A scheduled subtask belongs to its windows; an unscheduled one belongs to
    // today. Later tasks without a window are not a commitment for right now.
    if (step.windows.isNotEmpty() || task.horizon != TaskHorizon.TODAY) return emptyList()
    return listOf(FocusCandidate(target, FocusReason.Queued))
}

/** Today's commitments and how many are closed, driving the day ring. */
data class DayRing(
    val closed: Int,
    val total: Int,
) {
    val isVisible: Boolean get() = total > 0

    val fraction: Float
        get() = if (total <= 0) 0f else (closed.toFloat() / total).coerceIn(0f, 1f)

    val isComplete: Boolean get() = total > 0 && closed >= total
}

/**
 * Today's commitments: every subtask of a today task, and every daily goal.
 * Seven-day goals and later tasks are not today's ring.
 */
fun dayRing(
    items: List<RecordItem>,
    tasks: List<TodoTask>,
    goalValues: Map<Long, Double>,
): DayRing {
    val steps = tasks.filter { it.horizon == TaskHorizon.TODAY }.flatMap(TodoTask::steps)
    val dailyGoals = items.filter { item ->
        item.mode == RecordMode.ACCUMULATE && item.goalPeriod == GoalPeriod.DAY && item.goalValue != null
    }
    return DayRing(
        closed = steps.count(TodoStep::isCompleted) +
            dailyGoals.count { isGoalSatisfied(it, goalValues[it.id] ?: 0.0) },
        total = steps.size + dailyGoals.size,
    )
}

/** An unbroken run of days, and whether history ran out before the run did. */
data class StreakDays(
    val days: Int,
    val atHistoryLimit: Boolean,
) {
    val isVisible: Boolean get() = days > 0
}

/**
 * Consecutive days every daily goal was met, counting back from today. Today
 * stays optional until midnight, so a run survives a day that is still open.
 * No daily goals means no streak; nothing is invented to fill the slot.
 */
fun dailyGoalStreak(
    items: List<RecordItem>,
    entries: List<RecordEntry>,
    today: LocalDate,
    days: Int = FOCUS_HISTORY_DAYS,
): StreakDays {
    val goals = items.filter { item ->
        item.mode == RecordMode.ACCUMULATE && item.goalPeriod == GoalPeriod.DAY && item.goalValue != null
    }
    if (goals.isEmpty()) return StreakDays(days = 0, atHistoryLimit = false)
    val dayValues = itemRecentDayValues(goals.map(RecordItem::id), entries, today, days)
    val closed = BooleanArray(days) { index ->
        goals.all { goal -> (dayValues[goal.id]?.get(index) ?: 0.0) >= checkNotNull(goal.goalValue) }
    }
    return countStreak(closed)
}

/** Consecutive days an item was done, using its daily goal when it has one. */
fun itemStreaks(
    items: List<RecordItem>,
    entries: List<RecordEntry>,
    today: LocalDate,
    days: Int = FOCUS_HISTORY_DAYS,
): Map<Long, Int> {
    val accumulates = items.filter { it.mode == RecordMode.ACCUMULATE }
    if (accumulates.isEmpty()) return emptyMap()
    val dayValues = itemRecentDayValues(accumulates.map(RecordItem::id), entries, today, days)
    return accumulates.associate { item ->
        val values = dayValues[item.id] ?: List(days) { 0.0 }
        val threshold = item.goalValue?.takeIf { item.goalPeriod == GoalPeriod.DAY } ?: Double.MIN_VALUE
        item.id to countStreak(BooleanArray(days) { index -> values[index] >= threshold }).days
    }
}

/** Runs back from the last day, which may still be open without breaking the run. */
private fun countStreak(closed: BooleanArray): StreakDays {
    var index = closed.lastIndex
    if (index >= 0 && !closed[index]) index--
    var run = 0
    while (index >= 0 && closed[index]) {
        run++
        index--
    }
    return StreakDays(days = run, atHistoryLimit = run > 0 && index < 0)
}

/**
 * The time of day each item is normally recorded at. Only items with enough
 * entries, spread over enough days, and consistent enough to predict anything
 * get one, so a habit time is evidence rather than a guess.
 */
fun habitMinutes(
    entries: List<RecordEntry>,
    today: LocalDate,
): Map<Long, Int> = entries
    .asSequence()
    .filter { it.itemMode == RecordMode.ACCUMULATE && it.localDate != today.toString() }
    .groupBy(RecordEntry::itemId)
    .mapNotNull { (itemId, itemEntries) ->
        if (itemEntries.size < MIN_HABIT_ENTRIES) return@mapNotNull null
        if (itemEntries.distinctBy(RecordEntry::localDate).size < MIN_HABIT_DAYS) return@mapNotNull null
        val minutes = itemEntries.map(RecordEntry::localMinute)
        val center = circularMeanMinute(minutes)
        val spread = minutes.sumOf { circularMinuteDistance(it, center).toLong() } / minutes.size
        if (spread > MAX_HABIT_SPREAD_MINUTES) return@mapNotNull null
        itemId to center
    }
    .toMap()

/** Mean of minutes on a circular day, via unit vectors so midnight is not a wall. */
private fun circularMeanMinute(minutes: List<Int>): Int {
    require(minutes.isNotEmpty())
    val radiansPerMinute = 2.0 * Math.PI / MINUTES_PER_DAY
    val x = minutes.sumOf { cos(it * radiansPerMinute) }
    val y = minutes.sumOf { sin(it * radiansPerMinute) }
    val angle = atan2(y, x)
    val minute = (angle / radiansPerMinute).roundToInt()
    return ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
}

/** Local minute a subtask was completed at, for on-time feedback. */
fun completionMinute(completedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
    val time = Instant.ofEpochMilli(completedAt).atZone(zone)
    return time.hour * 60 + time.minute
}
