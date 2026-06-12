package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineTest {

    @Test
    fun `같은 점은 거리 0`() {
        assertEquals(0.0, Haversine.meters(37.55, 126.98, 37.55, 126.98), 1e-6)
    }

    @Test
    fun `적도에서 경도 1도는 약 111195m`() {
        assertEquals(111194.9, Haversine.meters(0.0, 0.0, 0.0, 1.0), 100.0)
    }

    @Test
    fun `위도 1도는 약 111195m`() {
        assertEquals(111194.9, Haversine.meters(0.0, 126.0, 1.0, 126.0), 100.0)
    }
}
