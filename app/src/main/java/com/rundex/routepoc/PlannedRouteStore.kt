package com.rundex.routepoc

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PlannedRoute(val points: List<LatLngPoint>, val distanceMeters: Double)

/** 마지막으로 계획한 경로 1건 저장(<dir>/planned_route.json) — 따라 뛰기 전달용 */
class PlannedRouteStore(private val dir: File) {

    private val file = File(dir, "planned_route.json")

    init {
        dir.mkdirs()
    }

    fun save(route: PlannedRoute) {
        val o = JSONObject().apply {
            put("distanceMeters", route.distanceMeters)
            put("points", JSONArray().apply {
                route.points.forEach { p ->
                    put(JSONArray().apply { put(p.lat); put(p.lon) })
                }
            })
        }
        file.writeText(o.toString())
    }

    fun load(): PlannedRoute? = try {
        if (!file.exists()) null
        else {
            val o = JSONObject(file.readText())
            val pts = o.getJSONArray("points")
            PlannedRoute(
                points = (0 until pts.length()).map { i ->
                    val p = pts.getJSONArray(i)
                    LatLngPoint(p.getDouble(0), p.getDouble(1))
                },
                distanceMeters = o.getDouble("distanceMeters"),
            )
        }
    } catch (e: Exception) {
        null
    }
}
