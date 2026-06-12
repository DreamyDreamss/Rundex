package com.rundex.routepoc

import org.json.JSONObject
import java.io.File

data class DexEntry(val code: String, val name: String, val firstVisitMs: Long, val totalMeters: Double)
data class GradeUp(val code: String, val name: String, val from: Grade, val to: Grade)
data class ApplyResult(val newRegions: List<DexEntry>, val gradeUps: List<GradeUp>)

/** 도감 상태(<dir>/dex.json): 동별 첫방문·누적거리 + 평생 누적거리 */
class DexStore(private val dir: File) {

    private val file = File(dir, "dex.json")

    init {
        dir.mkdirs()
    }

    fun applyRun(
        distanceByRegion: Map<String, Double>,
        names: Map<String, String>,
        runDistanceMeters: Double,
        nowMs: Long,
    ): ApplyResult {
        val root = loadRoot()
        val regions = root.getJSONObject("regions")
        val newRegions = mutableListOf<DexEntry>()
        val gradeUps = mutableListOf<GradeUp>()
        distanceByRegion.forEach { (code, meters) ->
            val name = names[code] ?: code
            if (!regions.has(code)) {
                regions.put(code, JSONObject().apply {
                    put("name", name)
                    put("firstVisitMs", nowMs)
                    put("totalMeters", meters)
                })
                newRegions.add(DexEntry(code, name, nowMs, meters))
            } else {
                val e = regions.getJSONObject(code)
                val before = e.getDouble("totalMeters")
                val after = before + meters
                e.put("totalMeters", after)
                if (Grades.gradeOf(before) != Grades.gradeOf(after)) {
                    gradeUps.add(GradeUp(code, name, Grades.gradeOf(before), Grades.gradeOf(after)))
                }
            }
        }
        root.put("lifetimeMeters", root.optDouble("lifetimeMeters", 0.0) + runDistanceMeters)
        file.writeText(root.toString())
        return ApplyResult(newRegions, gradeUps)
    }

    fun entries(): List<DexEntry> {
        val regions = loadRoot().getJSONObject("regions")
        return regions.keys().asSequence().map { code ->
            val e = regions.getJSONObject(code)
            DexEntry(code, e.getString("name"), e.getLong("firstVisitMs"), e.getDouble("totalMeters"))
        }.sortedByDescending { it.firstVisitMs }.toList()
    }

    fun discoveredCount(): Int = loadRoot().getJSONObject("regions").length()

    fun lifetimeMeters(): Double = loadRoot().optDouble("lifetimeMeters", 0.0)

    fun bestGradeReached(grade: Grade): Boolean =
        entries().any { Grades.gradeOf(it.totalMeters) >= grade }

    private fun loadRoot(): JSONObject =
        if (file.exists()) JSONObject(file.readText())
        else JSONObject().put("regions", JSONObject()).put("lifetimeMeters", 0.0)
}
