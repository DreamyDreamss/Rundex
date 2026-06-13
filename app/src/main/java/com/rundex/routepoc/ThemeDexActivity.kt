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
        list.adapter = object : ArrayAdapter<ThemeCollection>(this, R.layout.row_dex, collections) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.row_dex, parent, false)
                val col = getItem(position)!!
                val n = store.collected(col.slug).size
                val complete = n >= col.places.size
                v.findViewById<TextView>(R.id.rowTitle).text =
                    "${col.emoji} ${col.title}  $n/${col.places.size}" + if (complete) "  🏆" else ""
                v.findViewById<TextView>(R.id.rowSub).text =
                    if (complete) "완성! ${col.desc}" else col.desc
                return v
            }
        }
        list.setOnItemClickListener { _, _, pos, _ -> showChecklist(collections[pos]) }
    }

    private fun showChecklist(col: ThemeCollection) {
        val got = store.collected(col.slug)
        val items = col.places.map { (if (it.id in got) "✅ " else "🔒 ") + it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${col.emoji} ${col.title}  ${got.size}/${col.places.size}")
            .setItems(items, null)
            .setPositiveButton("닫기", null)
            .show()
    }
}
