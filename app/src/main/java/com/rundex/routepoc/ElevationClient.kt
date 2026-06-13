package com.rundex.routepoc

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Open-Meteo Elevation API(무료·키 불필요)로 좌표열의 고도를 받아 누적 상승고도를 계산한다.
 * 경로 계획 화면의 예상 상승고도 표시용. 실패해도 앱 동작에는 영향 없음(best-effort).
 */
class ElevationClient(private val http: OkHttpClient = OkHttpClient()) {

    /** 경로를 최대 maxPoints개로 균등 샘플링해 고도를 조회하고 누적 상승고도(m)를 콜백 */
    fun gainForRoute(points: List<LatLngPoint>, maxPoints: Int = 80, cb: (Result<Double>) -> Unit) {
        if (points.size < 2) return cb(Result.success(0.0))
        val sampled = sample(points, maxPoints)
        val lats = sampled.joinToString(",") { String.format("%.5f", it.lat) }
        val lons = sampled.joinToString(",") { String.format("%.5f", it.lon) }
        val url = "https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons"
        val req = Request.Builder().url(url).header("User-Agent", "rundex/1.0").build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cb(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return cb(Result.failure(IOException("HTTP ${it.code}")))
                    cb(runCatching { parseGain(it.body?.string().orEmpty()) })
                }
            }
        })
    }

    companion object {
        fun sample(points: List<LatLngPoint>, maxPoints: Int): List<LatLngPoint> {
            if (points.size <= maxPoints) return points
            val step = (points.size - 1).toDouble() / (maxPoints - 1)
            return (0 until maxPoints).map { points[(it * step).toInt()] }
        }

        fun parseGain(json: String): Double {
            val arr = JSONObject(json).getJSONArray("elevation")
            val elev = (0 until arr.length()).map { arr.getDouble(it) }
            return ElevationGain.totalGain(elev)
        }
    }
}
