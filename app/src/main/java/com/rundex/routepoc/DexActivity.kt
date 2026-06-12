package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** 발견 도감 — 완성률 헤더 + 발견 동 목록(등급 뱃지·누적 거리) */
class DexActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dex)

        val dex = DexStore(File(filesDir, "data"))
        val index = RegionRepo.get(this)
        val entries = dex.entries()

        val header = findViewById<TextView>(R.id.dexHeader)
        header.text = if (index != null) {
            val pct = if (index.totalCount > 0) entries.size * 100.0 / index.totalCount else 0.0
            "📒 서울 ${entries.size} / ${index.totalCount} (${String.format(Locale.US, "%.1f", pct)}%)"
        } else {
            "📒 도감 데이터를 불러올 수 없습니다"
        }

        val rows = if (entries.isEmpty()) {
            listOf("아직 발견한 동이 없습니다. 달려서 도감을 채워보세요!")
        } else {
            entries.map {
                "${Grades.badge(it.totalMeters)} ${it.name} — ${Grades.gradeOf(it.totalMeters).label}" +
                    " · ${String.format(Locale.US, "%.1f", it.totalMeters / 1000.0)}km"
            }
        }
        findViewById<ListView>(R.id.dexList).adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }
}
