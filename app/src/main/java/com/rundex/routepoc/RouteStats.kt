package com.rundex.routepoc

/** 코스 작성 화면 상단 통계(예상 소요 시간·지나는 동 개수) 계산 */
object RouteStats {

    /** 러닝 기준 페이스 6'00"/km */
    const val RUNNING_PACE_SEC_PER_KM = 360.0

    /** 거리(m)로 예상 소요 시간을 분 단위로 추정 (반올림) */
    fun estimatedMinutes(distanceMeters: Double): Int =
        Math.round(distanceMeters / 1000.0 * (RUNNING_PACE_SEC_PER_KM / 60.0)).toInt()

    /** 직선 두 점 사이 단순 구간(스냅 끄기용) */
    fun straightLeg(from: LatLngPoint, to: LatLngPoint): Leg =
        Leg(
            points = listOf(from, to),
            distanceMeters = Haversine.meters(from.lat, from.lon, to.lat, to.lon),
        )

    /** 경로가 지나는 서로 다른 행정동 개수 — 과도한 연산을 막기 위해 최대 300점만 샘플 */
    fun distinctRegionCount(path: List<LatLngPoint>, index: RegionIndex): Int {
        if (path.isEmpty()) return 0
        val step = maxOf(1, path.size / 300)
        val codes = HashSet<String>()
        var i = 0
        while (i < path.size) {
            index.findRegion(path[i].lat, path[i].lon)?.let { codes.add(it.code) }
            i += step
        }
        return codes.size
    }
}
