package com.fan.moneytoolbox.data

import kotlin.math.ceil

/** 提醒方式 */
enum class RemindMode {
    /** 普通系统通知 */
    NOTIFICATION,

    /** 闹钟和提醒: 锁屏时全屏亮起并响铃(系统全屏意图) */
    FULL_SCREEN,

    /** 弹出窗口提醒: 在其他应用上方弹出窗口 */
    OVERLAY,
}

/**
 * 停车收费提醒的配置。
 *
 * 计费模型:
 * - 免费时长 N 分钟,超出后开始计费;
 * - 按计费单元(通常 1 小时)收费,不足一个单元按一个单元收 —— 超出 1 分钟也要多付一个单元;
 * - 缴费后有出场宽限时间(10~15 分钟),宽限结束仍在场则重新按周期计费;
 * - 「缴费缓冲」: 收费提醒在每个计费周期截止前 B 分钟弹出(默认 2 分钟),
 *   此时缴费可锁定当前周期费用,并用宽限时间从容驶出。
 */
data class ParkingConfig(
    /** 免费时长(分钟),0 表示没有免费时长 */
    val freeMinutes: Int = 30,
    /** 每个计费单元的费用(元) */
    val rateYuan: Int = 6,
    /** 计费单元(分钟),常见为 60(按小时)或 30/15 */
    val billingUnitMinutes: Int = 60,
    /** 缴费后的出场宽限时间(分钟) */
    val exitGraceMinutes: Int = 15,
    /** 免费到期前多少分钟提醒 */
    val remindBeforeFreeMinutes: Int = 10,
    /** 缴费缓冲: 收费提醒在周期截止前多少分钟弹出 */
    val payBufferMinutes: Int = 2,
)

/** 一次停车记录(入场时间 + 当时锁定的配置 + 缴费标记) */
data class ParkingSession(
    val entryEpochMs: Long,
    val config: ParkingConfig,
    /** 最近一次标记"已缴费"的时刻;0 表示未缴费 */
    val paidAtMs: Long = 0,
)

/** 纯函数的计费/提醒时间计算,便于单元测试 */
object ParkingMath {

    const val MINUTE_MS: Long = 60_000L

    /** 免费时段截止时刻 */
    fun freeEndMs(s: ParkingSession): Long =
        s.entryEpochMs + s.config.freeMinutes * MINUTE_MS

    /** 缴费缓冲(毫秒),收敛到 [0, 计费单元-1] */
    fun payBufferMs(cfg: ParkingConfig): Long =
        cfg.payBufferMinutes.coerceIn(0, (cfg.billingUnitMinutes - 1).coerceAtLeast(1)) * MINUTE_MS

    /** 已缴费标记仅在进入计费阶段后有效(免费时段内不存在"缴费锁周期") */
    fun effectivePaidAtMs(s: ParkingSession): Long =
        if (s.paidAtMs >= freeEndMs(s)) s.paidAtMs else 0L

    /** 缴费后的出场宽限结束时刻(下次收费的周期锚点) */
    fun coverageEndMs(s: ParkingSession): Long =
        effectivePaidAtMs(s) + s.config.exitGraceMinutes * MINUTE_MS

    /** "免费即将到期"提醒时刻;无免费时长或提前量不合法时返回 null */
    fun freeRemindMs(s: ParkingSession): Long? {
        val cfg = s.config
        if (cfg.freeMinutes <= 0 || cfg.remindBeforeFreeMinutes <= 0) return null
        if (cfg.remindBeforeFreeMinutes >= cfg.freeMinutes) return null
        return s.entryEpochMs + (cfg.freeMinutes - cfg.remindBeforeFreeMinutes) * MINUTE_MS
    }

    /**
     * 第 k 次收费提醒 = 周期锚点 + k 个计费单元 − 缴费缓冲。
     * 未缴费时锚点是免费截止时刻;缴费且宽限结束后,锚点变为宽限结束时刻。
     */
    fun kthSaveRemindMs(s: ParkingSession, k: Int, anchor: Long = freeEndMs(s)): Long =
        anchor + k.toLong() * s.config.billingUnitMinutes * MINUTE_MS - payBufferMs(s.config)

    /** 下一个需要闹钟提醒的时刻(不含已经过去的),没有则返回 null */
    fun nextReminderMs(s: ParkingSession, nowMs: Long): Long? {
        freeRemindMs(s)?.let { if (it > nowMs) return it }
        val paid = effectivePaidAtMs(s)
        if (paid > 0) {
            val covEnd = coverageEndMs(s)
            // 已缴费: 先提醒"出场宽限结束",之后的收费提醒以宽限结束为新周期锚点
            if (covEnd > nowMs) return covEnd
            return nextSaveRemindAfter(s, nowMs, covEnd)
        }
        return nextSaveRemindAfter(s, nowMs, freeEndMs(s))
    }

