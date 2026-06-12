package com.rundex.routepoc

/**
 * GPS 노이즈 3단 게이트 (실측 튐 대응):
 * 1) 정확도 게이트  2) 속도(순간이동) 게이트  3) 최소 이동(정지 지터) 게이트
 * 첫 픽스는 더 엄격한 정확도(콜드 픽스 배제)를 요구한다.
 */
class LocationFilter(
    private val maxAccuracyM: Float = 25f,
    private val firstFixMaxAccuracyM: Float = 20f,
    private val maxSpeedMps: Double = 12.5,
    private val minMoveM: Double = 4.0,
) {
    var lastAccepted: TrackPoint? = null
        private set
    var rejectedCount = 0
        private set

    /** @return 채택 여부. 채택 시 lastAccepted 갱신 */
    fun offer(lat: Double, lon: Double, accuracyM: Float, timeMs: Long): Boolean {
        val last = lastAccepted
        if (last == null) {
            if (accuracyM > firstFixMaxAccuracyM) return reject()
        } else {
            if (accuracyM > maxAccuracyM) return reject()
            val dtSec = (timeMs - last.timeMs) / 1000.0
            if (dtSec <= 0) return reject()
            val d = Haversine.meters(last.lat, last.lon, lat, lon)
            if (d / dtSec > maxSpeedMps) return reject()
            if (d < minMoveM) return reject()
        }
        lastAccepted = TrackPoint(lat, lon, timeMs)
        return true
    }

    private fun reject(): Boolean {
        rejectedCount++
        return false
    }

    fun reset() {
        lastAccepted = null
        rejectedCount = 0
    }
}
