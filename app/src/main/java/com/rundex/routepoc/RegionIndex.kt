package com.rundex.routepoc

import org.json.JSONArray
import org.json.JSONObject

class Region(
    val code: String,
    val name: String,
    /** 폴리곤 링 목록 — 각 링은 [lon, lat] 배열들 */
    val rings: List<List<DoubleArray>>,
    val bbox: DoubleArray, // [minLon, minLat, maxLon, maxLat]
)

/** 행정동 경계 인덱스 — bbox 프리필터 + ray casting (even-odd, 구멍 포함) */
class RegionIndex(val regions: List<Region>) {

    val totalCount: Int get() = regions.size

    fun findRegion(lat: Double, lon: Double): Region? =
        regions.firstOrNull { r ->
            lon >= r.bbox[0] && lon <= r.bbox[2] &&
                lat >= r.bbox[1] && lat <= r.bbox[3] &&
                containsPoint(r, lat, lon)
        }

    private fun containsPoint(r: Region, lat: Double, lon: Double): Boolean {
        var inside = false
        for (ring in r.rings) {
            if (pointInRing(lat, lon, ring)) inside = !inside
        }
        return inside
    }

    private fun pointInRing(lat: Double, lon: Double, ring: List<DoubleArray>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i][0]; val yi = ring[i][1]
            val xj = ring[j][0]; val yj = ring[j][1]
            if ((yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
            ) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        fun fromGeoJson(json: String): RegionIndex {
            val features = JSONObject(json).getJSONArray("features")
            val regions = (0 until features.length()).map { i ->
                val f = features.getJSONObject(i)
                val props = f.getJSONObject("properties")
                val geom = f.getJSONObject("geometry")
                val rings = mutableListOf<List<DoubleArray>>()
                when (geom.getString("type")) {
                    "Polygon" -> addPolygon(geom.getJSONArray("coordinates"), rings)
                    "MultiPolygon" -> {
                        val polys = geom.getJSONArray("coordinates")
                        for (p in 0 until polys.length()) addPolygon(polys.getJSONArray(p), rings)
                    }
                }
                var minLon = Double.MAX_VALUE; var minLat = Double.MAX_VALUE
                var maxLon = -Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                rings.forEach { ring ->
                    ring.forEach { c ->
                        if (c[0] < minLon) minLon = c[0]
                        if (c[0] > maxLon) maxLon = c[0]
                        if (c[1] < minLat) minLat = c[1]
                        if (c[1] > maxLat) maxLat = c[1]
                    }
                }
                Region(
                    code = props.getString("code"),
                    name = props.getString("name"),
                    rings = rings,
                    bbox = doubleArrayOf(minLon, minLat, maxLon, maxLat),
                )
            }
            return RegionIndex(regions)
        }

        private fun addPolygon(coords: JSONArray, out: MutableList<List<DoubleArray>>) {
            for (r in 0 until coords.length()) {
                val ringArr = coords.getJSONArray(r)
                out.add((0 until ringArr.length()).map { c ->
                    val pt = ringArr.getJSONArray(c)
                    doubleArrayOf(pt.getDouble(0), pt.getDouble(1))
                })
            }
        }
    }
}
