package com.example.azan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.LocalScheduleCache
import com.example.data.RemoteScheduleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
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
                    pendingResult.finish()
                }
            }
        }
    }
}