    private fun nextSaveRemindAfter(s: ParkingSession, nowMs: Long, anchor: Long): Long? {
        val unitMs = s.config.billingUnitMinutes.toLong() * MINUTE_MS
        var k = ((nowMs - anchor) / unitMs).toInt() + 1
        if (k < 1) k = 1
        // 上限保护:最多往未来找 31 天
        var guard = 0
        while (guard++ < 24 * 31) {
            val t = kthSaveRemindMs(s, k, anchor)
            if (t > nowMs) return t
            k++
        }
        return null
    }

    /** 当前停车状态(用于界面展示) */
    data class Status(
        val inFree: Boolean,
        /** 已缴费、处于出场宽限时间内 */
        val inPaidWindow: Boolean,
        /** 免费时段剩余毫秒(inFree 时有效) */
        val freeRemainMs: Long,
        /** 免费时段已用比例 0..1 */
        val freeUsedFraction: Float,
        /** 出场宽限剩余毫秒(inPaidWindow 时有效) */
        val paidWindowRemainMs: Long,
        /** 出场宽限剩余比例 0..1 */
        val paidWindowFraction: Float,
        /** 当前周期锚点后已计时(毫秒) */
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
                inPaidWindow = false,
                freeRemainMs = freeEnd - nowMs,
                freeUsedFraction = ((nowMs - s.entryEpochMs).toFloat() / total.toFloat()).coerceIn(0f, 1f),
                paidWindowRemainMs = 0,
                paidWindowFraction = 0f,
                paidElapsedMs = 0,
                paidUnits = 0,
                currentCostYuan = 0,
                nextBoundaryMs = freeEnd,
                saveWindowStartMs = freeEnd,
                inSaveWindow = false,
                nextUnitCostYuan = cfg.rateYuan,
            )
        }
        val paid = effectivePaidAtMs(s)
        if (paid > 0) {
            val covEnd = coverageEndMs(s)
            if (nowMs < covEnd) {
                val graceMs = (cfg.exitGraceMinutes * MINUTE_MS).coerceAtLeast(1)
                val remain = covEnd - nowMs
                return Status(
                    inFree = false,
                    inPaidWindow = true,
                    freeRemainMs = 0,
                    freeUsedFraction = 1f,
                    paidWindowRemainMs = remain,
                    paidWindowFraction = (remain.toFloat() / graceMs).coerceIn(0f, 1f),
                    paidElapsedMs = 0,
                    paidUnits = 0,
                    currentCostYuan = 0,
                    nextBoundaryMs = covEnd,
                    saveWindowStartMs = covEnd,
                    inSaveWindow = false,
                    nextUnitCostYuan = cfg.rateYuan,
                )
            }
            return billingStatus(s, nowMs, coverageEndMs(s))
        }
        return billingStatus(s, nowMs, freeEnd)
    }

    private fun billingStatus(s: ParkingSession, nowMs: Long, anchor: Long): Status {
        val cfg = s.config
        val paid = (nowMs - anchor).coerceAtLeast(0)
        val unitMs = cfg.billingUnitMinutes * MINUTE_MS
        val k = ceil(paid.toDouble() / unitMs).toInt().coerceAtLeast(1)
        val boundary = anchor + k.toLong() * unitMs
        val windowStart = boundary - payBufferMs(cfg)
        return Status(
            inFree = false,
            inPaidWindow = false,
            freeRemainMs = 0,
            freeUsedFraction = 1f,
            paidWindowRemainMs = 0,
            paidWindowFraction = 0f,
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
        /** 是否为"收费提醒"类提醒 */
        val isSaveRemind: Boolean,
        val passed: Boolean,
    )

    /** 时间轴展示: 最近一条已过的提醒(提供上下文) + 接下来的提醒 */
    fun reminderTimeline(s: ParkingSession, nowMs: Long, maxItems: Int = 4): List<ReminderItem> {
        val items = mutableListOf<ReminderItem>()
        freeRemindMs(s)?.let {
            items += ReminderItem(it, "免费到期提醒", isSaveRemind = false, passed = it <= nowMs)
        }
        val paid = effectivePaidAtMs(s)
        if (paid > 0) {
            val covEnd = coverageEndMs(s)
            items += ReminderItem(covEnd, "出场宽限结束", isSaveRemind = false, passed = covEnd <= nowMs)
        }
        val anchor = if (paid > 0) coverageEndMs(s) else freeEndMs(s)
        var k = 1
        while (k <= maxItems + 2) {
            val t = kthSaveRemindMs(s, k, anchor)
            items += ReminderItem(t, "收费提醒 #$k(缴费驶出省 ¥${s.config.rateYuan})", isSaveRemind = true, passed = t <= nowMs)
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
