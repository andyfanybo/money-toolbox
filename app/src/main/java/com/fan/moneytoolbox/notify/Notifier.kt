package com.fan.moneytoolbox.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fan.moneytoolbox.MainActivity
import com.fan.moneytoolbox.R
import com.fan.moneytoolbox.ReminderActivity
import com.fan.moneytoolbox.data.ParkingMath
import com.fan.moneytoolbox.data.ParkingSession
import com.fan.moneytoolbox.data.RemindMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object Notifier {

    const val CHANNEL_REMIND = "parking_reminders"
    const val REMIND_NOTIF_ID = 1001

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
    private fun clock(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hhmm)

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_REMIND,
            "停车提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "免费时长到期提醒与省钱缴费提醒"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun stopActionIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 3001,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 根据被触发的提醒时刻,生成提醒的标题与正文。
     * 供通知与弹窗(ReminderActivity)共用。
     */
    fun reminderCopy(session: ParkingSession, remindTimeMs: Long): Pair<String, String> {
        val cfg = session.config
        val freeEnd = ParkingMath.freeEndMs(session)
        val unitMs = cfg.billingUnitMinutes.toLong() * ParkingMath.MINUTE_MS
        return if (remindTimeMs < freeEnd) {
            // 免费到期提醒
            val remainMin = ((freeEnd - remindTimeMs) / ParkingMath.MINUTE_MS).toInt()
            "免费停车即将结束 ⏰" to
                "免费时长还剩约 $remainMin 分钟(${clock(freeEnd)} 到期)。" +
                "及时驶出一分钱不花;继续停放将按 ¥${cfg.rateYuan}/${ParkingMath.unitText(cfg)} 计费。"
        } else {
            // 省钱缴费提醒: 第 k 个计费周期截止前 saveLead
            val k = ((remindTimeMs + ParkingMath.saveLeadMs(cfg) - freeEnd + unitMs / 2) / unitMs).toInt()
            val deadline = clock(remindTimeMs + ParkingMath.saveLeadMs(cfg))
            "现在缴费出场,立省 ¥${cfg.rateYuan} 💰" to
                "当前按 $k 个计费周期收费(约 ¥${k * cfg.rateYuan})。" +
                "在 $deadline 前完成缴费并驶出,就不会被计入第 ${k + 1} 个周期(¥${(k + 1) * cfg.rateYuan})。" +
                "还要继续停的话忽略本条即可。"
        }
    }

    /**
     * 发出到点提醒。
     * @param mode 提醒方式: 通知 / 闹钟和提醒(锁屏全屏) / 弹出窗口(通知兜底 + 由 Receiver 拉起弹窗)
     */
    fun notifyReminder(context: Context, session: ParkingSession, remindTimeMs: Long, mode: RemindMode) {
        val (title, text) = reminderCopy(session, remindTimeMs)
        val builder = NotificationCompat.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "结束本次停车", stopActionIntent(context))

        if (mode == RemindMode.FULL_SCREEN) {
            val fullScreenPi = PendingIntent.getActivity(
                context, 4001,
                Intent(context, ReminderActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ReminderActivity.EXTRA_TITLE, title)
                    .putExtra(ReminderActivity.EXTRA_TEXT, text),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(fullScreenPi, true)
        }

        safeNotify(context, REMIND_NOTIF_ID, builder.build())
    }

    private fun safeNotify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // 用户拒绝了通知权限:应用内仍有倒计时展示,这里静默跳过
        }
    }

    /** 闹钟可能存在 ±几秒 的系统误差,判断两个时刻是否指同一次提醒 */
    fun sameReminder(a: Long, b: Long): Boolean = abs(a - b) < 30_000L
}
