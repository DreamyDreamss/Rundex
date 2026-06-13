package com.rundex.routepoc

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 진행 중인 기록의 스냅샷 — 서비스 강제종료 후 복구용 */
data class ActiveRun(
    val startedAtMs: Long,
    val distanceMeters: Double,
    val elevationGainM: Double,
    val points: List<TrackPoint>,
)

/**
 * 기록 중 상태를 <dir>/active_run.json에 주기 저장한다.
 * 파일이 존재하면 "정상 종료되지 않은 기록"이 있다는 뜻(복구 대상).
 * 정상 종료 시 clear()로 삭제한다.
 */
class ActiveRunStore(private val dir: File) {

    private val file = File(dir, "active_run.json")

    init {
        dir.mkdirs()
    }

    fun save(run: ActiveRun) {
        val o = JSONObject().apply {
            put("startedAtMs", run.startedAtMs)
            put("distanceMeters", run.distanceMeters)
            put("elevationGainM", run.elevationGainM)
            put("points", JSONArray().apply {
                run.points.forEach { p ->
                    put(JSONArray().apply { put(p.lat); put(p.lon); put(p.timeMs) })
                }
            })
        }
        file.writeText(o.toString())
    }

    fun exists(): Boolean = file.exists()

    fun load(): ActiveRun? = try {
        if (!file.exists()) null
        else {
            val o = JSONObject(file.readText())
            val pts = o.getJSONArray("points")
            ActiveRun(
                startedAtMs = o.getLong("startedAtMs"),
                distanceMeters = o.getDouble("distanceMeters"),
                elevationGainM = o.optDouble("elevationGainM", 0.0),
                points = (0 until pts.length()).map { i ->
                    val p = pts.getJSONArray(i)
                    TrackPoint(p.getDouble(0), p.getDouble(1), p.getLong(2))
                },
            )
        }
    } catch (e: Exception) {
        null
    }

    fun clear() {
        file.delete()
    }
}
