package com.rundex.routepoc

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 진행 중인 기록의 단일 상태. Service가 쓰고 Activity가 읽는다.
 * LocationFilter(정확도·순간이동·정지지터 게이트)를 통과한 좌표만 채택해 거리를 누적.
 */
object TrackRecorder {

    interface Listener { fun onUpdate() }

    private val filter = LocationFilter()

    var recording = false; private set
    var startedAtMs = 0L; private set
    var distanceMeters = 0.0; private set
    var elevationGainM = 0.0; private set

    private val _points = mutableListOf<TrackPoint>()
    val points: List<TrackPoint> get() = _points

    private var lastAltitude = Double.NaN
    private const val ELEV_THRESHOLD_M = 3.0

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun start(nowMs: Long) {
        reset()
        recording = true
        startedAtMs = nowMs
    }

    /** @return 점이 채택되면 true (기록 중 아님/필터 폐기면 false) */
    fun addPoint(lat: Double, lon: Double, accuracyM: Float, nowMs: Long, altitudeM: Double = Double.NaN): Boolean {
        if (!recording || !filter.offer(lat, lon, accuracyM, nowMs)) return false
        val last = _points.lastOrNull()
        if (last != null) {
            distanceMeters += Haversine.meters(last.lat, last.lon, lat, lon)
        }
        if (!altitudeM.isNaN()) {
            if (!lastAltitude.isNaN() && altitudeM - lastAltitude >= ELEV_THRESHOLD_M) {
                elevationGainM += altitudeM - lastAltitude
            }
            lastAltitude = altitudeM
        }
        _points.add(TrackPoint(lat, lon, nowMs))
        listeners.forEach { it.onUpdate() }
        return true
    }

    fun stop(nowMs: Long): SavedTrack {
        recording = false
        return SavedTrack(
            id = startedAtMs.toString(),
            startedAtMs = startedAtMs,
            durationMs = nowMs - startedAtMs,
            distanceMeters = distanceMeters,
            points = _points.toList(),
            elevationGainM = elevationGainM,
        )
    }

    /** 검증용 — 이번 기록에서 필터가 폐기한 픽스 수 */
    val rejectedCount: Int get() = filter.rejectedCount

    /** 필터와 무관한 최신 수신 픽스 — 실시간 위치 점·카메라 팔로우용 */
    @Volatile var lastRawLat = Double.NaN; private set
    @Volatile var lastRawLon = Double.NaN; private set

    /** 모든 수신 픽스를 기록 채택 여부와 무관하게 반영하고 리스너에 통지 */
    fun updateRaw(lat: Double, lon: Double) {
        lastRawLat = lat
        lastRawLon = lon
        listeners.forEach { it.onUpdate() }
    }

    fun reset() {
        recording = false
        startedAtMs = 0L
        distanceMeters = 0.0
        elevationGainM = 0.0
        lastAltitude = Double.NaN
        _points.clear()
        filter.reset()
    }
}
