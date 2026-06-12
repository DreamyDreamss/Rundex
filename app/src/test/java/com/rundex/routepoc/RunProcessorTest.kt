package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunProcessorTest {

    private val idx = RegionIndex.fromGeoJson(RegionIndexTest.MINI_GEOJSON)

    @Test
    fun `동을 가로지르는 궤적은 양쪽에 거리가 배분된다`() {
        // 동A(127.00~127.01) 안 2점 → 동B(127.01~127.02)로 넘어가는 1점
        val pts = listOf(
            TrackPoint(37.505, 127.003, 0L),
            TrackPoint(37.505, 127.008, 60_000L),   // 중점 127.0055 → A
            TrackPoint(37.505, 127.014, 120_000L),  // 중점 127.011  → B
        )
        val map = RunProcessor.distanceByRegion(pts, idx)
        assertTrue("A 거리 누적", map.getValue("A") > 0.0)
        assertTrue("B 거리 누적", map.getValue("B") > 0.0)
        val total = Haversine.meters(37.505, 127.003, 37.505, 127.008) +
            Haversine.meters(37.505, 127.008, 37.505, 127.014)
        assertEquals(total, map.values.sum(), 0.01)
    }

    @Test
    fun `경계 밖 세그먼트는 배분되지 않는다`() {
        val pts = listOf(
            TrackPoint(37.60, 127.005, 0L), // 두 동 모두 밖
            TrackPoint(37.60, 127.010, 60_000L),
        )
        assertEquals(emptyMap<String, Double>(), RunProcessor.distanceByRegion(pts, idx))
    }
}
