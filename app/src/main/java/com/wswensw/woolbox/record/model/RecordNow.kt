package com.wswensw.woolbox.record.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Local wall-clock snapshot driving every time-aware Records surface. */
data class RecordsClock(
    val date: LocalDate,
    val minuteOfDay: Int,
) {
    companion object {
        fun from(dateTime: LocalDateTime) = RecordsClock(
            date = dateTime.toLocalDate(),
            minuteOfDay = dateTime.hour * 60 + dateTime.minute,
        )

        fun now(): RecordsClock = from(LocalDateTime.now())
    }
}
