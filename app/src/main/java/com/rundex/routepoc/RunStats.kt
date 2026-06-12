package com.rundex.routepoc

import java.util.Locale

/** 좌표·시간 데이터에서 파생되는 러닝 통계 — 순수 계산만 담당 */
object RunStats {

    fun avgSpeedKmh(distanceMeters: Double, elapsedMs: Long): Double {
        if (elapsedMs <= 0L) return 0.0
        return (distanceMeters / 1000.0) / (elapsedMs / 3_600_000.0)
    }

    /** 러닝 칼로리 근사: 1.036 × 체중(kg) × 거리(km) */
    fun calorieKcal(distanceMeters: Double, weightKg: Double = 70.0): Double =
        1.036 * weightKg * (distanceMeters / 1000.0)

    /**
     * 최근 windowMs 동안의 순간 페이스(초/km).
     * 창 안의 이동 거리가 10m 미만이거나 점이 부족하면 null.
     */
    fun currentPaceSecPerKm(
        points: List<TrackPoint>,
        nowMs: Long,
        windowMs: Long = 15_000L,
    ): Double? {
        val recent = points.filter { it.timeMs >= nowMs - windowMs }
        if (recent.size < 2) return null
        var dist = 0.0
        for (i in 1 until recent.size) {
            dist += Haversine.meters(
                recent[i - 1].lat, recent[i - 1].lon, recent[i].lat, recent[i].lon
            )
        }
        if (dist < 10.0) return null
        val spanMs = recent.last().timeMs - recent.first().timeMs
        if (spanMs <= 0L) return null
        return (spanMs / 1000.0) / (dist / 1000.0)
    }

    /** 완료된 1km마다 걸린 시간(ms). km 경계는 구간 내 선형 보간 */
    fun splitsMs(points: List<TrackPoint>): List<Long> {
        val splits = mutableListOf<Long>()
        var cumDist = 0.0
        var lastBoundaryTimeMs = points.firstOrNull()?.timeMs ?: return splits
        var nextBoundary = 1000.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val seg = Haversine.meters(prev.lat, prev.lon, cur.lat, cur.lon)
            while (seg > 0 && cumDist + seg >= nextBoundary) {
                val frac = (nextBoundary - cumDist) / seg
                val crossTimeMs = prev.timeMs + ((cur.timeMs - prev.timeMs) * frac).toLong()
                splits.add(crossTimeMs - lastBoundaryTimeMs)
                lastBoundaryTimeMs = crossTimeMs
                nextBoundary += 1000.0
            }
            cumDist += seg
        }
        return splits
    }

    fun formatPaceSec(secPerKm: Double): String {
        val m = (secPerKm / 60).toInt()
        val s = (secPerKm % 60).toInt()
        return String.format(Locale.US, "%d'%02d\"", m, s)
    }
}
