package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxExporterTest {

    @Test
    fun `GPX 1_1 헤더와 트랙포인트를 생성한다`() {
        val track = SavedTrack(
            id = "1718000000000",
            startedAtMs = 1718000000000L,
            durationMs = 600_000L,
            distanceMeters = 2400.0,
            points = listOf(
                TrackPoint(37.51, 126.99, 1718000000000L),
                TrackPoint(37.52, 127.00, 1718000600000L),
            ),
        )
        val gpx = GpxExporter.toGpx(track)
        assertTrue(gpx.contains("""<gpx version="1.1""""))
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("""lat="37.51""""))
        assertTrue(gpx.contains("""lon="126.99""""))
        assertTrue(gpx.contains("<time>"))
    }
}
