package com.rundex.routepoc

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.io.File

/** 테마 도감 — 큐레이션 컬렉션별 수집 진행도 + 장소 체크리스트 */
class ThemeDexActivity : Activity() {

    private val store by lazy { ThemeStore(File(filesDir, "data")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_dex)

        val collections = ThemeRepo.all(this)
        val totalPlaces = collections.sumOf { it.places.size }
        val collectedTotal = collections.sumOf { store.collected(it.slug).size }
        val done = store.completedSlugs(collections).size
        findViewById<TextView>(R.id.themeHeader).text =
            "🌟 테마 도감 ${collectedTotal} / ${totalPlaces}\n완성한 컬렉션 ${done} / ${collections.size}"

        val list = findViewById<ListView>(R.id.themeList)
        list.adapter = object : ArrayAdapter<ThemeCollection>(this, R.layout.row_theme, collections) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.row_theme, parent, false)
                val col = getItem(position)!!
                val n = store.collected(col.slug).size
                val total = col.places.size
                val complete = n >= total
                v.findViewById<TextView>(R.id.themeIcon).text = col.emoji
                v.findViewById<TextView>(R.id.themeTitle).text = col.title + if (complete) "  🏆" else ""
                v.findViewById<TextView>(R.id.themeCount).text = "$n/$total"
                v.findViewById<TextView>(R.id.themeDesc).text = if (complete) "완성! ${col.desc}" else col.desc
                v.findViewById<android.widget.ProgressBar>(R.id.themeBar).progress =
                    if (total > 0) (n * 1000 / total) else 0
                return v
            }
        }
        list.setOnItemClickListener { _, _, pos, _ -> showChecklist(collections[pos]) }
    }

    /** 컬렉션 장소 체크리스트 — 수집/미수집을 카드 행으로 */
    private fun showChecklist(col: ThemeCollection) {
        val got = store.collected(col.slug)
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), px(8))
        }
        col.places.forEach { place ->
            val collected = place.id in got
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, px(8), 0, px(8))
            }
            row.addView(TextView(this).apply {
                text = if (collected) "✅" else "🔒"; textSize = 18f
                setPadding(0, 0, px(12), 0)
            })
            row.addView(TextView(this).apply {
                text = place.name; textSize = 15f
                setTextColor(getColor(if (collected) R.color.textDark else R.color.textGrey))
                if (collected) setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            box.addView(row)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("${col.emoji} ${col.title}  ${got.size}/${col.places.size}")
            .setView(scroll)
            .setPositiveButton("닫기", null)
            .show()
    }
}
