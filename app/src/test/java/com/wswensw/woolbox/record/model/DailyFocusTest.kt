package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyFocusTest {
    @Test
    fun reviewIsDueOnlyForActiveFocusAtBoundary() {
        val focus = focus(nextReviewAt = 10_000)

        assertFalse(focus.isReviewDue(9_999))
        assertTrue(focus.isReviewDue(10_000))
        assertFalse(focus.copy(state = DailyFocusState.PAUSED).isReviewDue(10_000))
    }

    @Test
    fun currentAndSelectedOpenTasksComeFromTodayTasks() {
        val completed = task(1, completedAt = 10)
        val current = task(2)
        val sideTask = task(3)
        val focus = focus(primaryTaskId = 1, secondaryTaskId = 2, currentTaskId = 2)
        val tasks = listOf(completed, current, sideTask)

        assertEquals(current, focus.currentTask(tasks))
        assertEquals(listOf(2L), focus.selectedOpenTasks(tasks).map(TodayTask::id))
    }

    private fun focus(
        nextReviewAt: Long? = null,
        primaryTaskId: Long = 1,
        secondaryTaskId: Long? = null,
        currentTaskId: Long? = 1,
    ) = DailyFocus(
        id = 1,
        localDate = LocalDate.of(2026, 8, 18),
        firstMove = "Go to the library",
        firstMoveCompletedAt = 1,
        primaryTaskId = primaryTaskId,
        secondaryTaskId = secondaryTaskId,
        currentTaskId = currentTaskId,
        state = DailyFocusState.ACTIVE,
        pauseReason = null,
        completedRounds = 0,
        nextReviewAt = nextReviewAt,
        revision = 0,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun task(id: Long, completedAt: Long? = null) = TodayTask(
        id = id,
        localDate = LocalDate.of(2026, 8, 18),
        templateId = id,
        sourceTaskId = null,
        title = "Task $id",
        position = id.toInt(),
        completedAt = completedAt,
        revision = 0,
        createdAt = 0,
        updatedAt = 0,
        steps = emptyList(),
    )
}
