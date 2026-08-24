package com.wswensw.woolbox.record.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wswensw.woolbox.record.model.DailyCheckin
import com.wswensw.woolbox.record.model.nextReminderAt
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HourlyCheckinReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    fun reconcile(checkin: DailyCheckin?, now: Long = System.currentTimeMillis()) {
        val nextReminderAt = checkin?.nextReminderAt(now)
        if (nextReminderAt == null) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            HourlyCheckinNotifications.cancel(applicationContext)
            return
        }
        val request = OneTimeWorkRequestBuilder<HourlyCheckinReminderWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay((nextReminderAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_LOCAL_DATE, checkin.localDate.toString())
                    .putLong(KEY_REMINDER_AT, nextReminderAt)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        internal const val KEY_LOCAL_DATE = "local_date"
        internal const val KEY_REMINDER_AT = "reminder_at"
        private const val UNIQUE_WORK = "records-hourly-checkin"
    }
}
