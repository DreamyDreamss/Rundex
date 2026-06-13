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

/** 칭호 보관함 — 획득(이름·날짜·보유율) / 미획득(조건 힌트) */
class TitleActivity : Activity() {

    private val rows = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val fmt = SimpleDateFormat("M/d", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title)

        val owned = TitleStore(File(filesDir, "data")).owned()
        findViewById<TextView>(R.id.titleHeader).text =
            "🏅 칭호 ${owned.size} / ${Titles.all.size}"

        rebuild(owned, rarity = emptyMap())
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        findViewById<ListView>(R.id.titleList).adapter = adapter

        // 서버가 켜져 있으면 보유율(희소성)을 받아 갱신
        ApiClient(Session(this)).getTitleRarity { result ->
            result.onSuccess { arr ->
                val map = HashMap<String, Double>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    map[o.getString("code")] = o.optDouble("rarity", 0.0)
                }
                runOnUiThread {
                    rebuild(owned, map)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun rebuild(owned: Map<String, Long>, rarity: Map<String, Double>) {
        rows.clear()
        Titles.all.forEach { def ->
            val at = owned[def.id]
            val rare = rarity[def.id]?.let { " · 보유 ${String.format(Locale.US, "%.1f", it * 100)}%" } ?: ""
            rows.add(
                if (at != null) "🏅 ${def.name} — ${fmt.format(Date(at))} 획득$rare"
                else "🔒 ${def.name} — ${def.desc}"
            )
        }
    }
}
