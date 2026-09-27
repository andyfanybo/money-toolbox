package com.fan.moneytoolbox

import com.fan.moneytoolbox.data.RenewalCategory
import com.fan.moneytoolbox.data.RenewalCycle
import com.fan.moneytoolbox.data.RenewalItem
import com.fan.moneytoolbox.data.RenewalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RenewalMathTest {
    private fun item(date: LocalDate, cycle: RenewalCycle, index: Int = 0) = RenewalItem(
        id = "test", name = "停车月租", category = RenewalCategory.PARKING,
        amountCents = 30000, firstDueEpochDay = date.toEpochDay(), cycle = cycle,
        periodIndex = index, remindDaysBefore = 3,
    )

    @Test fun `月末续费保留原始锚点`() {
        val base = item(LocalDate.of(2025, 1, 31), RenewalCycle.MONTHLY)
        assertEquals(LocalDate.of(2025, 2, 28), RenewalMath.dueDate(base.copy(periodIndex = 1)))
        assertEquals(LocalDate.of(2025, 3, 31), RenewalMath.dueDate(base.copy(periodIndex = 2)))
    }

    @Test fun `闰日年费在闰年恢复2月29日`() {
        val base = item(LocalDate.of(2024, 2, 29), RenewalCycle.YEARLY)
        assertEquals(LocalDate.of(2025, 2, 28), RenewalMath.dueDate(base.copy(periodIndex = 1)))
        assertEquals(LocalDate.of(2028, 2, 29), RenewalMath.dueDate(base.copy(periodIndex = 4)))
    }

    @Test fun `提前确认续费后进入下一周期`() {
        val base = item(LocalDate.of(2026, 10, 1), RenewalCycle.MONTHLY)
        val renewed = RenewalMath.advanceAfterPaid(base, LocalDate.of(2026, 9, 26))
        assertEquals(LocalDate.of(2026, 11, 1), RenewalMath.dueDate(renewed))
    }

    @Test fun `逾期多个周期后确认续费跳到未来`() {
        val base = item(LocalDate.of(2026, 1, 31), RenewalCycle.MONTHLY)
        val renewed = RenewalMath.advanceAfterPaid(base, LocalDate.of(2026, 5, 2))
        assertEquals(LocalDate.of(2026, 5, 31), RenewalMath.dueDate(renewed))
    }

    @Test fun `单次费用确认后完成`() {
        val renewed = RenewalMath.advanceAfterPaid(item(LocalDate.of(2026, 10, 1), RenewalCycle.ONCE), LocalDate.of(2026, 9, 26))
        assertTrue(renewed.completed)
    }

    @Test fun `提前提醒按自然日计算`() {
        val base = item(LocalDate.of(2026, 10, 1), RenewalCycle.QUARTERLY)
        assertEquals(LocalDate.of(2026, 9, 28), RenewalMath.reminderDate(base))
        assertFalse(base.completed)
    }
}
