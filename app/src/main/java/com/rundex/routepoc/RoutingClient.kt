package com.rundex.routepoc

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** FOSSGIS OSRM 공개 서버(foot 프로파일)로 두 점 사이 보행 경로를 요청 */
class RoutingClient(private val http: OkHttpClient = OkHttpClient()) {

    fun fetchRoute(from: LatLngPoint, to: LatLngPoint, callback: (Result<Leg>) -> Unit) {
        val url = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/" +
            "${from.lon},${from.lat};${to.lon},${to.lat}" +
            "?overview=full&geometries=polyline"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "rundex-route-poc/0.1")
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${it.code}")))
                        return
                    }
                    val body = it.body?.string().orEmpty()
                    callback(runCatching { parseRouteResponse(body) })
                }
            }
        })
    }

    companion object {
        fun parseRouteResponse(json: String): Leg {
            val root = JSONObject(json)
            val code = root.optString("code")
            require(code == "Ok") { "OSRM 응답 code=$code" }
            val route = root.getJSONArray("routes").getJSONObject(0)
            return Leg(
                points = PolylineDecoder.decode(route.getString("geometry")),
                distanceMeters = route.getDouble("distance"),
            )
        }
    }
}
