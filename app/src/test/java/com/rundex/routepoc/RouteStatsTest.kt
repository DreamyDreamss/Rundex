package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStatsTest {

    @Test
    fun `예상 시간은 6분퍼km 기준으로 계산된다`() {
        assertEquals(0, RouteStats.estimatedMinutes(0.0))
        assertEquals(6, RouteStats.estimatedMinutes(1000.0))
        assertEquals(30, RouteStats.estimatedMinutes(5000.0))
    }

    @Test
    fun `직선 구간은 두 점과 하버사인 거리를 갖는다`() {
        val leg = RouteStats.straightLeg(LatLngPoint(37.5, 127.0), LatLngPoint(37.51, 127.0))
        assertEquals(2, leg.points.size)
        // 위도 0.01도 ≈ 1.11km
        assertEquals(1112.0, leg.distanceMeters, 5.0)
    }

    @Test
    fun `지나는 동 개수는 중복 없이 센다`() {
        // 0~1 사이 정사각형 A, 1~2 사이 정사각형 B
        val a = squareRegion("A", 0.0, 0.0, 1.0, 1.0)
        val b = squareRegion("B", 1.0, 0.0, 2.0, 1.0)
        val index = RegionIndex(listOf(a, b))
        val path = listOf(
            LatLngPoint(0.5, 0.5),  // A
            LatLngPoint(0.7, 0.3),  // A
            LatLngPoint(0.5, 1.5),  // B
        )
        assertEquals(2, RouteStats.distinctRegionCount(path, index))
    }

    @Test
    fun `빈 경로의 동 개수는 0`() {
        val index = RegionIndex(listOf(squareRegion("A", 0.0, 0.0, 1.0, 1.0)))
        assertEquals(0, RouteStats.distinctRegionCount(emptyList(), index))
    }

    /** [minLon,minLat]~[maxLon,maxLat] 사각형 폴리곤 한 개짜리 지역 */
    private fun squareRegion(
        code: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double,
    ): Region {
        val ring = listOf(
            doubleArrayOf(minLon, minLat),
            doubleArrayOf(maxLon, minLat),
            doubleArrayOf(maxLon, maxLat),
            doubleArrayOf(minLon, maxLat),
            doubleArrayOf(minLon, minLat),
        )
        return Region(code, code, listOf(ring), doubleArrayOf(minLon, minLat, maxLon, maxLat))
    }
}
