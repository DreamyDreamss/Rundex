package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

/** 추천 경로(Discover) — 공개 코스 목록 + 내 코스 등록. 서버(Supabase) 연동. */
class DiscoverActivity : Activity() {

    private val rows = ArrayList<SocialRow>()
    private lateinit var adapter: SocialRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        adapter = SocialRowAdapter(this, rows)
        findViewById<ListView>(R.id.discoverList).adapter = adapter
        findViewById<Button>(R.id.registerRouteButton).setOnClickListener { registerMyRoute() }

        if (!ApiConfig.enabled) {
            findViewById<TextView>(R.id.discoverHeader).text = "🧭 추천 코스\n(서버 연결 전)"
        }
        loadRoutes()
    }

    private fun loadRoutes() {
        ApiClient(Session(this)).listRoutes("is_public=eq.true&order=created_at.desc&limit=50") { result ->
            result.onSuccess { arr ->
                runOnUiThread {
                    rows.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val km = String.format(Locale.US, "%.1f", o.optDouble("distance_m") / 1000.0)
                        rows.add(
                            SocialRow(
                                "🗺", o.optString("name"),
                                "${km}km · 난이도 ${"★".repeat(o.optInt("difficulty", 1))}", "›"
                            )
                        )
                    }
                    if (rows.isEmpty()) rows.add(SocialRow("🧭", "아직 등록된 추천 코스가 없습니다", "첫 코스를 등록해보세요!"))
                    adapter.notifyDataSetChanged()
                }
            }.onFailure {
                runOnUiThread {
                    rows.clear()
                    rows.add(SocialRow("🧭", "추천 코스를 불러올 수 없어요", "서버 연결 후 사용할 수 있습니다"))
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    /** 마지막으로 만든 계획 코스를 출발지 trim 후 공개 등록 */
    private fun registerMyRoute() {
        if (!ApiConfig.enabled) {
            Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        val plan = PlannedRouteStore(File(filesDir, "data")).load()
        if (plan == null || plan.points.size < 2) {
            Toast.makeText(this, "먼저 코스를 만들어 저장하세요", Toast.LENGTH_SHORT).show()
            return
        }
        val safe = RoutePrivacy.trimEnds(plan.points)
        val ewkt = "SRID=4326;LINESTRING(" +
            safe.joinToString(", ") { "${it.lon} ${it.lat}" } + ")"
        val body = org.json.JSONObject()
            .put("name", "내 코스 ${String.format(Locale.US, "%.1f", plan.distanceMeters / 1000.0)}km")
            .put("distance_m", plan.distanceMeters)
            .put("difficulty", 2)
            .put("geom", ewkt)
            .put("is_public", true)
        ApiClient(Session(this)).createRoute(body) { result ->
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, "코스를 등록했어요", Toast.LENGTH_SHORT).show()
                    loadRoutes()
                }.onFailure {
                    Toast.makeText(this, "등록 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
