package com.wswensw.woolbox.record.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class HourlyCheckin(
    val hour: Int,
    val checkedInAt: Long,
)

data class DailyCheckin(
    val localDate: LocalDate,
    val wokeAt: Long,
    val hourlyCheckins: List<HourlyCheckin>,
)

fun DailyCheckin.currentCheckinHour(now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Int? {
    val localNow = Instant.ofEpochMilli(now).atZone(zoneId)
    if (localNow.toLocalDate() != localDate) return null
    val firstCheckin = Instant.ofEpochMilli(wokeAt).atZone(zoneId).truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1)
    if (localNow < firstCheckin) return null
    return localNow.hour.takeUnless { hour -> hourlyCheckins.any { it.hour == hour } }
}

fun DailyCheckin.nextReminderAt(now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    val localNow = Instant.ofEpochMilli(now).atZone(zoneId)
    if (localNow.toLocalDate() != localDate) return null
    val nextHour: ZonedDateTime = localNow.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1)
    return nextHour.takeIf { it.toLocalDate() == localDate }?.toInstant()?.toEpochMilli()
}
