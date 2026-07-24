package com.wswensw.woolbox.record.model

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
            horizon = com.wswensw.woolbox.record.model.TaskHorizon.TODAY,
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
}
