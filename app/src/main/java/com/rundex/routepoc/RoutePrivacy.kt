package com.rundex.routepoc

/** 경로 공개/등록 전 출발지·도착지 보호 — 시작·끝 반경 내 좌표를 잘라낸다. */
object RoutePrivacy {

    const val DEFAULT_TRIM_M = 150.0

    /**
     * 경로 앞쪽에서 시작점 반경 [radiusM] 이내 구간을, 뒤쪽에서 끝점 반경 이내 구간을 제거.
     * 너무 짧아 모두 잘리면 원본을 반환(빈 경로 방지).
     */
    fun trimEnds(path: List<LatLngPoint>, radiusM: Double = DEFAULT_TRIM_M): List<LatLngPoint> {
        if (path.size < 3) return path
        val start = path.first()
        val end = path.last()
        var head = 0
        while (head < path.size &&
            Haversine.meters(start.lat, start.lon, path[head].lat, path[head].lon) <= radiusM
        ) head++
        var tail = path.size - 1
        while (tail >= 0 &&
            Haversine.meters(end.lat, end.lon, path[tail].lat, path[tail].lon) <= radiusM
        ) tail--
        if (head > tail) return path // 전부 잘릴 정도로 짧으면 원본 유지
        return path.subList(head, tail + 1).toList()
    }
}
