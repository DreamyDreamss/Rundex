package com.rundex.routepoc

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 서버 업로드 대기 큐 — 네트워크 끊김·토큰 만료 중 완주한 러닝이 유실되지 않도록
 * 페이로드를 디스크에 보관하고, 앱 재개/다음 러닝 때 재시도한다.
 */
class PendingUploadStore(dir: File) {
    private val file = File(dir.apply { mkdirs() }, "pending_runs.json")

    private fun load(): JSONArray = runCatching {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    }.getOrDefault(JSONArray())

    private fun save(arr: JSONArray) = runCatching { file.writeText(arr.toString()) }

    /** 페이로드를 큐에 적재하고 localId 반환 */
    fun add(payload: JSONObject): String {
        val localId = "${payload.optString("startedAt")}_${payload.optDouble("distanceM").toInt()}"
        val arr = load()
        // 같은 localId가 있으면 교체
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("localId") != localId) out.put(o)
        }
        out.put(JSONObject().put("localId", localId).put("payload", payload))
        save(out)
        return localId
    }

    fun remove(localId: String) {
        val arr = load()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("localId") != localId) out.put(o)
        }
        save(out)
    }

    /** (localId, payload) 목록 */
    fun all(): List<Pair<String, JSONObject>> {
        val arr = load()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val p = o.optJSONObject("payload") ?: return@mapNotNull null
            o.optString("localId") to p
        }
    }

    fun count(): Int = load().length()
}
