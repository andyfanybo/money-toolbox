package com.fan.moneytoolbox.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.fan.moneytoolbox.data.RenewalItem
import com.fan.moneytoolbox.data.RenewalMath
import com.fan.moneytoolbox.data.RenewalRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 每个项目分别排提前提醒与到期当天提醒，已处理的周期会取消旧闹钟。 */
object RenewalScheduler {
    private const val EARLY = "early"
    private const val DUE = "due"

    private fun pendingIntent(context: Context, item: RenewalItem, phase: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RenewalReceiver::class.java)
                .setAction(RenewalReceiver.ACTION_FIRE)
                .setData(Uri.parse("moneybox://renewal/${item.id}/$phase"))
                .putExtra(RenewalReceiver.EXTRA_ITEM_ID, item.id)
                .putExtra(RenewalReceiver.EXTRA_PERIOD_INDEX, item.periodIndex)
                .putExtra(RenewalReceiver.EXTRA_PHASE, phase),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun cancel(context: Context, item: RenewalItem) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, item, EARLY))
        manager.cancel(pendingIntent(context, item, DUE))
    }

    fun schedule(context: Context, item: RenewalItem) {
        if (item.completed) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        fun set(date: LocalDate, phase: String) {
            val atMs = date.atTime(LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (atMs <= System.currentTimeMillis()) return
            val pi = pendingIntent(context, item, phase)
            if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
        if (item.remindDaysBefore > 0) set(RenewalMath.reminderDate(item), EARLY)
        set(RenewalMath.dueDate(item), DUE)
    }

    fun restoreAll(context: Context) {
        RenewalRepository(context).itemsBlocking().forEach { item ->
            cancel(context, item)
            schedule(context, item)
        }
    }
}
