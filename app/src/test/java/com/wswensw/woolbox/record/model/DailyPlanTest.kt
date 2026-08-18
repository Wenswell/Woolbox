package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanTest {
    @Test
    fun reviewIsDueOnlyForActivePlanAtBoundary() {
        val plan = plan(nextReviewAt = 10_000)

        assertFalse(plan.isReviewDue(9_999))
        assertTrue(plan.isReviewDue(10_000))
        assertFalse(plan.copy(state = DailyPlanState.ENOUGH).isReviewDue(10_000))
    }

    @Test
    fun commitmentsFollowPrimaryThenSecondaryAndIgnoreMissingTasks() {
        val primary = task(1)
        val secondary = task(2)

        assertEquals(listOf(1L, 2L), plan().committedTasks(listOf(secondary, primary)).map(TodoTask::id))
        assertEquals(listOf(1L), plan().committedTasks(listOf(primary)).map(TodoTask::id))
    }

    private fun plan(nextReviewAt: Long? = null) = DailyPlan(
        id = 1,
        localDate = LocalDate.of(2026, 8, 18),
        firstMove = "Go to the library",
        firstMoveCompletedAt = 1,
        primaryTaskId = 1,
        secondaryTaskId = 2,
        currentTaskId = 1,
        state = DailyPlanState.ACTIVE,
        completedRounds = 0,
        nextReviewAt = nextReviewAt,
        revision = 0,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun task(id: Long) = TodoTask(id, "Task $id", null, 0, 0, emptyList())
}
