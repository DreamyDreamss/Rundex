package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunStatsTest {

    /** 위도 1도 = 약 111194.9m. 250m 간격·1분 간격(4분/km 페이스) 점 12개 = 2750m */
    private fun constantSpeedPoints(count: Int): List<TrackPoint> {
        val degPer250m = 250.0 / 111194.9
        return (0 until count).map { i ->
            TrackPoint(i * degPer250m, 126.0, i * 60_000L)
        }
    }

    @Test
    fun `평균속도 - 6분에 1km는 10kmh`() {
        assertEquals(10.0, RunStats.avgSpeedKmh(1000.0, 360_000L), 1e-9)
    }

    @Test
    fun `시간이 0이면 평균속도 0`() {
        assertEquals(0.0, RunStats.avgSpeedKmh(1000.0, 0L), 1e-9)
    }

    @Test
    fun `칼로리 - 70kg가 5km 뛰면 약 363kcal`() {
        assertEquals(362.6, RunStats.calorieKcal(5000.0, 70.0), 1.0)
    }

    @Test
    fun `순간 페이스 - 등속 구간에서는 평균 페이스와 같다`() {
        val pts = constantSpeedPoints(12) // 4분/km = 240초/km
        val nowMs = pts.last().timeMs
        val pace = RunStats.currentPaceSecPerKm(pts, nowMs, windowMs = 120_000L)!!
        assertEquals(240.0, pace, 5.0)
    }

    @Test
    fun `순간 페이스 - 움직임이 거의 없으면 null`() {
        val pts = listOf(
            TrackPoint(37.55, 126.98, 0L),
            TrackPoint(37.55, 126.98, 10_000L),
        )
        assertNull(RunStats.currentPaceSecPerKm(pts, 10_000L))
    }

    @Test
    fun `스플릿 - 2750m 등속이면 1km 랩 2개, 각 4분`() {
        val splits = RunStats.splitsMs(constantSpeedPoints(12))
        assertEquals(2, splits.size)
        assertEquals(240_000L, splits[0], 2_000L)
        assertEquals(240_000L, splits[1], 2_000L)
    }

    @Test
    fun `스플릿 - 1km 미만이면 빈 목록`() {
        assertEquals(emptyList<Long>(), RunStats.splitsMs(constantSpeedPoints(3)))
    }

    private fun assertEquals(expected: Long, actual: Long, delta: Long) {
        org.junit.Assert.assertTrue(
            "expected $expected ± $delta but was $actual",
            Math.abs(expected - actual) <= delta
        )
    }
}
