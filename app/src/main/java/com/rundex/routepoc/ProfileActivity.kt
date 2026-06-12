package com.rundex.routepoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 프로필 — 이름·대표 칭호·통계 2×2 + 칭호 보관함·러닝 기록 진입 */
class ProfileActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        NavBar.setup(this, R.id.navProfile)

        findViewById<TextView>(R.id.profileName).setOnClickListener { editName() }
        findViewById<Button>(R.id.openTitlesButton).setOnClickListener {
            startActivity(Intent(this, TitleActivity::class.java))
        }
        findViewById<Button>(R.id.openRunsButton).setOnClickListener { showRuns() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        findViewById<TextView>(R.id.profileName).text = prefs.getString("name", "러너")

        val dataDir = File(filesDir, "data")
        val dex = DexStore(dataDir)
        val owned = TitleStore(dataDir).owned()
        val runs = TrackStore(File(filesDir, "tracks")).list()
        val index = RegionRepo.get(this)

        // 대표 칭호 = 가장 최근 획득
        val latest = owned.maxByOrNull { it.value }?.key
            ?.let { id -> Titles.all.firstOrNull { it.id == id } }
        findViewById<TextView>(R.id.profileTitle).text =
            latest?.let { "🏅 ${it.name}" } ?: "아직 칭호가 없습니다"

        val totalKm = runs.sumOf { it.distanceMeters } / 1000.0
        findViewById<TextView>(R.id.statDistance).text =
            "총 거리\n${String.format(Locale.US, "%.1f", totalKm)} km"
        findViewById<TextView>(R.id.statRuns).text = "러닝\n${runs.size}회"
        findViewById<TextView>(R.id.statDex).text = if (index != null) {
            "발견 동\n${dex.discoveredCount()} / ${index.totalCount}"
        } else {
            "발견 동\n${dex.discoveredCount()}"
        }
        findViewById<TextView>(R.id.statTitles).text = "칭호\n${owned.size} / ${Titles.all.size}"
    }

    private fun editName() {
        val input = EditText(this).apply {
            setText(getSharedPreferences("profile", MODE_PRIVATE).getString("name", "러너"))
        }
        AlertDialog.Builder(this)
            .setTitle("이름 변경")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "러너" }
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putString("name", name).apply()
                refresh()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showRuns() {
        val summaries = TrackStore(File(filesDir, "tracks")).list()
        if (summaries.isEmpty()) {
            Toast.makeText(this, "저장된 기록 없음", Toast.LENGTH_SHORT).show()
            return
        }
        val fmt = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
        val labels = summaries.map {
            "${fmt.format(Date(it.startedAtMs))} · " +
                "${TrackActivity.formatKm(it.distanceMeters)} · " +
                TrackActivity.formatDuration(it.durationMs)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("러닝 기록")
            .setItems(labels) { _, i ->
                startActivity(
                    Intent(this, TrackViewActivity::class.java)
                        .putExtra("trackId", summaries[i].id)
                )
            }
            .show()
    }
}
