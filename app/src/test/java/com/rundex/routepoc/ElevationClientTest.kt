package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationClientTest {

    @Test
    fun `응답 JSON에서 누적 상승고도를 계산한다`() {
        val json = """{"elevation":[10.0,15.0,12.0,20.0]}"""  // +5, -3, +8 → 13
        assertEquals(13.0, ElevationClient.parseGain(json), 1e-9)
    }

    @Test
    fun `샘플링은 maxPoints 이하로 균등 축소한다`() {
        val pts = (0 until 200).map { LatLngPoint(it.toDouble(), 0.0) }
        val s = ElevationClient.sample(pts, 80)
        assertEquals(80, s.size)
        assertEquals(0.0, s.first().lat, 1e-9)
        assertEquals(199.0, s.last().lat, 1e-9)
    }

    @Test
    fun `점이 적으면 그대로 반환`() {
        val pts = listOf(LatLngPoint(1.0, 1.0), LatLngPoint(2.0, 2.0))
        assertEquals(2, ElevationClient.sample(pts, 80).size)
    }
}
