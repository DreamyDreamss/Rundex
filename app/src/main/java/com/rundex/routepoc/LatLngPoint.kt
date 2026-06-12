package com.rundex.routepoc

data class LatLngPoint(val lat: Double, val lon: Double)

/** 두 경유점 사이의 스냅된 구간: 디코딩된 좌표열 + 거리(m) */
data class Leg(val points: List<LatLngPoint>, val distanceMeters: Double)
