package com.fan.moneytoolbox.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
    private val mmdd = DateTimeFormatter.ofPattern("M月d日")
    private val eWeek = DateTimeFormatter.ofPattern("E", Locale.CHINESE)

    fun clock(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hhmm)

    fun date(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(mmdd) +
            " " + Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(eWeek)

    fun dateTime(ms: Long): String = "${date(ms)} ${clock(ms)}"

    /** 倒计时: 45:32 / 1:05:32 */
    fun countdown(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 时长描述: 105 分钟 -> "1 小时 45 分" */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h <= 0 -> "${m} 分钟"
            m == 0L -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }

    fun elapsedDescription(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 1) "刚刚" else duration(minutes)
    }
}
