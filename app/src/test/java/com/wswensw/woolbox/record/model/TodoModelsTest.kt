package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoModelsTest {
    @Test
    fun reportsDerivedStepProgress() {
        val task = TodoTask(
            id = 1,
            title = "Prepare trip",
            plannedDate = LocalDate.of(2026, 7, 26),
            completedAt = null,
            createdAt = 0,
            updatedAt = 0,
            steps = listOf(
                TodoStep(1, 1, "Book train", completedAt = 1, createdAt = 0, updatedAt = 0),
                TodoStep(2, 1, "Pack bag", completedAt = null, createdAt = 0, updatedAt = 0),
            ),
        )

        assertFalse(task.isCompleted)
        assertEquals(1, task.completedStepCount)
        assertTrue(task.steps.first().isCompleted)
    }

    @Test
    fun normalizesAndRequiresTaskTitles() {
        assertEquals("Prepare trip", validatedTaskTitle("  Prepare trip  "))
        assertThrows(RecordValidationException::class.java) { validatedTaskTitle(" ") }
    }

    @Test
    fun derivesTaskBucketFromPlannedDate() {
        val today = LocalDate.of(2026, 7, 26)
        val task = TodoTask(
            id = 1,
            title = "Plan",
            plannedDate = today,
            completedAt = null,
            createdAt = 0,
            updatedAt = 0,
            steps = emptyList(),
        )

        assertEquals(TaskDateBucket.OVERDUE, task.copy(plannedDate = today.minusDays(1)).dateBucket(today))
        assertEquals(TaskDateBucket.TODAY, task.dateBucket(today))
        assertEquals(TaskDateBucket.LATER, task.copy(plannedDate = today.plusDays(1)).dateBucket(today))
        assertEquals(TaskDateBucket.LATER, task.copy(plannedDate = null).dateBucket(today))
    }
}
