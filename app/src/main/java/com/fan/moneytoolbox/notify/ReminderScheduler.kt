package com.fan.moneytoolbox.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fan.moneytoolbox.data.ParkingMath
import com.fan.moneytoolbox.data.SettingsRepository

/**
 * 一次只排一个最近的精确闹钟;触发后再排下一个。
 * 进程被杀、手机重启(BootReceiver)都不影响。
 */
object ReminderScheduler {

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePendingIntent(context: Context, remindTimeMs: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 2001,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_REMIND_MS, remindTimeMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 读取会话并安排下一个提醒;没有会话或没有未来提醒时什么都不做 */
    fun scheduleNext(context: Context) {
        val session = SettingsRepository(context).sessionBlocking() ?: return
        val next = ParkingMath.nextReminderMs(session, System.currentTimeMillis()) ?: return

        val am = alarmManager(context)
        val pi = firePendingIntent(context, next)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            // 未授予精确闹钟权限时的兜底:仍会提醒,但时间可能有几分钟偏差
            am.setWindow(AlarmManager.RTC_WAKEUP, next, 60_000L, pi)
        }
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(firePendingIntent(context, 0L))
    }
}
