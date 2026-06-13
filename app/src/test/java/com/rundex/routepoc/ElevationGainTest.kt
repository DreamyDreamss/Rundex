package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationGainTest {

    @Test
    fun `오르막 합만 누적한다`() {
        // 10 → 15 → 12 → 20 : 상승 +5, +8 = 13 (하강 -3 무시)
        assertEquals(13.0, ElevationGain.totalGain(listOf(10.0, 15.0, 12.0, 20.0)), 1e-9)
    }

    @Test
    fun `노이즈 임계 미만 변화는 무시한다`() {
        // 임계 3m: +2(무시) +4(채택) → 4
        assertEquals(4.0, ElevationGain.totalGain(listOf(10.0, 12.0, 16.0), threshold = 3.0), 1e-9)
    }

    @Test
    fun `점이 부족하면 0`() {
        assertEquals(0.0, ElevationGain.totalGain(listOf(10.0)), 1e-9)
        assertEquals(0.0, ElevationGain.totalGain(emptyList()), 1e-9)
    }

    @Test
    fun `계속 내려가면 0`() {
        assertEquals(0.0, ElevationGain.totalGain(listOf(30.0, 20.0, 10.0)), 1e-9)
    }
}
