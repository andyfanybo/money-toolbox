package com.fan.moneytoolbox.data

import java.time.LocalDate

enum class RenewalCategory(val label: String) {
    PARKING("停车费"),
    MEMBERSHIP("影音会员"),
    OTHER("其他续费"),
}

enum class RenewalCycle(val label: String) {
    ONCE("仅一次"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    QUARTERLY("每季"),
    YEARLY("每年"),
}

data class RenewalItem(
    val id: String,
    val name: String,
    val category: RenewalCategory,
    val amountCents: Long?,
    /** 第一次到期日。后续周期总从这里计算，避免 1 月 31 日逐月滚动后永久变成 28 日。 */
    val firstDueEpochDay: Long,
    val cycle: RenewalCycle,
    val periodIndex: Int = 0,
    val remindDaysBefore: Int = 3,
    val completed: Boolean = false,
)

object RenewalMath {
    fun dueDate(item: RenewalItem): LocalDate {
        val first = LocalDate.ofEpochDay(item.firstDueEpochDay)
        val period = item.periodIndex.toLong()
        return when (item.cycle) {
            RenewalCycle.ONCE -> first
            RenewalCycle.WEEKLY -> first.plusWeeks(period)
            RenewalCycle.MONTHLY -> first.plusMonths(period)
            RenewalCycle.QUARTERLY -> first.plusMonths(period * 3)
            RenewalCycle.YEARLY -> first.plusYears(period)
        }
    }

    fun advanceAfterPaid(item: RenewalItem, today: LocalDate): RenewalItem {
        if (item.cycle == RenewalCycle.ONCE) return item.copy(completed = true)
        var next = item.copy(periodIndex = item.periodIndex + 1)
        while (!dueDate(next).isAfter(today)) {
            next = next.copy(periodIndex = next.periodIndex + 1)
        }
        return next
    }

    fun reminderDate(item: RenewalItem): LocalDate =
        dueDate(item).minusDays(item.remindDaysBefore.toLong().coerceAtLeast(0))
}
