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
        findViewById<TextView>(R.id.profileTitle).setOnClickListener { chooseRepresentativeTitle() }
        findViewById<Button>(R.id.openTitlesButton).setOnClickListener {
            startActivity(Intent(this, TitleActivity::class.java))
        }
        findViewById<Button>(R.id.openRunsButton).setOnClickListener { showRuns() }
        findViewById<Button>(R.id.openDiscoverButton).setOnClickListener {
            startActivity(Intent(this, DiscoverActivity::class.java))
        }
        findViewById<Button>(R.id.openFeedButton).setOnClickListener {
            startActivity(Intent(this, FeedActivity::class.java))
        }
        findViewById<Button>(R.id.openUsersButton).setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }
        findViewById<Button>(R.id.openCrewButton).setOnClickListener {
            startActivity(Intent(this, CrewActivity::class.java))
        }
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

        // 대표 칭호 = 사용자가 고른 것, 없으면 가장 최근 획득 (탭하면 변경)
        val repId = prefs.getString("rep_title_id", null)?.takeIf { owned.containsKey(it) }
        val rep = (repId ?: owned.maxByOrNull { it.value }?.key)
            ?.let { id -> Titles.all.firstOrNull { it.id == id } }
        findViewById<TextView>(R.id.profileTitle).text =
            rep?.let { "🏅 ${it.name}  ✎" } ?: "칭호를 선택하세요"

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

    /** 보유 칭호 중 프로필 대표로 노출할 칭호를 고른다 (서버엔 best-effort 동기화) */
    private fun chooseRepresentativeTitle() {
        val owned = TitleStore(File(filesDir, "data")).owned()
        val items = Titles.all.filter { owned.containsKey(it.id) }
        if (items.isEmpty()) {
            Toast.makeText(this, "아직 보유한 칭호가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { "🏅 ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("대표 칭호 선택")
            .setItems(labels) { _, i ->
                val id = items[i].id
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putString("rep_title_id", id).apply()
                refresh()
                // 서버가 켜져 있으면 대표 칭호 동기화 (없으면 조용히 스킵)
                val session = Session(this)
                session.userId?.let { uid ->
                    ApiClient(session).patchMe(
                        uid,
                        org.json.JSONObject().put("rep_title_ids", org.json.JSONArray(listOf(id)))
                    )
                }
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
