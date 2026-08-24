package com.wswensw.woolbox.record.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wswensw.woolbox.record.data.RecordRepository
import com.wswensw.woolbox.record.model.currentCheckinHour
import java.time.LocalDate

class HourlyCheckinReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val localDate = inputData.getString(HourlyCheckinReminderScheduler.KEY_LOCAL_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        if (localDate != LocalDate.now()) return Result.success()
        val checkin = try {
            RecordRepository(applicationContext).getDailyCheckin(localDate)
        } catch (_: Exception) {
            return Result.retry()
        } ?: return Result.success()
        if (checkin.currentCheckinHour(System.currentTimeMillis()) != null) {
            DailyReminderNotifications.showHourlyCheckin(applicationContext)
        }
        HourlyCheckinReminderScheduler(applicationContext).reconcile(checkin)
        return Result.success()
    }
}
