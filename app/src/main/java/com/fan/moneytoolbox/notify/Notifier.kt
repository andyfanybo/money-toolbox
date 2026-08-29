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
            description = "免费时长到期提醒与收费提醒"
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

    private fun paidActionIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 3002,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_PAID),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 一条提醒的文案与可用操作 */
    data class ReminderCopy(val title: String, val text: String, val showPaidAction: Boolean)

    /** 判断该提醒时刻是否为"出场宽限结束"提醒 */
    private fun isCoverageEndRemind(s: ParkingSession, remindMs: Long): Boolean {
        if (ParkingMath.effectivePaidAtMs(s) <= 0) return false
        return abs(remindMs - ParkingMath.coverageEndMs(s)) < 30_000
    }

    /**
     * 根据被触发的提醒时刻,生成提醒的标题与正文。
     * 三类: 免费到期提醒 / 出场宽限结束提醒 / 收费提醒。
     * 供通知与弹窗(ReminderActivity)共用。
     */
    fun reminderCopy(session: ParkingSession, remindTimeMs: Long): ReminderCopy {
        val cfg = session.config
        val freeEnd = ParkingMath.freeEndMs(session)
        val unitMs = cfg.billingUnitMinutes.toLong() * ParkingMath.MINUTE_MS
        return when {
            remindTimeMs < freeEnd -> {
                val remainMin = ((freeEnd - remindTimeMs) / ParkingMath.MINUTE_MS).toInt()
                ReminderCopy(
                    "免费停车即将结束 ⏰",
                    "免费时长还剩约 $remainMin 分钟(${clock(freeEnd)} 到期)。" +
                        "及时驶出一分钱不花;继续停放将按 ¥${cfg.rateYuan}/${ParkingMath.unitText(cfg)} 计费。",
                    showPaidAction = false,
                )
            }
            isCoverageEndRemind(session, remindTimeMs) -> {
                val paidAt = ParkingMath.effectivePaidAtMs(session)
                val covEnd = ParkingMath.coverageEndMs(session)
                ReminderCopy(
                    "出场时间即将结束 ⏰",
                    "您在 ${clock(paidAt)} 完成缴费,${cfg.exitGraceMinutes} 分钟出场宽限将于 ${clock(covEnd)} 结束。" +
                        "若仍未驶出,将从现在起按下一周期计费(¥${cfg.rateYuan}/${ParkingMath.unitText(cfg)})。",
                    showPaidAction = true,
                )
            }
            else -> {
                val anchor = if (ParkingMath.effectivePaidAtMs(session) > 0) ParkingMath.coverageEndMs(session)
                else freeEnd
                val k = ((remindTimeMs + ParkingMath.payBufferMs(cfg) - anchor + unitMs / 2) / unitMs).toInt()
                val boundary = remindTimeMs + ParkingMath.payBufferMs(cfg)
                ReminderCopy(
                    "现在缴费出场,立省 ¥${cfg.rateYuan} 💰",
                    "当前按 $k 个计费周期收费(约 ¥${k * cfg.rateYuan})。" +
                        "在 ${clock(boundary)} 前完成缴费,就只按这些周期收费;缴费后另有 ${cfg.exitGraceMinutes} 分钟出场时间。" +
                        "还要继续停的话忽略本条即可。",
                    showPaidAction = true,
                )
            }
        }
    }

    /**
     * 发出到点提醒。
     * @param mode 提醒方式: 通知 / 闹钟和提醒(锁屏全屏) / 弹出窗口(通知兜底 + 由 Receiver 拉起弹窗)
     */
    fun notifyReminder(context: Context, session: ParkingSession, remindTimeMs: Long, mode: RemindMode) {
        val copy = reminderCopy(session, remindTimeMs)
        val builder = NotificationCompat.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "结束本次停车", stopActionIntent(context))

        if (copy.showPaidAction) {
            builder.addAction(0, "已缴费,稍后驶出", paidActionIntent(context))
        }

        if (mode == RemindMode.FULL_SCREEN) {
            val fullScreenPi = PendingIntent.getActivity(
                context, 4001,
                Intent(context, ReminderActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ReminderActivity.EXTRA_TITLE, copy.title)
                    .putExtra(ReminderActivity.EXTRA_TEXT, copy.text)
                    .putExtra(ReminderActivity.EXTRA_SHOW_PAID, copy.showPaidAction),
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
