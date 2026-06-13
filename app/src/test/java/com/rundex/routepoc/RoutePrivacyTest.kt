package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePrivacyTest {

    @Test
    fun `시작과 끝 반경 구간을 잘라낸다`() {
        // 위도 0.001도 ≈ 111m 간격으로 일렬 배치
        val path = (0..9).map { LatLngPoint(37.5000 + it * 0.001, 127.0) }
        val trimmed = RoutePrivacy.trimEnds(path, radiusM = 150.0)
        // 앞뒤로 ~150m(약 1.3점)씩 제거되어 가운데만 남음
        assertTrue(trimmed.size < path.size)
        assertTrue(trimmed.first().lat > path.first().lat)
        assertTrue(trimmed.last().lat < path.last().lat)
    }

    @Test
    fun `점이 너무 적으면 원본 유지`() {
        val path = listOf(LatLngPoint(37.5, 127.0), LatLngPoint(37.5001, 127.0))
        assertEquals(path, RoutePrivacy.trimEnds(path))
    }

    @Test
    fun `전부 잘릴 만큼 짧으면 원본 유지`() {
        val path = (0..4).map { LatLngPoint(37.5 + it * 0.0001, 127.0) } // 총 ~44m
        assertEquals(path.size, RoutePrivacy.trimEnds(path, radiusM = 150.0).size)
    }
}
