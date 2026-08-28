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

    // 固定基准时间: 2025-01-01 08:00:00 本地任意时区(纯毫秒运算,与时区无关)
    private val entry = 1735689600000L
    private val min = ParkingMath.MINUTE_MS

    private fun session(
        free: Int = 0,
        rate: Int = 6,
        unit: Int = 60,
        grace: Int = 15,
        remindBefore: Int = 10,
    ) = ParkingSession(entry, ParkingConfig(free, rate, unit, grace, remindBefore))

    @Test
    fun `无免费时长时 第1次省钱提醒在45分钟`() {
        val s = session(free = 0, unit = 60, grace = 15)
        assertEquals(entry + 45 * min, ParkingMath.kthSaveRemindMs(s, 1))
    }

    @Test
    fun `免费60分钟时 第1次省钱提醒在105分钟`() {
        val s = session(free = 60, unit = 60, grace = 15)
        assertEquals(entry + 105 * min, ParkingMath.kthSaveRemindMs(s, 1))
        // 第2次在 +165 分钟
        assertEquals(entry + 165 * min, ParkingMath.kthSaveRemindMs(s, 2))
    }

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

    @Test
    fun `免费期内的下一个提醒是免费到期提醒`() {
        val s = session(free = 30, remindBefore = 10)
        val next = ParkingMath.nextReminderMs(s, entry + 5 * min)!!
        assertEquals(entry + 20 * min, next)
    }

    @Test
    fun `免费刚到期后 下一个提醒是第1次省钱提醒`() {
        val s = session(free = 30, unit = 60, grace = 15)
        val next = ParkingMath.nextReminderMs(s, entry + 31 * min)!!
        assertEquals(entry + 90 * min - 15 * min, next)
    }

    @Test
    fun `省钱提醒被忽略后 会继续安排下一个周期`() {
        val s = session(free = 0, unit = 60, grace = 15)
        val next = ParkingMath.nextReminderMs(s, entry + 46 * min)!!
        assertEquals(entry + 105 * min, next)
    }

    @Test
    fun `免费期状态为免费`() {
        val s = session(free = 30)
        val st = ParkingMath.status(s, entry + 10 * min)
        assertTrue(st.inFree)
        assertEquals(20 * min, st.freeRemainMs)
        assertEquals(0, st.currentCostYuan)
    }

    @Test
    fun `计费44分钟时费用为一个单元`() {
        val s = session(free = 0, rate = 6, unit = 60)
        val st = ParkingMath.status(s, entry + 44 * min)
        assertFalse(st.inFree)
        assertFalse(st.inSaveWindow)
        assertEquals(1, st.paidUnits)
        assertEquals(6, st.currentCostYuan)
        assertEquals(12, st.nextUnitCostYuan)
    }

    @Test
    fun `45分钟进入省钱窗口`() {
        val s = session(free = 0, rate = 6, unit = 60, grace = 15)
        val st = ParkingMath.status(s, entry + 46 * min)
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
    fun `宽限大于计费单元时收敛避免提前量为零`() {
        val s = session(free = 0, unit = 30, grace = 45)
        // 提前量收敛为 29 分钟,第1次提醒在 30-29=1 分钟处
        assertEquals(entry + 1 * min, ParkingMath.kthSaveRemindMs(s, 1))
    }
}
