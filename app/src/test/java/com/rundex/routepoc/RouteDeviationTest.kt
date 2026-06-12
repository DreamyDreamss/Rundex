package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDeviationTest {

    // 적도 부근: 위도 1도 ≈ 111194.9m
    private val degPer100m = 100.0 / 111194.9

    // 동서로 1km 뻗은 경로 (lat 0.0, lon 126.0 → 126.009)
    private val route = listOf(
        LatLngPoint(0.0, 126.000),
        LatLngPoint(0.0, 126.009),
    )

    @Test
    fun `경로 위의 점은 거리 0에 가깝다`() {
        assertEquals(0.0, RouteDeviation.minDistanceMeters(0.0, 126.004, route), 1.0)
    }

    @Test
    fun `경로 중간에서 수직 100m 떨어진 점`() {
        assertEquals(100.0, RouteDeviation.minDistanceMeters(degPer100m, 126.004, route), 2.0)
    }

    @Test
    fun `경로 끝점 너머의 점은 끝점까지 거리`() {
        // 끝점(126.009)에서 동쪽으로 100m 더
        val lonPer100m = degPer100m // 적도에서 경도도 동일
        assertEquals(100.0, RouteDeviation.minDistanceMeters(0.0, 126.009 + lonPer100m, route), 2.0)
    }

    @Test
    fun `빈 경로는 무한대`() {
        assertEquals(Double.MAX_VALUE, RouteDeviation.minDistanceMeters(0.0, 126.0, emptyList()), 0.0)
    }
}
