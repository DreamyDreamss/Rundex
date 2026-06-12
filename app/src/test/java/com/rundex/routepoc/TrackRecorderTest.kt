package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackRecorderTest {

    @Before
    fun resetRecorder() {
        TrackRecorder.reset()
    }

    @Test
    fun `시작 후 점을 추가하면 거리가 누적된다`() {
        TrackRecorder.start(1000L)
        TrackRecorder.addPoint(0.0, 0.0, 10f, 1000L)
        TrackRecorder.addPoint(0.0, 1.0, 10f, 2000L) // 적도 경도 1도 ≈ 111195m
        assertEquals(111194.9, TrackRecorder.distanceMeters, 100.0)
        assertEquals(2, TrackRecorder.points.size)
    }

    @Test
    fun `정확도 30m 초과인 점은 버린다`() {
        TrackRecorder.start(1000L)
        TrackRecorder.addPoint(0.0, 0.0, 10f, 1000L)
        assertFalse(TrackRecorder.addPoint(0.0, 1.0, 31f, 2000L))
        assertEquals(1, TrackRecorder.points.size)
        assertEquals(0.0, TrackRecorder.distanceMeters, 1e-9)
    }

    @Test
    fun `기록 중이 아니면 점을 받지 않는다`() {
        assertFalse(TrackRecorder.addPoint(0.0, 0.0, 10f, 1000L))
        assertEquals(0, TrackRecorder.points.size)
    }

    @Test
    fun `stop은 거리·시간이 채워진 SavedTrack을 반환한다`() {
        TrackRecorder.start(1000L)
        TrackRecorder.addPoint(0.0, 0.0, 10f, 1500L)
        TrackRecorder.addPoint(0.0, 1.0, 10f, 61000L)
        val track = TrackRecorder.stop(61000L)
        assertEquals(60000L, track.durationMs)
        assertEquals(111194.9, track.distanceMeters, 100.0)
        assertEquals(2, track.points.size)
        assertFalse(TrackRecorder.recording)
    }

    @Test
    fun `리스너는 점이 추가될 때 호출된다`() {
        var called = 0
        val listener = object : TrackRecorder.Listener {
            override fun onUpdate() { called++ }
        }
        TrackRecorder.addListener(listener)
        TrackRecorder.start(1000L)
        TrackRecorder.addPoint(0.0, 0.0, 10f, 1000L)
        TrackRecorder.removeListener(listener)
        assertTrue(called >= 1)
    }
}
