package com.rundex.routepoc

import kotlin.math.cos
import kotlin.math.hypot

/** 현재 위치 → 계획 경로 폴리라인 최단거리(m). 브레드크럼 이탈 판정용 */
object RouteDeviation {

    private const val M_PER_DEG = 111194.9

    fun minDistanceMeters(lat: Double, lon: Double, route: List<LatLngPoint>): Double {
        if (route.isEmpty()) return Double.MAX_VALUE
        if (route.size == 1) return Haversine.meters(lat, lon, route[0].lat, route[0].lon)

        // 현재 위치를 원점으로 하는 등장방형 근사 (수십 km 스케일에서 충분)
        val cosLat = cos(Math.toRadians(lat))
        fun x(p: LatLngPoint) = (p.lon - lon) * M_PER_DEG * cosLat
        fun y(p: LatLngPoint) = (p.lat - lat) * M_PER_DEG

        var min = Double.MAX_VALUE
        for (i in 1 until route.size) {
            val ax = x(route[i - 1]); val ay = y(route[i - 1])
            val bx = x(route[i]); val by = y(route[i])
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((-ax) * dx + (-ay) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < min) min = d
        }
        return min
    }
}
