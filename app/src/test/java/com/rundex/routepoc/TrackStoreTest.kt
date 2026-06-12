package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrackStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sampleTrack(id: String, startedAt: Long) = SavedTrack(
        id = id,
        startedAtMs = startedAt,
        durationMs = 600_000L,
        distanceMeters = 2400.0,
        points = listOf(
            TrackPoint(37.51, 126.99, startedAt),
            TrackPoint(37.52, 127.00, startedAt + 600_000L),
        ),
    )

    @Test
    fun `저장 후 로드하면 동일한 기록이 나온다`() {
        val store = TrackStore(tmp.root)
        val track = sampleTrack("100", 100L)
        store.save(track)
        assertEquals(track, store.load("100"))
    }

    @Test
    fun `목록은 최신 시작 순으로 정렬된다`() {
        val store = TrackStore(tmp.root)
        store.save(sampleTrack("100", 100L))
        store.save(sampleTrack("300", 300L))
        store.save(sampleTrack("200", 200L))
        assertEquals(listOf("300", "200", "100"), store.list().map { it.id })
    }

    @Test
    fun `빈 디렉토리면 빈 목록`() {
        assertEquals(emptyList<TrackSummary>(), TrackStore(tmp.root).list())
    }
}
