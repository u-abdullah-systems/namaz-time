package com.example.azan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.CachedAlarmSnapshot
import com.example.domain.PrayerScheduleResolver
import com.example.domain.ResolvedPrayerTiming
import java.util.Calendar

object AzanScheduler {
    private const val TAG = "AzanScheduler"
    private const val REQUEST_CODE_AZAN = 7001

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun scheduleNextAzan(context: Context, snapshot: CachedAlarmSnapshot): ResolvedPrayerTiming? {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!snapshot.azanVoiceEnabled || snapshot.enabledPrayers.isEmpty()) {
            cancelScheduledAzan(appContext)
            return null
        }

        val now = System.currentTimeMillis()
        val todayCal = Calendar.getInstance()
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val todayTimings = PrayerScheduleResolver.resolveForDay(
            dayCalendar = todayCal,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
            method = snapshot.calculationMethod,
            asrMethod = snapshot.asrMethod,
            masjidSchedule = snapshot.cachedMasjidSchedule
        )
        val tomorrowTimings = PrayerScheduleResolver.resolveForDay(
            dayCalendar = tomorrowCal,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
            method = snapshot.calculationMethod,
            asrMethod = snapshot.asrMethod,
            masjidSchedule = snapshot.cachedMasjidSchedule
        )

        val nextTiming = (todayTimings + tomorrowTimings)
            .sortedBy { it.azanEpochMillis }
            .firstOrNull { timing ->
                timing.prayer in snapshot.enabledPrayers && timing.azanEpochMillis > now + 2000L
            } ?: return null

        val alarmIntent = Intent(appContext, AzanAlarmReceiver::class.java).apply {
            action = AzanAlarmReceiver.ACTION_AZAN_TRIGGER
            putExtra(AzanPlaybackService.EXTRA_PRAYER_NAME, nextTiming.prayer.name)
            putExtra(AzanPlaybackService.EXTRA_JAMAAT_OFFSET_MIN, nextTiming.jamaatOffsetMinutes)
            putExtra(AzanPlaybackService.EXTRA_AZAN_EPOCH_MILLIS, nextTiming.azanEpochMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_AZAN,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms(appContext)) {
                val clockInfo = AlarmManager.AlarmClockInfo(nextTiming.azanEpochMillis, showIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTiming.azanEpochMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled ${nextTiming.prayer.displayName} Azan at ${nextTiming.azanFormatted}")
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied; falling back to inexact allowWhileIdle", se)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTiming.azanEpochMillis,
                pendingIntent
            )
        }

        return nextTiming
    }

    fun cancelScheduledAzan(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, AzanAlarmReceiver::class.java).apply {
            action = AzanAlarmReceiver.ACTION_AZAN_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_AZAN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
