package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** 소셜 피드 — 팔로우/본인 활동(러닝·발견·승급) + 이모지 반응. 서버(Supabase) 연동. */
class FeedActivity : Activity() {

    private val rows = ArrayList<SocialRow>()
    private val activityIds = ArrayList<String>()
    private lateinit var adapter: SocialRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)

        adapter = SocialRowAdapter(this, rows)
        val list = findViewById<ListView>(R.id.feedList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> reactTo(pos) }

        if (!ApiConfig.enabled) {
            findViewById<TextView>(R.id.feedHeader).text = "📣 피드\n(서버 연결 전)"
        }
        load()
    }

    private fun load() {
        ApiClient(Session(this)).getFeed { result ->
            result.onSuccess { arr ->
                runOnUiThread {
                    rows.clear(); activityIds.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        activityIds.add(o.optString("id"))
                        rows.add(toRow(o))
                    }
                    if (rows.isEmpty()) {
                        rows.add(SocialRow("📣", "아직 피드가 없습니다", "팔로우하거나 달려보세요!"))
                    }
                    adapter.notifyDataSetChanged()
                }
            }.onFailure {
                runOnUiThread {
                    rows.clear(); activityIds.clear()
                    rows.add(SocialRow("📣", "피드를 불러올 수 없어요", "서버 연결 후 사용할 수 있습니다"))
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun toRow(o: org.json.JSONObject): SocialRow {
        val p = o.optJSONObject("payload") ?: org.json.JSONObject()
        return when (o.optString("type")) {
            "run_completed" -> {
                val km = String.format(Locale.US, "%.1f", p.optDouble("distanceM") / 1000.0)
                val regions = p.optJSONArray("newRegions")?.length() ?: 0
                SocialRow(
                    "🏃", "${km}km 러닝",
                    if (regions > 0) "새 동 ${regions}곳 발견" else "러닝 완료", "👍"
                )
            }
            "title_earned" -> SocialRow("🏅", "칭호 획득", p.optString("name"), "👍")
            "theme_completed" -> SocialRow("🏆", "컬렉션 완성", p.optString("title"), "👍")
            else -> SocialRow("•", "활동")
        }
    }

    private fun reactTo(pos: Int) {
        if (!ApiConfig.enabled || pos >= activityIds.size) return
        ApiClient(Session(this)).react(activityIds[pos], "👍") { result ->
            runOnUiThread {
                result.onSuccess { Toast.makeText(this, "👍", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
