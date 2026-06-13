package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodingClientTest {

    @Test
    fun `검색 응답에서 장소 목록을 추출한다`() {
        val json = """
            [
              {"name":"서울역","display_name":"서울역, 용산구, 서울특별시","lat":"37.5547","lon":"126.9707"},
              {"name":"서울시청","display_name":"서울시청, 중구, 서울특별시","lat":"37.5663","lon":"126.9779"}
            ]
        """.trimIndent()
        val places = GeocodingClient.parseSearchResponse(json)
        assertEquals(2, places.size)
        assertEquals("서울역", places[0].name)
        assertEquals(37.5547, places[0].lat, 1e-5)
        assertEquals(126.9707, places[0].lon, 1e-5)
    }

    @Test
    fun `name이 비면 display_name의 첫 토막을 대표 이름으로 쓴다`() {
        val json = """
            [{"name":"","display_name":"강남대로 123, 강남구, 서울특별시","lat":"37.50","lon":"127.02"}]
        """.trimIndent()
        val places = GeocodingClient.parseSearchResponse(json)
        assertEquals(1, places.size)
        assertEquals("강남대로 123", places[0].name)
    }

    @Test
    fun `display_name이 없는 항목은 건너뛴다`() {
        val json = """
            [{"name":"이름만","lat":"37.5","lon":"127.0"}]
        """.trimIndent()
        val places = GeocodingClient.parseSearchResponse(json)
        assertTrue(places.isEmpty())
    }

    @Test
    fun `빈 배열이면 빈 목록을 반환한다`() {
        assertTrue(GeocodingClient.parseSearchResponse("[]").isEmpty())
    }
}
