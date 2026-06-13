package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDexTest {

    private val places = listOf(
        ThemePlace("a", "A", 37.5000, 127.0000, 120.0),
        ThemePlace("b", "B", 37.5100, 127.0000, 120.0),
    )

    @Test
    fun `반경 이내를 지난 장소만 수집한다`() {
        val path = listOf(
            LatLngPoint(37.5001, 127.0000), // A에서 ~11m
            LatLngPoint(37.5200, 127.0000), // 둘 다 멀음
        )
        val ids = ThemeMatcher.collectedPlaceIds(path, places)
        assertTrue("a" in ids)
        assertFalse("b" in ids)
    }

    @Test
    fun `시드 JSON을 파싱한다`() {
        val json = """
            [{"slug":"s","title":"테스트","places":[
              {"id":"p1","name":"장소1","lat":37.5,"lon":127.0,"radiusM":100}
            ]}]
        """.trimIndent()
        val cols = ThemeRepo.parse(json)
        assertEquals(1, cols.size)
        assertEquals("s", cols[0].slug)
        assertEquals(1, cols[0].places.size)
        assertEquals(100.0, cols[0].places[0].radiusM, 1e-9)
    }

    @Test
    fun `radiusM 누락 시 기본값 120`() {
        val cols = ThemeRepo.parse(
            """[{"slug":"s","title":"t","places":[{"id":"p","name":"n","lat":37.5,"lon":127.0}]}]"""
        )
        assertEquals(120.0, cols[0].places[0].radiusM, 1e-9)
    }
}
