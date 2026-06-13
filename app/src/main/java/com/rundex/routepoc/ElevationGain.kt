package com.rundex.routepoc

/**
 * 누적 상승고도(m) 계산. GPS·DEM 고도열에서 오르막 구간 합만 누적한다.
 * 직전 채택 고도 대비 threshold(기본 3m) 이상 오를 때만 가산해 GPS 고도 노이즈를 흡수한다.
 */
object ElevationGain {

    fun totalGain(altitudes: List<Double>, threshold: Double = 3.0): Double {
        if (altitudes.size < 2) return 0.0
        var gain = 0.0
        for (i in 1 until altitudes.size) {
            val d = altitudes[i] - altitudes[i - 1]
            if (d >= threshold) gain += d
        }
        return gain
    }
}
