package com.fan.moneytoolbox.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fan.moneytoolbox.R
import com.fan.moneytoolbox.RenewalActivity
import com.fan.moneytoolbox.data.RenewalItem
import com.fan.moneytoolbox.data.RenewalMath

object RenewalNotifier {
    private const val CHANNEL_ID = "renewal_reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "续费提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "停车费、会员等项目的提前与到期提醒"
            }
        )
    }

    fun cancel(context: Context, item: RenewalItem) {
        NotificationManagerCompat.from(context).cancel(item.id.hashCode())
    }

    fun notify(context: Context, item: RenewalItem, dueToday: Boolean) {
        val date = RenewalMath.dueDate(item)
        val title = if (dueToday) "${item.name} 今天到期" else "${item.name} 即将到期"
        val amount = item.amountCents?.let {
            val yuan = it / 100
            val cents = (it % 100).toString().padStart(2, '0')
            "，金额 ¥$yuan.$cents"
        } ?: ""
        val message = "${item.category.label}将于 $date 续费$amount。请确认是否需要缴费或取消自动续费。"
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, RenewalActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // 未授予通知权限时仍可在应用内查看到期状态。
        }
    }
}
