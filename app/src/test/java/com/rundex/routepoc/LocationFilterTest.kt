package com.rundex.routepoc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFilterTest {

    // 위도 기준 10m ≈ 0.0000899도
    private val degPer10m = 10.0 / 111194.9

    @Test
    fun `첫 픽스는 정확도 20m 이하만 채택`() {
        val f = LocationFilter()
        assertFalse(f.offer(37.55, 126.98, 21f, 0L))
        assertTrue(f.offer(37.55, 126.98, 19f, 1000L))
    }

    @Test
    fun `정확도 25m 초과는 폐기`() {
        val f = LocationFilter()
        f.offer(37.55, 126.98, 10f, 0L)
        assertFalse(f.offer(37.55 + degPer10m, 126.98, 26f, 1000L))
    }

    @Test
    fun `러닝 속도 이동은 채택`() {
        val f = LocationFilter()
        f.offer(0.0, 126.98, 10f, 0L)
        // 1초에 5m (5 m/s)
        assertTrue(f.offer(degPer10m / 2, 126.98, 10f, 1_000L))
    }

    @Test
    fun `순간이동(45kmh 초과)은 폐기`() {
        val f = LocationFilter()
        f.offer(0.0, 126.98, 10f, 0L)
        // 1초에 500m
        assertFalse(f.offer(degPer10m * 50, 126.98, 10f, 1_000L))
    }

    @Test
    fun `정지 지터(4m 미만)는 폐기되어 거리가 쌓이지 않는다`() {
        val f = LocationFilter()
        f.offer(0.0, 126.98, 10f, 0L)
        assertFalse(f.offer(degPer10m * 0.3, 126.98, 10f, 1_000L)) // 3m
        assertFalse(f.offer(0.0, 126.98, 10f, 2_000L))
    }

    @Test
    fun `점프 폐기 후 정상 픽스는 자연 복구된다`() {
        val f = LocationFilter()
        f.offer(0.0, 126.98, 10f, 0L)
        assertFalse(f.offer(degPer10m * 50, 126.98, 10f, 1_000L)) // 점프 폐기
        // 2초 뒤 10m 지점(5m/s 상당) → 채택
        assertTrue(f.offer(degPer10m, 126.98, 10f, 2_000L))
    }

    @Test
    fun `시간 역행 픽스는 폐기`() {
        val f = LocationFilter()
        f.offer(0.0, 126.98, 10f, 5_000L)
        assertFalse(f.offer(degPer10m, 126.98, 10f, 4_000L))
    }

    @Test
    fun `폐기 횟수가 집계된다`() {
        val f = LocationFilter()
        f.offer(0.0, 126.98, 10f, 0L)
        f.offer(degPer10m * 50, 126.98, 10f, 1_000L) // 점프
        f.offer(0.0, 126.98, 30f, 2_000L)             // 저정확도
        org.junit.Assert.assertEquals(2, f.rejectedCount)
    }
}
