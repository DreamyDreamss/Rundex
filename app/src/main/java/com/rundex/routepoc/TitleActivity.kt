package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 칭호 보관함 — 획득(이름·날짜·보유율) / 미획득(조건 힌트). 서버(user_titles) 원천. */
class TitleActivity : Activity() {

    private val rows = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val outFmt = SimpleDateFormat("M/d", Locale.KOREA)

    private var earned = HashMap<String, String>()    // code → 획득일 라벨
    private var rarity = HashMap<String, Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title)

        // 로컬 값으로 즉시 표시(오프라인/초기), 이후 서버로 덮어쓴다
        TitleStore(File(filesDir, "data")).owned().forEach { (id, ts) ->
            earned[id] = outFmt.format(Date(ts))
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        findViewById<ListView>(R.id.titleList).adapter = adapter
        rebuild()

        val session = Session(this)
        if (ApiConfig.enabled) session.userId?.let { uid ->
            ApiClient(session).getMyTitles(uid) { r ->
                r.onSuccess { arr ->
                    val m = HashMap<String, String>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val code = o.optJSONObject("titles")?.optString("code") ?: continue
                        val d = runCatching { iso.parse(o.optString("earned_at").take(19)) }.getOrNull()
                        m[code] = d?.let { outFmt.format(it) } ?: "획득"
                    }
                    runOnUiThread { earned = m; rebuild() }
                }
            }
            ApiClient(session).getTitleRarity { r ->
                r.onSuccess { arr ->
                    val m = HashMap<String, Double>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        m[o.getString("code")] = o.optDouble("rarity", 0.0)
                    }
                    runOnUiThread { rarity = m; rebuild() }
                }
            }
        }
    }

    private fun rebuild() {
        findViewById<TextView>(R.id.titleHeader).text = "🏅 칭호 ${earned.size} / ${Titles.all.size}"
        rows.clear()
        Titles.all.forEach { def ->
            val at = earned[def.id]
            val rare = rarity[def.id]?.let { " · 보유 ${String.format(Locale.US, "%.1f", it * 100)}%" } ?: ""
            rows.add(
                if (at != null) "🏅 ${def.name} — $at 획득$rare"
                else "🔒 ${def.name} — ${def.desc}"
            )
        }
        adapter.notifyDataSetChanged()
    }
}
