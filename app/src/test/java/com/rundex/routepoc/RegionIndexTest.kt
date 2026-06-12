package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionIndexTest {

    // 동A: lon 127.00~127.01, lat 37.50~37.51 / 동B: lon 127.01~127.02, lat 37.50~37.51
    companion object {
        val MINI_GEOJSON = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"code":"A","name":"가동"},
           "geometry":{"type":"Polygon","coordinates":[[[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]]]}},
          {"type":"Feature","properties":{"code":"B","name":"나동"},
           "geometry":{"type":"MultiPolygon","coordinates":[[[[127.01,37.50],[127.02,37.50],[127.02,37.51],[127.01,37.51],[127.01,37.50]]]]}}
        ]}
        """.trimIndent()
    }

    @Test
    fun `폴리곤 내부 좌표는 해당 동을 반환한다`() {
        val idx = RegionIndex.fromGeoJson(MINI_GEOJSON)
        assertEquals("A", idx.findRegion(37.505, 127.005)?.code)
        assertEquals("나동", idx.findRegion(37.505, 127.015)?.name)
    }

    @Test
    fun `경계 밖 좌표는 null`() {
        val idx = RegionIndex.fromGeoJson(MINI_GEOJSON)
        assertNull(idx.findRegion(37.52, 127.005))
    }

    @Test
    fun `지역 총 수를 안다`() {
        assertEquals(2, RegionIndex.fromGeoJson(MINI_GEOJSON).totalCount)
    }
}
