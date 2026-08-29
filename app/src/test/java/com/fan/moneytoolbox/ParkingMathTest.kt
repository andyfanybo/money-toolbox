package com.fan.moneytoolbox

import com.fan.moneytoolbox.data.ParkingConfig
import com.fan.moneytoolbox.data.ParkingMath
import com.fan.moneytoolbox.data.ParkingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingMathTest {

    // 固定基准时间(纯毫秒运算,与时区无关)
    private val entry = 1735689600000L
    private val min = ParkingMath.MINUTE_MS

    private fun session(
        free: Int = 0,
        rate: Int = 6,
        unit: Int = 60,
        grace: Int = 15,
        remindBefore: Int = 10,
        buffer: Int = 2,
        paidAt: Long = 0L,
    ) = ParkingSession(entry, ParkingConfig(free, rate, unit, grace, remindBefore, buffer), paidAt)

    // ---------- 收费提醒时刻: 锚点 + k*单元 − 缴费缓冲 ----------

    @Test
    fun `无免费时长时 第1次收费提醒在周期截止前2分钟`() {
        val s = session(free = 0, unit = 60, buffer = 2)
        assertEquals(entry + 58 * min, ParkingMath.kthSaveRemindMs(s, 1))
    }

    @Test
    fun `免费60分钟时 收费提醒锚定在免费截止之后`() {
        val s = session(free = 60, unit = 60, buffer = 2)
        assertEquals(entry + 118 * min, ParkingMath.kthSaveRemindMs(s, 1))
        assertEquals(entry + 178 * min, ParkingMath.kthSaveRemindMs(s, 2))
    }

    @Test
    fun `每30分钟单元时 首次收费提醒在28分钟处`() {
        val s = session(free = 0, unit = 30, buffer = 2)
        assertEquals(entry + 28 * min, ParkingMath.kthSaveRemindMs(s, 1))
    }

    // ---------- 免费到期提醒 ----------

    @Test
    fun `免费到期提醒 = 免费 - 提前量`() {
        val s = session(free = 30, remindBefore = 10)
        assertEquals(entry + 20 * min, ParkingMath.freeRemindMs(s))
    }

    @Test
    fun `免费时长小于提前量时 不做免费提醒`() {
        val s = session(free = 5, remindBefore = 10)
        assertNull(ParkingMath.freeRemindMs(s))
    }

    // ---------- 下一个提醒的调度顺序 ----------

    @Test
    fun `免费期内的下一个提醒是免费到期提醒`() {
        val s = session(free = 30, remindBefore = 10)
        assertEquals(entry + 20 * min, ParkingMath.nextReminderMs(s, entry + 5 * min))
    }

    @Test
    fun `免费刚结束后 下一个是首次收费提醒`() {
        val s = session(free = 30, unit = 60, buffer = 2)
        // 免费截止 entry+30min,首次收费提醒 = 30 + 60 - 2 = entry+88min
        assertEquals(entry + 88 * min, ParkingMath.nextReminderMs(s, entry + 31 * min))
    }

    @Test
    fun `收费提醒被忽略后 按周期继续提醒`() {
        val s = session(free = 0, unit = 60, buffer = 2)
        assertEquals(entry + 118 * min, ParkingMath.nextReminderMs(s, entry + 59 * min))
    }

    @Test
    fun `缴费后 下一个提醒是出场宽限结束`() {
        val s = session(free = 0, unit = 60, grace = 15, paidAt = entry + 58 * min)
        assertEquals(entry + 73 * min, ParkingMath.nextReminderMs(s, entry + 59 * min))
    }

    @Test
    fun `宽限结束后 收费提醒以宽限结束为新周期锚点`() {
        val s = session(free = 0, unit = 60, grace = 15, buffer = 2, paidAt = entry + 58 * min)
        // 宽限结束 entry+73min,之后的首次收费提醒 = 73 + 60 - 2 = entry+131min
        assertEquals(entry + 131 * min, ParkingMath.nextReminderMs(s, entry + 74 * min))
    }

    // ---------- 状态计算 ----------

    @Test
    fun `免费期状态为免费`() {
        val s = session(free = 30)
        val st = ParkingMath.status(s, entry + 10 * min)
        assertTrue(st.inFree)
        assertFalse(st.inPaidWindow)
        assertEquals(20 * min, st.freeRemainMs)
        assertEquals(0, st.currentCostYuan)
    }

    @Test
    fun `44分钟不在省钱窗口`() {
        val s = session(free = 0, rate = 6, unit = 60, buffer = 2)
        val st = ParkingMath.status(s, entry + 44 * min)
        assertFalse(st.inFree)
        assertFalse(st.inSaveWindow)
        assertEquals(1, st.paidUnits)
        assertEquals(6, st.currentCostYuan)
        assertEquals(12, st.nextUnitCostYuan)
        assertEquals(entry + 58 * min, st.saveWindowStartMs)
    }

    @Test
    fun `59分钟进入省钱窗口`() {
        val s = session(free = 0, rate = 6, unit = 60, buffer = 2)
        val st = ParkingMath.status(s, entry + 59 * min)
        assertTrue(st.inSaveWindow)
        assertEquals(6, st.currentCostYuan)
        assertEquals(entry + 60 * min, st.nextBoundaryMs)
    }

    @Test
    fun `超过60分钟费用跳到两个单元`() {
        val s = session(free = 0, rate = 6, unit = 60)
        val st = ParkingMath.status(s, entry + 61 * min)
        assertEquals(2, st.paidUnits)
        assertEquals(12, st.currentCostYuan)
    }

    @Test
    fun `缴费后处于出场宽限状态`() {
        val s = session(free = 0, grace = 15, paidAt = entry + 58 * min)
        val st = ParkingMath.status(s, entry + 65 * min)
        assertTrue(st.inPaidWindow)
        assertEquals(8 * min, st.paidWindowRemainMs)
        assertEquals(entry + 73 * min, st.nextBoundaryMs)
    }

    @Test
    fun `免费时段内的缴费标记不生效`() {
        val s = session(free = 30, paidAt = entry + 10 * min)
        val st = ParkingMath.status(s, entry + 20 * min)
        assertTrue(st.inFree)
        assertFalse(st.inPaidWindow)
        // 下一个提醒仍是免费到期提醒
        assertEquals(entry + 20 * min, ParkingMath.nextReminderMs(s, entry + 5 * min))
    }

    // ---------- 边界收敛 ----------

    @Test
    fun `缓冲大于计费单元时收敛避免提前量为负`() {
        val s = session(free = 0, unit = 30, buffer = 45)
        // 缓冲收敛为 29 分钟,首次提醒在 30-29=1 分钟处
        assertEquals(entry + 1 * min, ParkingMath.kthSaveRemindMs(s, 1))
    }
}
