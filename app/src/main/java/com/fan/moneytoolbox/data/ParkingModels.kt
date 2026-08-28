package com.fan.moneytoolbox.data

import kotlin.math.ceil
import kotlin.math.max

/**
 * 停车收费提醒的配置。
 *
 * 计费模型(与绝大多数停车场一致):
 * - 免费时长 N 分钟,超出后开始计费;
 * - 按计费单元(通常 1 小时)收费,不足一个单元按一个单元收 —— 即超出 1 分钟也要多付一个单元;
 * - 缴费后通常有 10~15 分钟的出场宽限时间。
 *
 * 因此"省钱缴费"的最佳时机是: 每个计费周期截止前的 [exitGraceMinutes] 分钟。
 * 例如免费 30 分钟、6 元/小时、宽限 15 分钟:
 *   13:00 入场 → 15:45 提醒缴费并驶出(16:00 前出场),只付 6 元;
 *   若拖到 16:01 之后出场,则要付 12 元。
 */
data class ParkingConfig(
    /** 免费时长(分钟),0 表示没有免费时长 */
    val freeMinutes: Int = 30,
    /** 每个计费单元的费用(元) */
    val rateYuan: Int = 6,
    /** 计费单元(分钟),常见为 60(按小时)或 30 */
    val billingUnitMinutes: Int = 60,
    /** 缴费后的出场宽限时间(分钟) */
    val exitGraceMinutes: Int = 15,
    /** 免费到期前多少分钟提醒 */
    val remindBeforeFreeMinutes: Int = 10,
)

/** 一次停车记录(入场时间 + 当时锁定的配置,避免后续修改默认值影响进行中的会话) */
data class ParkingSession(
    val entryEpochMs: Long,
    val config: ParkingConfig,
)

/** 纯函数的计费/提醒时间计算,便于单元测试 */
object ParkingMath {

    const val MINUTE_MS: Long = 60_000L

    /** 免费时段截止时刻 */
    fun freeEndMs(s: ParkingSession): Long =
        s.entryEpochMs + s.config.freeMinutes * MINUTE_MS

    /**
     * "省钱提醒"相对计费周期截止时刻的提前量。
     * 原则上等于出场宽限时间;若宽限 >= 计费单元则收敛到 (单元 - 1) 分钟,保证至少有 1 分钟意义。
     */
    fun saveLeadMs(cfg: ParkingConfig): Long =
        cfg.exitGraceMinutes.coerceIn(1, (cfg.billingUnitMinutes - 1).coerceAtLeast(1)) * MINUTE_MS

    /** 第 k 次"省钱提醒"的时刻(k >= 1):免费截止 + k 个计费单元 - 提前量 */
    fun kthSaveRemindMs(s: ParkingSession, k: Int): Long =
        freeEndMs(s) + k.toLong() * s.config.billingUnitMinutes * MINUTE_MS - saveLeadMs(s.config)

    /** "免费即将到期"提醒时刻;无免费时长或提前量不合法时返回 null */
    fun freeRemindMs(s: ParkingSession): Long? {
        val cfg = s.config
        if (cfg.freeMinutes <= 0 || cfg.remindBeforeFreeMinutes <= 0) return null
        if (cfg.remindBeforeFreeMinutes >= cfg.freeMinutes) return null
        return s.entryEpochMs + (cfg.freeMinutes - cfg.remindBeforeFreeMinutes) * MINUTE_MS
    }

    /** 下一个需要闹钟提醒的时刻(不含已经过去的),没有则返回 null */
    fun nextReminderMs(s: ParkingSession, nowMs: Long): Long? {
        freeRemindMs(s)?.let { if (it > nowMs) return it }
        val unitMs = s.config.billingUnitMinutes.toLong() * MINUTE_MS
        var k = ((nowMs - freeEndMs(s)) / unitMs).toInt() + 1
        if (k < 1) k = 1
        // 上限保护:最多往未来找 31 天(每小时一个周期约 744 个)
        var guard = 0
        while (guard++ < 24 * 31) {
            val t = kthSaveRemindMs(s, k)
            if (t > nowMs) return t
            k++
        }
        return null
    }

