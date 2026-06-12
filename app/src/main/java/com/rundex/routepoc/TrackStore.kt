package com.rundex.routepoc

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 완료된 기록을 <dir>/<id>.json 파일로 저장·조회 */
class TrackStore(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun save(track: SavedTrack) {
        val json = JSONObject().apply {
            put("id", track.id)
            put("startedAtMs", track.startedAtMs)
            put("durationMs", track.durationMs)
            put("distanceMeters", track.distanceMeters)
            put("points", JSONArray().apply {
                track.points.forEach { p ->
                    put(JSONArray().apply { put(p.lat); put(p.lon); put(p.timeMs) })
                }
            })
        }
        File(dir, "${track.id}.json").writeText(json.toString())
    }

    fun list(): List<TrackSummary> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .map { f ->
                val o = JSONObject(f.readText())
                TrackSummary(
                    id = o.getString("id"),
                    startedAtMs = o.getLong("startedAtMs"),
                    durationMs = o.getLong("durationMs"),
                    distanceMeters = o.getDouble("distanceMeters"),
                )
            }
            .sortedByDescending { it.startedAtMs }

    fun load(id: String): SavedTrack {
        val o = JSONObject(File(dir, "$id.json").readText())
        val pts = o.getJSONArray("points")
        return SavedTrack(
            id = o.getString("id"),
            startedAtMs = o.getLong("startedAtMs"),
            durationMs = o.getLong("durationMs"),
            distanceMeters = o.getDouble("distanceMeters"),
            points = (0 until pts.length()).map { i ->
                val p = pts.getJSONArray(i)
                TrackPoint(p.getDouble(0), p.getDouble(1), p.getLong(2))
            },
        )
    }
}
