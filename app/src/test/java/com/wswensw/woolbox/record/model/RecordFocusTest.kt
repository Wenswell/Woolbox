package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordFocusTest {
    private val today = LocalDate.of(2026, 7, 26)

    @Test
    fun oneTaskContributesItsFirstUnfinishedStep() {
        val task = task(
            id = 5,
            plannedDate = today,
            steps = listOf("Prepare", "Pack"),
            completedSteps = 0,
        )

        val queue = focusQueue(emptyList(), listOf(task), clock(9 * 60))

        assertEquals(1, queue.size)
        assertEquals("Prepare", (queue.single().target as NowTarget.Task).step.title)
        assertEquals(FocusReason.Queued, queue.single().reason)
    }

    @Test
    fun completingTheFirstStepAdvancesTheSameTask() {
        val task = task(
            id = 5,
            plannedDate = today,
            steps = listOf("Prepare", "Pack"),
            completedSteps = 1,
        )

        val candidate = focusQueue(emptyList(), listOf(task), clock(9 * 60)).single()

        assertEquals("Pack", (candidate.target as NowTarget.Task).step.title)
    }

    @Test
    fun overdueTasksStayInFocusAndOldestComesFirst() {
        val yesterday = task(id = 1, plannedDate = today.minusDays(1), title = "Yesterday")
        val older = task(id = 2, plannedDate = today.minusDays(3), title = "Older")
        val dueToday = task(id = 3, plannedDate = today, title = "Today")

        val queue = focusQueue(emptyList(), listOf(yesterday, dueToday, older), clock(9 * 60))

        assertEquals(listOf(2L, 1L, 3L), queue.map { (it.target as NowTarget.Task).task.id })
        assertEquals(FocusReason.Overdue(3), queue.first().reason)
        assertEquals(FocusReason.Queued, queue.last().reason)
    }

    @Test
    fun futureAndUnscheduledTasksStayOutOfFocus() {
        val future = task(id = 1, plannedDate = today.plusDays(1))
        val unscheduled = task(id = 2, plannedDate = null)

        assertTrue(focusQueue(emptyList(), listOf(future, unscheduled), clock(9 * 60)).isEmpty())
    }

    @Test
    fun habitOnlyAppearsNearItsLearnedTime() {
        val item = accumulate(1, "Walk")
        val habits = mapOf(1L to 9 * 60)

        assertEquals(
            FocusReason.Habit(9 * 60, 20),
            focusQueue(listOf(item), emptyList(), clock(9 * 60 + 20), habitMinutes = habits).single().reason,
        )
        assertTrue(focusQueue(listOf(item), emptyList(), clock(13 * 60), habitMinutes = habits).isEmpty())
        assertTrue(
            focusQueue(
                listOf(item),
                emptyList(),
                clock(9 * 60),
                todayMinutes = mapOf(1L to listOf(9 * 60)),
                habitMinutes = habits,
            ).isEmpty(),
        )
    }

    @Test
    fun goalsUseProgressSignalsAndSatisfiedGoalsLeaveFocus() {
        val item = accumulate(1, "Water").copy(goalValue = 8.0, goalPeriod = GoalPeriod.DAY)

        assertEquals(
            FocusReason.BehindPace(13),
            focusQueue(listOf(item), emptyList(), clock(12 * 60), goalValues = mapOf(1L to 1.0)).single().reason,
        )
        assertTrue(
            focusQueue(listOf(item), emptyList(), clock(12 * 60), goalValues = mapOf(1L to 8.0)).isEmpty(),
        )
    }

    @Test
    fun deferralUsesTaskIdentityAndExpiresWithTheDay() {
        val task = task(id = 5, plannedDate = today)
        val key = focusTargetKey(NowTarget.Task(task, task.steps.single()))
        val deferred = DeferredTargets(today, setOf(key))

        val postponed = focusQueue(emptyList(), listOf(task), clock(9 * 60), deferred = deferred).single()
        assertTrue(postponed.isDeferred)
        assertEquals("task:5", key)
        assertTrue(deferred.on(today.plusDays(1)).isEmpty())
    }

    @Test
    fun focusCommitmentsDoNotDependOnTheMinute() {
        assertTrue(
            hasFocusCommitments(
                items = listOf(accumulate(1, "Walk").copy(goalValue = 1.0, goalPeriod = GoalPeriod.DAY)),
                tasks = emptyList(),
                today = today,
            ),
        )
        assertTrue(hasFocusCommitments(emptyList(), listOf(task(5, plannedDate = today)), today))
        assertTrue(
            hasFocusCommitments(
                items = listOf(accumulate(1, "Walk")),
                tasks = emptyList(),
                today = today,
                habitMinutes = mapOf(1L to 9 * 60),
            ),
        )
    }

    @Test
    fun habitTimeNeedsEvidenceAcrossDays() {
        val entries = (1..5).map { back ->
            entry(1, today.minusDays(back.toLong()), 9 * 60 + back)
        }

        assertEquals(9 * 60 + 3, habitMinutes(entries, today)[1L])
        assertTrue(habitMinutes(entries.take(4), today)[1L] == null)
    }

    private fun clock(minute: Int, date: LocalDate = today) = RecordsClock(date, minute)

    private fun task(
        id: Long,
        plannedDate: LocalDate?,
        title: String = "Task $id",
        steps: List<String> = listOf("Step"),
        completedSteps: Int = 0,
    ) = TodoTask(
        id = id,
        title = title,
        plannedDate = plannedDate,
        completedAt = if (completedSteps >= steps.size) 1L else null,
        createdAt = 0,
        updatedAt = 0,
        steps = steps.mapIndexed { index, stepTitle ->
            TodoStep(
                id = id * 100 + index,
                taskId = id,
                title = stepTitle,
                completedAt = if (index < completedSteps) 1L else null,
                createdAt = 0,
                updatedAt = 0,
            )
        },
    )

    private fun accumulate(id: Long, name: String) = RecordItem(
        id = id,
        name = name,
        description = "",
        mode = RecordMode.ACCUMULATE,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "count",
        goalValue = null,
        goalPeriod = null,
        priority = 0,
        archived = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun entry(itemId: Long, date: LocalDate, minute: Int) = RecordEntry(
        id = itemId * 1000 + date.dayOfYear,
        itemId = itemId,
        occurredAt = 0,
        localDate = date.toString(),
        localMinute = minute,
        value = 1.0,
        createdAt = 0,
        itemName = "Item $itemId",
        itemMode = RecordMode.ACCUMULATE,
        itemUnit = "count",
        itemValueType = RecordValueType.NUMBER,
    )
}
