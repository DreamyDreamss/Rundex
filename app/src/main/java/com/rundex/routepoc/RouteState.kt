package com.rundex.routepoc

/**
 * 경로 작성 상태. 불변식: legs.size <= max(0, waypoints.size - 1)
 * (라우팅 응답 대기 중에는 waypoint가 leg보다 2개 많을 수 있다)
 */
class RouteState {
    private val _waypoints = mutableListOf<LatLngPoint>()
    private val _legs = mutableListOf<Leg>()

    val waypoints: List<LatLngPoint> get() = _waypoints
    val legs: List<Leg> get() = _legs

    fun addWaypoint(p: LatLngPoint) {
        _waypoints.add(p)
    }

    fun addLeg(leg: Leg) {
        _legs.add(leg)
    }

    /** 마지막 경유점을 제거하고, 그에 딸린 구간이 있으면 함께 제거 */
    fun undo() {
        if (_waypoints.isEmpty()) return
        _waypoints.removeAt(_waypoints.lastIndex)
        if (_legs.size > maxOf(0, _waypoints.size - 1)) {
            _legs.removeAt(_legs.lastIndex)
        }
    }

    fun totalDistanceMeters(): Double = _legs.sumOf { it.distanceMeters }

    /** 모든 구간 좌표를 이어 붙인 전체 경로 (지도 LineString용) */
    fun fullPath(): List<LatLngPoint> = _legs.flatMap { it.points }
}
