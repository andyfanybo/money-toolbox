package com.fan.moneytoolbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.fan.moneytoolbox.data.SettingsRepository

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val remindMs = intent.getLongExtra(EXTRA_REMIND_MS, 0L)
                val session = SettingsRepository(context).sessionBlocking()
                if (session != null && remindMs > 0L) {
                    Notifier.notifyReminder(context, session, remindMs)
                }
                // 无论有没有会话都重新调度:会话不存在时调度器内部会直接返回
                ReminderScheduler.scheduleNext(context)
            }
            ACTION_STOP -> {
                ReminderScheduler.cancel(context)
                kotlinx.coroutines.runBlocking {
                    SettingsRepository(context).saveSession(null)
                }
                NotificationManagerCompat.from(context).cancel(Notifier.REMIND_NOTIF_ID)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.fan.moneytoolbox.ACTION_FIRE_REMINDER"
        const val ACTION_STOP = "com.fan.moneytoolbox.ACTION_STOP_SESSION"
        const val EXTRA_REMIND_MS = "remind_ms"
    }
}
