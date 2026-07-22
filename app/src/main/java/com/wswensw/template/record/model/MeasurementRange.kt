package com.wswensw.template.record.model

import java.time.Instant
import java.time.ZoneId

enum class MeasurementRange(
    val label: String,
    private val durationMillis: Long?,
) {
    LAST_24_HOURS("24 h", 24 * 60 * 60 * 1_000L),
    LAST_7_DAYS("7 d", 7 * 24 * 60 * 60 * 1_000L),
    LAST_30_DAYS("30 d", 30 * 24 * 60 * 60 * 1_000L),
    ALL("All", null),
    ;

    fun startAt(now: Long): Long? = durationMillis?.let(now::minus)

    fun queryStartDate(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        startAt(now)
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
            ?: "0001-01-01"

    fun contains(occurredAt: Long, now: Long): Boolean =
        occurredAt <= now && (startAt(now)?.let { occurredAt >= it } ?: true)
}
