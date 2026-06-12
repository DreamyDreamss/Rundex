package com.rundex.routepoc

/** 궤적을 동별 거리로 배분 — 각 세그먼트 중점이 속한 동에 세그먼트 거리를 가산 */
object RunProcessor {

    fun distanceByRegion(points: List<TrackPoint>, index: RegionIndex): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = Haversine.meters(a.lat, a.lon, b.lat, b.lon)
            val region = index.findRegion((a.lat + b.lat) / 2, (a.lon + b.lon) / 2) ?: continue
            result.merge(region.code, d, Double::plus)
        }
        return result
    }
}
