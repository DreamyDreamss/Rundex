package com.rundex.routepoc

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder

/** 검색 추천으로 표시할 한 곳(주소/장소) */
data class GeoPlace(
    val name: String,
    val displayName: String,
    val lat: Double,
    val lon: Double,
)

/** OSM Nominatim 공개 서버로 주소·장소를 검색해 추천 목록을 만든다 */
class GeocodingClient(private val http: OkHttpClient = OkHttpClient()) {

    fun search(query: String, callback: (Result<List<GeoPlace>>) -> Unit) {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search" +
            "?q=$q&format=jsonv2&limit=6&addressdetails=0" +
            "&countrycodes=kr&accept-language=ko"
        val request = Request.Builder()
            .url(url)
            // Nominatim 이용 정책상 식별 가능한 User-Agent 필수
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
                    callback(runCatching { parseSearchResponse(body) })
                }
            }
        })
    }

    companion object {
        fun parseSearchResponse(json: String): List<GeoPlace> {
            val arr = JSONArray(json)
            val out = ArrayList<GeoPlace>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val display = o.optString("display_name")
                if (display.isEmpty()) continue
                // jsonv2의 name이 비면 display_name의 첫 토막을 대표 이름으로 사용
                val name = o.optString("name").ifEmpty {
                    display.substringBefore(",").trim()
                }
                out.add(
                    GeoPlace(
                        name = name,
                        displayName = display,
                        lat = o.getDouble("lat"),
                        lon = o.getDouble("lon"),
                    )
                )
            }
            return out
        }
    }
}