    /** 当前停车状态(用于界面展示) */
    data class Status(
        val inFree: Boolean,
        /** 免费时段剩余毫秒(inFree 时有效) */
        val freeRemainMs: Long,
        /** 免费时段已用比例 0..1 */
        val freeUsedFraction: Float,
        /** 已进入计费的时长(毫秒) */
        val paidElapsedMs: Long,
        /** 当前已计费的单元数 */
        val paidUnits: Int,
        /** 按当前时刻估算的应付费用(元) */
        val currentCostYuan: Int,
        /** 当前计费周期截止时刻(超过将多付一个单元) */
        val nextBoundaryMs: Long,
        /** 省钱窗口开始时刻 */
        val saveWindowStartMs: Long,
        /** 是否正处于省钱窗口(现在缴费可省一个单元的钱) */
        val inSaveWindow: Boolean,
        /** 拖过当前周期后的费用(元) */
        val nextUnitCostYuan: Int,
    )

    fun status(s: ParkingSession, nowMs: Long): Status {
        val cfg = s.config
        val freeEnd = freeEndMs(s)
        if (nowMs < freeEnd) {
            val total = cfg.freeMinutes * MINUTE_MS
            return Status(
                inFree = true,
                freeRemainMs = freeEnd - nowMs,
                freeUsedFraction = ((nowMs - s.entryEpochMs).toFloat() / total.toFloat()).coerceIn(0f, 1f),
                paidElapsedMs = 0,
                paidUnits = 0,
                currentCostYuan = 0,
                nextBoundaryMs = freeEnd,
                saveWindowStartMs = freeEnd,
                inSaveWindow = false,
                nextUnitCostYuan = cfg.rateYuan,
            )
        }
        val paid = nowMs - freeEnd
        val unitMs = cfg.billingUnitMinutes * MINUTE_MS
        val k = ceil(paid.toDouble() / unitMs).toInt().coerceAtLeast(1)
        val boundary = freeEnd + k.toLong() * unitMs
        val windowStart = boundary - saveLeadMs(cfg)
        return Status(
            inFree = false,
            freeRemainMs = 0,
            freeUsedFraction = 1f,
            paidElapsedMs = paid,
            paidUnits = k,
            currentCostYuan = k * cfg.rateYuan,
            nextBoundaryMs = boundary,
            saveWindowStartMs = windowStart,
            inSaveWindow = nowMs >= windowStart,
            nextUnitCostYuan = (k + 1) * cfg.rateYuan,
        )
    }

    /** 提醒时间轴条目(界面展示用) */
    data class ReminderItem(
        val timeMs: Long,
        val label: String,
        /** 是否为"省钱缴费"类提醒 */
        val isSaveRemind: Boolean,
        val passed: Boolean,
    )

    /** 时间轴展示: 最近一条已过的提醒(提供上下文) + 接下来的提醒 */
    fun reminderTimeline(s: ParkingSession, nowMs: Long, maxItems: Int = 4): List<ReminderItem> {
        val items = mutableListOf<ReminderItem>()
        if (s.config.freeMinutes > 0) {
            val freeEnd = freeEndMs(s)
            items += ReminderItem(freeEnd, "免费时长到期", isSaveRemind = false, passed = freeEnd <= nowMs)
        }
        var k = 1
        while (k <= maxItems + 2) {
            val t = kthSaveRemindMs(s, k)
            items += ReminderItem(t, "省钱提醒 #$k(缴费驶出可省 ¥${s.config.rateYuan})", isSaveRemind = true, passed = t <= nowMs)
            k++
        }
        val sorted = items.sortedBy { it.timeMs }
        val result = mutableListOf<ReminderItem>()
        sorted.lastOrNull { it.passed }?.let { result += it }
        result += sorted.filter { !it.passed }.take(maxItems - result.size)
        return result
    }

    /** 单元时间的展示文案 */
    fun unitText(cfg: ParkingConfig): String =
        if (cfg.billingUnitMinutes % 60 == 0) "${cfg.billingUnitMinutes / 60} 小时" else "${cfg.billingUnitMinutes} 分钟"
}
