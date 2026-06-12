package com.rundex.routepoc

/** Google polyline5 디코더 — OSRM 기본 geometry 형식 */
object PolylineDecoder {

    fun decode(encoded: String): List<LatLngPoint> {
        val result = mutableListOf<LatLngPoint>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var shift = 0
            var acc = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (acc and 1 != 0) (acc shr 1).inv() else acc shr 1

            shift = 0
            acc = 0
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (acc and 1 != 0) (acc shr 1).inv() else acc shr 1

            result.add(LatLngPoint(lat / 1e5, lon / 1e5))
        }
        return result
    }
}
