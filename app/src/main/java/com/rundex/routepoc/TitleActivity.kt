package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 칭호 보관함 — 카테고리별 카드. 획득(이름·날짜·보유율) / 미획득(조건 힌트·잠금). 서버(user_titles) 원천. */
class TitleActivity : Activity() {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val outFmt = SimpleDateFormat("M/d", Locale.KOREA)

    private var earned = HashMap<String, String>()    // code → 획득일 라벨
    private var rarity = HashMap<String, Double>()
    private val rows = ArrayList<Row>()
    private lateinit var adapter: TitleAdapter

    /** 카테고리: 표시 라벨 + 아이콘 + 해당 코드 prefix 판정 */
    private data class Cat(val label: String, val icon: String, val match: (String) -> Boolean)
    private val cats = listOf(
        Cat("동네 탐험", "🗺", { it.startsWith("explorer_") || it == "first_card" }),
        Cat("누적 거리", "🏃", { it.startsWith("dist_") }),
        Cat("단골 등급", "⭐", { it.startsWith("first_") && it != "first_card" }),
        Cat("시간대 한정", "⏰", { it == "early_bird" || it == "night_owl" }),
        Cat("테마 컬렉션", "🏆", { it.startsWith("theme_") }),
    )

    private sealed class Row
    private class Section(val cat: Cat, val earnedN: Int, val totalN: Int) : Row()
    private class Item(val def: TitleDef, val cat: Cat) : Row()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title)

        // 로컬 값으로 즉시 표시(오프라인/초기), 이후 서버로 덮어쓴다
        TitleStore(File(filesDir, "data")).owned().forEach { (id, ts) ->
            earned[id] = outFmt.format(Date(ts))
        }
        adapter = TitleAdapter()
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
        for (cat in cats) {
            val defs = Titles.all.filter { cat.match(it.id) }
            if (defs.isEmpty()) continue
            val earnedN = defs.count { earned.containsKey(it.id) }
            rows.add(Section(cat, earnedN, defs.size))
            defs.forEach { rows.add(Item(it, cat)) }
        }
        adapter.notifyDataSetChanged()
    }

    private inner class TitleAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(p: Int) = if (rows[p] is Section) 0 else 1
        override fun isEnabled(p: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return when (val row = rows[position]) {
                is Section -> bindSection(convertView, parent, row)
                is Item -> bindItem(convertView, parent, row)
            }
        }

        private fun bindSection(cv: View?, parent: ViewGroup, s: Section): View {
            val v = cv ?: layoutInflater.inflate(R.layout.row_title_section, parent, false)
            v.findViewById<TextView>(R.id.sectionTitle).text = "${s.cat.icon} ${s.cat.label}"
            v.findViewById<TextView>(R.id.sectionCount).text = "${s.earnedN}/${s.totalN}"
            return v
        }

        private fun bindItem(cv: View?, parent: ViewGroup, item: Item): View {
            val v = cv ?: layoutInflater.inflate(R.layout.row_title, parent, false)
            val def = item.def
            val at = earned[def.id]
            val got = at != null

            v.findViewById<TextView>(R.id.titleIcon).text = if (got) item.cat.icon else "🔒"
            v.findViewById<TextView>(R.id.titleName).text = def.name
            v.findViewById<TextView>(R.id.titleDesc).text = def.desc
            v.findViewById<TextView>(R.id.titleStatus).apply {
                text = if (got) "✓ ${at}" else "미획득"
                setTextColor(getColor(if (got) R.color.primary else R.color.textGrey))
            }
            v.findViewById<TextView>(R.id.titleRarity).apply {
                val rare = rarity[def.id]
                text = if (got && rare != null && rare > 0)
                    "보유 ${String.format(Locale.US, "%.1f", rare * 100)}%" else ""
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
            // 미획득은 살짝 흐리게
            v.findViewById<View>(R.id.titleIcon).alpha = if (got) 1f else 0.5f
            v.findViewById<TextView>(R.id.titleName).alpha = if (got) 1f else 0.6f
            return v
        }
    }
}
