package com.rundex.routepoc

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.Locale

/** 발견 도감 — 완성률 헤더 + 발견 동 목록(등급 뱃지·누적·다음 등급까지) */
class DexActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dex)
        NavBar.setup(this, R.id.navDex)
        findViewById<android.widget.Button>(R.id.openThemeDexButton).setOnClickListener {
            startActivity(android.content.Intent(this, ThemeDexActivity::class.java))
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val dex = DexStore(File(filesDir, "data"))
        val index = RegionRepo.get(this)
        val entries = dex.entries()

        findViewById<TextView>(R.id.dexHeader).text = if (index != null) {
            val pct = if (index.totalCount > 0) entries.size * 100.0 / index.totalCount else 0.0
            "📒 서울 ${entries.size} / ${index.totalCount}\n도감 완성률 ${String.format(Locale.US, "%.1f", pct)}%"
        } else {
            "📒 도감 데이터를 불러올 수 없습니다"
        }

        findViewById<ListView>(R.id.dexList).adapter = object : ArrayAdapter<DexEntry>(
            this, R.layout.row_dex, entries
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.row_dex, parent, false)
                val e = getItem(position)!!
                val grade = Grades.gradeOf(e.totalMeters)
                val color = getColor(gradeColorRes(grade))
                v.findViewById<TextView>(R.id.rowTitle).text =
                    "${Grades.badge(e.totalMeters)} ${e.name}"
                v.findViewById<TextView>(R.id.rowGrade).apply {
                    text = grade.label + if (grade == Grade.GOLD) " ★${Grades.starsOf(e.totalMeters)}" else ""
                    setTextColor(color)
                }
                v.findViewById<TextView>(R.id.rowSub).text = buildSub(e)
                v.findViewById<ProgressBar>(R.id.rowProgress).apply {
                    progress = gradeProgressPct(e.totalMeters)
                    progressTintList = ColorStateList.valueOf(color)
                }
                return v
            }
        }

        if (entries.isEmpty()) {
            findViewById<TextView>(R.id.dexHeader).append("\n\n아직 발견한 동이 없습니다. 달려서 채워보세요!")
        }
    }

    private fun buildSub(e: DexEntry): String {
        val km = String.format(Locale.US, "%.1f", e.totalMeters / 1000.0)
        val next = when (Grades.gradeOf(e.totalMeters)) {
            Grade.CARD -> "브론즈까지 ${String.format(Locale.US, "%.1f", (Grades.BRONZE_M - e.totalMeters) / 1000.0)}km"
            Grade.BRONZE -> "실버까지 ${String.format(Locale.US, "%.1f", (Grades.SILVER_M - e.totalMeters) / 1000.0)}km"
            Grade.SILVER -> "골드까지 ${String.format(Locale.US, "%.1f", (Grades.GOLD_M - e.totalMeters) / 1000.0)}km"
            Grade.GOLD -> "다음 ★까지 " +
                String.format(Locale.US, "%.1f", ((Grades.starsOf(e.totalMeters) + 1) * Grades.GOLD_M + Grades.GOLD_M - e.totalMeters) / 1000.0) + "km"
        }
        return "누적 ${km}km · $next"
    }

    private fun gradeColorRes(g: Grade): Int = when (g) {
        Grade.CARD -> R.color.gradeCard
        Grade.BRONZE -> R.color.gradeBronze
        Grade.SILVER -> R.color.gradeSilver
        Grade.GOLD -> R.color.gradeGold
    }

    /** 현재 등급 구간 내 진행률(%) — 프로그레스 바용 */
    private fun gradeProgressPct(m: Double): Int = when (Grades.gradeOf(m)) {
        Grade.CARD -> (m / Grades.BRONZE_M * 100).toInt()
        Grade.BRONZE -> ((m - Grades.BRONZE_M) / (Grades.SILVER_M - Grades.BRONZE_M) * 100).toInt()
        Grade.SILVER -> ((m - Grades.SILVER_M) / (Grades.GOLD_M - Grades.SILVER_M) * 100).toInt()
        Grade.GOLD -> (((m - Grades.GOLD_M) % Grades.GOLD_M) / Grades.GOLD_M * 100).toInt()
    }.coerceIn(0, 100)
}
