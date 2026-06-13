package com.rundex.routepoc

data class TrackPoint(val lat: Double, val lon: Double, val timeMs: Long)

data class SavedTrack(
    val id: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val distanceMeters: Double,
    val points: List<TrackPoint>,
    val elevationGainM: Double = 0.0,
)

data class TrackSummary(
    val id: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val distanceMeters: Double,
)
