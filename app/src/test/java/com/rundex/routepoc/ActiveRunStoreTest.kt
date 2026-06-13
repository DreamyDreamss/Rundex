package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActiveRunStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `저장 후 로드하면 진행 중 기록이 복원된다`() {
        val store = ActiveRunStore(tmp.root)
        val snap = ActiveRun(
            startedAtMs = 1000L,
            distanceMeters = 1234.0,
            elevationGainM = 45.0,
            points = listOf(TrackPoint(37.5, 127.0, 1000L), TrackPoint(37.51, 127.01, 2000L)),
        )
        store.save(snap)
        assertTrue(store.exists())
        assertEquals(snap, store.load())
    }

    @Test
    fun `clear 후에는 복원할 것이 없다`() {
        val store = ActiveRunStore(tmp.root)
        store.save(ActiveRun(1000L, 10.0, 0.0, listOf(TrackPoint(1.0, 1.0, 1000L))))
        store.clear()
        assertFalse(store.exists())
        assertNull(store.load())
    }

    @Test
    fun `파일이 없으면 null`() {
        assertNull(ActiveRunStore(tmp.root).load())
    }
}
