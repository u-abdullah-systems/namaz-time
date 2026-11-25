package com.example.azan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.data.LocalScheduleCache
import com.example.data.RemoteScheduleSource
import com.example.domain.PrayerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AzanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AzanPlaybackService.EXTRA_PRAYER_NAME) ?: PrayerName.FAJR.name
        val prayer = PrayerName.entries.firstOrNull { it.name == prayerName } ?: PrayerName.FAJR
        val offsetMin = intent.getIntExtra(AzanPlaybackService.EXTRA_JAMAAT_OFFSET_MIN, 15)
        val azanEpoch = intent.getLongExtra(AzanPlaybackService.EXTRA_AZAN_EPOCH_MILLIS, 0L)

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NamazTime:AlarmReceiver").apply {
            setReferenceCounted(false)
            acquire(10_000L)
        }

        if (AzanAlertState.claimPrayerTrigger(context, prayer, azanEpoch)) {
            val serviceIntent = Intent(context, AzanPlaybackService::class.java).apply {
                putExtra(AzanPlaybackService.EXTRA_PRAYER_NAME, prayer.name)
                putExtra(AzanPlaybackService.EXTRA_JAMAAT_OFFSET_MIN, offsetMin)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        // Schedule the subsequent prayer Azan in the background
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cache = LocalScheduleCache(context)
                val remote = RemoteScheduleSource(context).fetchOnce()
                if (remote != null) {
                    cache.saveMasjidSchedule(remote)
                }
                val snapshot = cache.getSnapshot()
                AzanScheduler.scheduleNextAzan(context, snapshot)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_AZAN_TRIGGER = "com.example.azan.ACTION_AZAN_TRIGGER"
    }
}
