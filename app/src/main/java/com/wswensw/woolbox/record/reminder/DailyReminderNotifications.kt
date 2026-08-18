package com.wswensw.woolbox.record.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wswensw.woolbox.MainActivity
import com.wswensw.woolbox.R
import java.time.Duration
import java.time.ZonedDateTime

object DailyReminderNotifications {
    const val EXTRA_OPEN_DAILY_REVIEW = "open_daily_review"

    fun areEnabled(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun cancelReview(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun showReview(context: Context): Boolean {
        if (!areEnabled(context)) return false
        val now = ZonedDateTime.now()
        val timeoutAfter = Duration.between(
            now,
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone),
        ).toMillis()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.daily_review_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_DAILY_REVIEW, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_records_24)
                .setContentTitle(context.getString(R.string.hourly_review))
                .setContentText(context.getString(R.string.daily_review_notification))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setTimeoutAfter(timeoutAfter)
                .build(),
        )
        return true
    }

    private const val CHANNEL_ID = "records_daily_review"
    private const val NOTIFICATION_ID = 29_001
    private const val REQUEST_CODE = 29_001
}
