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
        findViewById<Button>(R.id.openRunsButton).setOnClickListener {
            startActivity(Intent(this, RunHistoryActivity::class.java))
        }
        findViewById<Button>(R.id.openDiscoverButton).setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
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
        val nameView = findViewById<TextView>(R.id.profileName)
        val localName = prefs.getString("name", null)
        nameView.text = localName ?: "러너"
        // 서버 프로필에서 닉네임(최초)·핸들 동기화
        val sessionP = Session(this)
        if (ApiConfig.enabled) sessionP.userId?.let { uid ->
            ApiClient(sessionP).getProfile(uid) { r ->
                r.onSuccess { arr ->
                    val o = arr.optJSONObject(0) ?: return@onSuccess
                    val name = o.optString("display_name")
                    val handle = o.optString("handle")
                    runOnUiThread {
                        if (localName == null && name.isNotBlank() && name != "null") {
                            prefs.edit().putString("name", name).apply()
                            nameView.text = name
                        }
                        findViewById<TextView>(R.id.profileHandle).text =
                            if (handle.isNotBlank() && handle != "null") "@$handle" else "@아이디 설정하기"
                    }
                }
            }
        }

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

        // 우선 로컬 값으로 즉시 표시 (오프라인/초기), 이후 서버 통계로 덮어쓴다
        val total = index?.totalCount ?: 3482
        val totalKm = runs.sumOf { it.distanceMeters } / 1000.0
        findViewById<TextView>(R.id.statDistance).text =
            "총 거리\n${String.format(Locale.US, "%.1f", totalKm)} km"
        findViewById<TextView>(R.id.statRuns).text = "러닝\n${runs.size}회"
        findViewById<TextView>(R.id.statDex).text = "발견 동\n${dex.discoveredCount()} / $total"
        findViewById<TextView>(R.id.statTitles).text = "칭호\n${owned.size} / ${Titles.all.size}"

        // 서버 원천 통계로 동기화 (프로필 ↔ 도감/기록 수치 일치)
        val session = Session(this)
        if (ApiConfig.enabled && session.userId != null) {
            ApiClient(session).getMyStats { r ->
                r.onSuccess { o ->
                    runOnUiThread {
                        findViewById<TextView>(R.id.statDistance).text =
                            "총 거리\n${String.format(Locale.US, "%.1f", o.optDouble("totalM") / 1000.0)} km"
                        findViewById<TextView>(R.id.statRuns).text = "러닝\n${o.optInt("runs")}회"
                        findViewById<TextView>(R.id.statDex).text = "발견 동\n${o.optInt("dex")} / $total"
                        findViewById<TextView>(R.id.statTitles).text =
                            "칭호\n${o.optInt("titles")} / ${Titles.all.size}"
                        findViewById<TextView>(R.id.weekDistance).text =
                            "${String.format(Locale.US, "%.1f", o.optDouble("weekM") / 1000.0)} km"
                        findViewById<TextView>(R.id.weekRuns).text = "${o.optInt("weekRuns")}회"
                        findViewById<TextView>(R.id.statFollow).text =
                            "${o.optInt("followers")} · ${o.optInt("following")}"
                    }
                }
            }
        }
    }

    private fun editName() {
        val view = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val input = view.findViewById<EditText>(R.id.nameInput)
        val handleInput = view.findViewById<EditText>(R.id.handleInput)
        input.setText(getSharedPreferences("profile", MODE_PRIVATE).getString("name", "러너"))
        input.setSelection(input.text.length)

        val session = Session(this)
        // 현재 핸들 채워넣기
        session.userId?.let { uid ->
            ApiClient(session).getProfile(uid) { r ->
                r.onSuccess { arr ->
                    val h = arr.optJSONObject(0)?.optString("handle")
                    if (!h.isNullOrBlank() && h != "null") runOnUiThread { handleInput.setText(h) }
                }
            }
        }

        val dialog = android.app.Dialog(this, R.style.RundexDialog).apply {
            setContentView(view)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val name = input.text.toString().trim().ifEmpty { "러너" }
            // 핸들 정제: 소문자 영문·숫자·_, 3~20자
            val handle = handleInput.text.toString().trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
            getSharedPreferences("profile", MODE_PRIVATE).edit().putString("name", name).apply()
            refresh()
            session.userId?.let { uid ->
                val body = org.json.JSONObject().put("display_name", name)
                if (handle.length in 3..20) body.put("handle", handle)
                ApiClient(session).patchMe(uid, body) { r ->
                    runOnUiThread {
                        r.onFailure {
                            Toast.makeText(this, "아이디(@$handle)가 이미 사용 중이거나 형식이 올바르지 않아요", Toast.LENGTH_SHORT).show()
                        }
                        r.onSuccess { refresh() }
                    }
                }
            }
            dialog.dismiss()
        }
        dialog.show()
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
        AlertDialog.Builder(this, R.style.RundexDialog)
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

    /** 북마크한 코스 — 서버에서 불러와 목록 표시(탭하면 삭제) */
    private fun showBookmarks() {
        val session = Session(this)
        val uid = session.userId
        if (!ApiConfig.enabled || uid == null) {
            Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient(session).listMyBookmarks(uid) { result ->
            runOnUiThread {
                result.onSuccess { arr ->
                    if (arr.length() == 0) {
                        Toast.makeText(this, "북마크한 코스가 없어요. 러닝 → 추천에서 저장해보세요", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val ids = ArrayList<String>()
                    val labels = ArrayList<String>()
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i).optJSONObject("routes") ?: continue
                        ids.add(r.optString("id"))
                        val km = String.format(Locale.US, "%.1f", r.optDouble("distance_m") / 1000.0)
                        labels.add("🗺 ${r.optString("name")}  ·  ${km}km  ·  ${"★".repeat(r.optInt("difficulty", 1))}")
                    }
                    AlertDialog.Builder(this, R.style.RundexDialog)
                        .setTitle("🔖 북마크한 코스 (탭하면 삭제)")
                        .setItems(labels.toTypedArray()) { _, i ->
                            ApiClient(session).removeBookmark(uid, ids[i]) { r ->
                                runOnUiThread {
                                    r.onSuccess { Toast.makeText(this, "북마크에서 삭제했어요", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                        .setPositiveButton("닫기", null)
                        .show()
                }.onFailure {
                    Toast.makeText(this, "북마크를 불러올 수 없어요", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 내 러닝 기록 — 서버(원천)에서 불러와 공개/비공개를 직접 전환(업로드) */
    private fun showRuns() {
        val session = Session(this)
        val uid = session.userId
        if (!ApiConfig.enabled || uid == null) {
            showLocalRuns()
            return
        }
        ApiClient(session).listMyRuns(uid) { result ->
            runOnUiThread {
                result.onSuccess { arr -> renderRunList(uid, arr) }
                    .onFailure {
                        Toast.makeText(this, "서버 기록을 불러올 수 없어 로컬 기록을 표시합니다", Toast.LENGTH_SHORT).show()
                        showLocalRuns()
                    }
            }
        }
    }

    private fun renderRunList(uid: String, arr: org.json.JSONArray) {
        if (arr.length() == 0) {
            Toast.makeText(this, "서버에 저장된 기록 없음", Toast.LENGTH_SHORT).show()
            return
        }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val fmt = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
        val runs = (0 until arr.length()).map { arr.getJSONObject(it) }
        val labels = runs.map { o ->
            val started = runCatching { iso.parse(o.optString("started_at").take(19)) }.getOrNull()
            val badge = if (o.optString("visibility", "private") == "public") "🔓 공개" else "🔒 비공개"
            "${started?.let { fmt.format(it) } ?: "-"} · " +
                "${TrackActivity.formatKm(o.optDouble("distance_m"))} · " +
                "${TrackActivity.formatDuration(o.optLong("duration_ms"))}   $badge"
        }
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("내 러닝 기록 ${runs.size}개")
            .setItems(labels.toTypedArray()) { _, i -> runActionDialog(runs[i]) }
            .setPositiveButton("닫기", null)
            .show()
    }

    /** 런 1개 액션: 경로 보기 / 공개·비공개 전환 */
    private fun runActionDialog(o: org.json.JSONObject) {
        val isPublic = o.optString("visibility", "private") == "public"
        val toggleLabel = if (isPublic) "🔒 비공개로 전환" else "🔓 피드에 공개"
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setItems(arrayOf("🗺 경로 보기", toggleLabel)) { _, which ->
                if (which == 0) {
                    val geom = o.optJSONObject("geom")
                    if (geom == null) {
                        Toast.makeText(this, "이 기록엔 경로 데이터가 없어요", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    startActivity(
                        Intent(this, TrackViewActivity::class.java)
                            .putExtra("serverGeoJson", geom.toString())
                            .putExtra("distanceM", o.optDouble("distance_m"))
                            .putExtra("durationMs", o.optLong("duration_ms"))
                            .putExtra("caption", o.optString("caption").takeIf { it != "null" } ?: "")
                    )
                } else {
                    val next = if (isPublic) "private" else "public"
                    ApiClient(Session(this)).setRunVisibility(o.optString("id"), next) { r ->
                        runOnUiThread {
                            r.onSuccess {
                                Toast.makeText(this, if (next == "public") "피드에 공개했어요" else "비공개로 전환했어요", Toast.LENGTH_SHORT).show()
                                showRuns()
                            }.onFailure { Toast.makeText(this, "전환 실패", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showLocalRuns() {
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
