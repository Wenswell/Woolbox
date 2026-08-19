package com.wswensw.woolbox.record.reminder

import com.wswensw.woolbox.record.model.DailyFocus
import com.wswensw.woolbox.record.model.DailyFocusState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyReminderSchedulerTest {
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun activeFocusProducesRevisionBoundReminder() {
        assertEquals(
            DailyReminderSpec(today.toString(), 7, 3_600_000),
            dailyReminderSpec(focus(), today),
        )
    }

    @Test
    fun inactiveOrDifferentDayHasNoReminder() {
        assertNull(dailyReminderSpec(focus().copy(state = DailyFocusState.PAUSED, nextReviewAt = null), today))
        assertNull(dailyReminderSpec(focus().copy(localDate = today.minusDays(1)), today))
    }

    @Test
    fun deliveryTokenChangesWithServerReminderIdentity() {
        val spec = DailyReminderSpec(today.toString(), 7, 3_600_000)

        assertEquals("2026-08-18|7|3600000", spec.deliveryToken())
        assertNotEquals(spec.deliveryToken(), spec.copy(revision = 8).deliveryToken())
        assertNotEquals(spec.deliveryToken(), spec.copy(nextReviewAt = 7_200_000).deliveryToken())
    }

    private fun focus() = DailyFocus(
        id = 1,
        localDate = today,
        firstMove = "Go to the library",
        firstMoveCompletedAt = 0,
        primaryTaskId = 1,
        secondaryTaskId = null,
        currentTaskId = 1,
        state = DailyFocusState.ACTIVE,
        pauseReason = null,
        completedRounds = 0,
        nextReviewAt = 3_600_000,
        revision = 7,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
    )
}
