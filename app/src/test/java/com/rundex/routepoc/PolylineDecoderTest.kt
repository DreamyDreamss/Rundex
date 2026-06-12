package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `표준 테스트 벡터를 디코딩한다`() {
        val decoded = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].lat, 1e-5)
        assertEquals(-120.2, decoded[0].lon, 1e-5)
        assertEquals(40.7, decoded[1].lat, 1e-5)
        assertEquals(-120.95, decoded[1].lon, 1e-5)
        assertEquals(43.252, decoded[2].lat, 1e-5)
        assertEquals(-126.453, decoded[2].lon, 1e-5)
    }

    @Test
    fun `빈 문자열은 빈 리스트를 반환한다`() {
        assertEquals(emptyList<LatLngPoint>(), PolylineDecoder.decode(""))
    }
}
