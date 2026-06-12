package com.rundex.routepoc

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 진행 중인 기록의 단일 상태. Service가 쓰고 Activity가 읽는다.
 * 정확도 30m 초과 좌표는 폐기, 통과한 좌표 간 하버사인 거리를 누적.
 */
object TrackRecorder {

    interface Listener { fun onUpdate() }

    private const val MAX_ACCURACY_M = 30f

    var recording = false; private set
    var startedAtMs = 0L; private set
    var distanceMeters = 0.0; private set

    private val _points = mutableListOf<TrackPoint>()
    val points: List<TrackPoint> get() = _points

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun start(nowMs: Long) {
        reset()
        recording = true
        startedAtMs = nowMs
    }

    /** @return 점이 채택되면 true (기록 중 아님/저정확도면 false) */
    fun addPoint(lat: Double, lon: Double, accuracyM: Float, nowMs: Long): Boolean {
        if (!recording || accuracyM > MAX_ACCURACY_M) return false
        val last = _points.lastOrNull()
        if (last != null) {
            distanceMeters += Haversine.meters(last.lat, last.lon, lat, lon)
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
        )
    }

    fun reset() {
        recording = false
        startedAtMs = 0L
        distanceMeters = 0.0
        _points.clear()
    }
}
