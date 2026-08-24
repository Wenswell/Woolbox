package com.wswensw.woolbox.record.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyCheckinTest {
    private val date = LocalDate.of(2026, 8, 18)
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun wakeStartsCheckinsAtTheFollowingLocalHour() {
        val checkin = DailyCheckin(
            localDate = date,
            wokeAt = at(8, 35),
            hourlyCheckins = emptyList(),
        )

        assertNull(checkin.currentCheckinHour(at(8, 59), zone))
        assertEquals(at(9, 0), checkin.nextReminderAt(at(8, 35), zone))
        assertEquals(9, checkin.currentCheckinHour(at(9, 0), zone))
    }

    @Test
    fun onlyTheCurrentHourCanBeCheckedIn() {
        val checkin = DailyCheckin(
            localDate = date,
            wokeAt = at(8, 35),
            hourlyCheckins = listOf(HourlyCheckin(hour = 9, checkedInAt = at(9, 8))),
        )

        assertNull(checkin.currentCheckinHour(at(9, 50), zone))
        assertEquals(10, checkin.currentCheckinHour(at(10, 15), zone))
    }

    private fun at(hour: Int, minute: Int) = ZonedDateTime.of(
        date,
        LocalTime.of(hour, minute),
        zone,
    ).toInstant().toEpochMilli()
}
