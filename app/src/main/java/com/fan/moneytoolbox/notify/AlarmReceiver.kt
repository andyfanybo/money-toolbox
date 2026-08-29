package com.fan.moneytoolbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.fan.moneytoolbox.ReminderActivity
import com.fan.moneytoolbox.data.RemindMode
import com.fan.moneytoolbox.data.SettingsRepository

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val remindMs = intent.getLongExtra(EXTRA_REMIND_MS, 0L)
                val repo = SettingsRepository(context)
                val session = repo.sessionBlocking()
                if (session != null && remindMs > 0L) {
                    val mode = repo.remindModeBlocking()
                    Notifier.notifyReminder(context, session, remindMs, mode)
                    if (mode == RemindMode.OVERLAY && Settings.canDrawOverlays(context)) {
                        val (title, text) = Notifier.reminderCopy(session, remindMs)
                        try {
                            // 已授予"显示在其他应用上层"权限,可从后台直接拉起弹窗
                            context.startActivity(
                                Intent(context, ReminderActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(ReminderActivity.EXTRA_TITLE, title)
                                    .putExtra(ReminderActivity.EXTRA_TEXT, text)
                            )
                        } catch (_: Exception) {
                            // 部分系统(如 MIUI 的"后台弹出界面"开关)限制后台启动,退回普通通知
                        }
                    }
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
