package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutingClientTest {

    @Test
    fun `정상 응답에서 거리와 좌표를 추출한다`() {
        // polyline "_p~iF~ps|U_ulLnnqC" = (38.5,-120.2) → (40.7,-120.95)
        val json = """
            {"code":"Ok","routes":[{"distance":1234.5,"geometry":"_p~iF~ps|U_ulLnnqC"}]}
        """.trimIndent()
        val leg = RoutingClient.parseRouteResponse(json)
        assertEquals(1234.5, leg.distanceMeters, 1e-9)
        assertEquals(2, leg.points.size)
        assertEquals(38.5, leg.points[0].lat, 1e-5)
    }

    @Test
    fun `code가 Ok가 아니면 예외를 던진다`() {
        val json = """{"code":"NoRoute","routes":[]}"""
        assertThrows(IllegalArgumentException::class.java) {
            RoutingClient.parseRouteResponse(json)
        }
    }
}
