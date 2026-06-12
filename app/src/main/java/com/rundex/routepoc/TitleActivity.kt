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

/** 칭호 보관함 — 획득(이름·날짜) / 미획득(조건 힌트) */
class TitleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title)

        val owned = TitleStore(File(filesDir, "data")).owned()
        findViewById<TextView>(R.id.titleHeader).text =
            "🏅 칭호 ${owned.size} / ${Titles.all.size}"

        val fmt = SimpleDateFormat("M/d", Locale.KOREA)
        val rows = Titles.all.map { def ->
            val at = owned[def.id]
            if (at != null) "🏅 ${def.name} — ${fmt.format(Date(at))} 획득"
            else "🔒 ${def.name} — ${def.desc}"
        }
        findViewById<ListView>(R.id.titleList).adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }
}
