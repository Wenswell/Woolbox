package com.wswensw.woolbox.record.model

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordNowTest {
    @Test
    fun clockUsesLocalDateAndMinute() {
        val clock = RecordsClock.from(LocalDateTime.of(2026, 8, 18, 14, 32))

        assertEquals(LocalDate.of(2026, 8, 18), clock.date)
        assertEquals(14 * 60 + 32, clock.minuteOfDay)
    }
}
