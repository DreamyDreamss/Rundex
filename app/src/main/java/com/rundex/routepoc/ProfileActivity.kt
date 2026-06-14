package com.rundex.routepoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** 프로필 — Threads형: 게시물/팔로워/팔로잉 + 소개 + 러닝 기록 그리드. 메뉴는 ☰. */
class ProfileActivity : Activity() {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val dateFmt = SimpleDateFormat("M/d", Locale.KOREA)
    private val labelFmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
    private var postsMode = "posts"      // "posts"(공개=게시물) | "records"(전체 러닝기록)
    private var allRuns = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        NavBar.setup(this, R.id.navProfile)

        findViewById<TextView>(R.id.profileName).setOnClickListener { editName() }
        findViewById<TextView>(R.id.editProfileButton).setOnClickListener { editName() }
        findViewById<TextView>(R.id.profileTitle).setOnClickListener { chooseRepresentativeTitle() }
        findViewById<TextView>(R.id.menuButton).setOnClickListener { showMenu() }
        findViewById<View>(R.id.statFollowers).setOnClickListener { openFollowList("followers") }
        findViewById<View>(R.id.statFollowing).setOnClickListener { openFollowList("following") }
        findViewById<TextView>(R.id.tabPosts).setOnClickListener { switchTab("posts") }
        findViewById<TextView>(R.id.tabRecords).setOnClickListener { switchTab("records") }
        findViewById<TextView>(R.id.registerPost).setOnClickListener { showRegisterPicker() }
        styleTabs()
    }

    private fun switchTab(mode: String) {
        if (postsMode == mode) return
        postsMode = mode
        styleTabs()
        renderPosts(allRuns)
    }

    private fun styleTabs() {
        val posts = findViewById<TextView>(R.id.tabPosts)
        val records = findViewById<TextView>(R.id.tabRecords)
        val on = postsMode == "posts"
        posts.setTextColor(getColor(if (on) R.color.primary else R.color.textGrey))
        records.setTextColor(getColor(if (on) R.color.textGrey else R.color.primary))
        findViewById<View>(R.id.registerPost).visibility = if (on) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        refresh()
        loadPosts()
    }

    private fun refresh() {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        val nameView = findViewById<TextView>(R.id.profileName)
        val topName = findViewById<TextView>(R.id.profileTopName)
        val localName = prefs.getString("name", null)
        nameView.text = localName ?: "러너"
        topName.text = localName ?: "러너"

        val sessionP = Session(this)
        if (ApiConfig.enabled) sessionP.userId?.let { uid ->
            ApiClient(sessionP).getProfile(uid) { r ->
                r.onSuccess { arr ->
                    val o = arr.optJSONObject(0) ?: return@onSuccess
                    val name = o.optString("display_name")
                    val handle = o.optString("handle")
                    val bio = o.optString("bio")
                    runOnUiThread {
                        if (localName == null && name.isNotBlank() && name != "null") {
                            prefs.edit().putString("name", name).apply()
                            nameView.text = name; topName.text = name
                        }
                        findViewById<TextView>(R.id.profileHandle).text =
                            if (handle.isNotBlank() && handle != "null") "@$handle" else "@아이디 설정하기"
                        findViewById<TextView>(R.id.profileBio).apply {
                            if (bio.isNotBlank() && bio != "null") { text = bio; visibility = View.VISIBLE }
                            else visibility = View.GONE
                        }
                    }
                }
            }
        }

        val dataDir = File(filesDir, "data")
        val dex = DexStore(dataDir)
        val owned = TitleStore(dataDir).owned()
        val runs = TrackStore(File(filesDir, "tracks")).list()
        val index = RegionRepo.get(this)

        val repId = prefs.getString("rep_title_id", null)?.takeIf { owned.containsKey(it) }
        val rep = (repId ?: owned.maxByOrNull { it.value }?.key)
            ?.let { id -> Titles.all.firstOrNull { it.id == id } }
        findViewById<TextView>(R.id.profileTitle).text =
            rep?.let { "🏅 ${it.name}  ✎" } ?: "칭호를 선택하세요"

        val total = index?.totalCount ?: 3482
        val totalKm = runs.sumOf { it.distanceMeters } / 1000.0
        findViewById<TextView>(R.id.statDistance).text = "총 거리\n${String.format(Locale.US, "%.1f", totalKm)} km"
        findViewById<TextView>(R.id.statRuns).text = "러닝\n${runs.size}회"
        findViewById<TextView>(R.id.statDex).text = "발견 동\n${dex.discoveredCount()} / $total"
        findViewById<TextView>(R.id.statTitles).text = "칭호\n${owned.size} / ${Titles.all.size}"

        val session = Session(this)
        if (ApiConfig.enabled && session.userId != null) {
            ApiClient(session).getMyStats { r ->
                r.onSuccess { o ->
                    runOnUiThread {
                        findViewById<TextView>(R.id.statDistance).text =
                            "총 거리\n${String.format(Locale.US, "%.1f", o.optDouble("totalM") / 1000.0)} km"
                        findViewById<TextView>(R.id.statRuns).text = "러닝\n${o.optInt("runs")}회"
                        findViewById<TextView>(R.id.statDex).text = "발견 동\n${o.optInt("dex")} / $total"
                        findViewById<TextView>(R.id.statTitles).text = "칭호\n${o.optInt("titles")} / ${Titles.all.size}"
                        findViewById<TextView>(R.id.postsCount).text = "${o.optInt("runs")}"
                        findViewById<TextView>(R.id.followersCount).text = "${o.optInt("followers")}"
                        findViewById<TextView>(R.id.followingCount).text = "${o.optInt("following")}"
                        val streak = o.optInt("streak")
                        findViewById<TextView>(R.id.streakBanner).apply {
                            if (streak > 0) { text = "🔥 ${streak}일 연속 러닝 중!"; visibility = View.VISIBLE }
                            else visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun openFollowList(kind: String) {
        val uid = Session(this).userId ?: return
        startActivity(Intent(this, UserListActivity::class.java)
            .putExtra("userId", uid).putExtra("kind", kind))
    }

    // ── 게시물(러닝 기록) 그리드 ─────────────────────────────
    private fun loadPosts() {
        val uid = Session(this).userId
        if (!ApiConfig.enabled || uid == null) return
        ApiClient(Session(this)).listMyRuns(uid) { r ->
            runOnUiThread {
                r.onSuccess { allRuns = it; renderPosts(it) }
                    .onFailure {
                        // 무음 빈 상태 방지 — 실패를 명확히 안내
                        if (allRuns.length() == 0) {
                            val empty = findViewById<TextView>(R.id.postsEmpty)
                            empty.text = "기록을 불러오지 못했어요.\n연결을 확인하고 다시 들어와주세요."
                            empty.visibility = View.VISIBLE
                        }
                    }
            }
        }
    }

    /** 현재 탭에 맞춰 그리드 렌더 — 게시물=공개만, 러닝기록=전체 */
    private fun renderPosts(arr: JSONArray) {
        val grid = findViewById<LinearLayout>(R.id.postsGrid)
        grid.removeAllViews()
        // 탭 필터
        val shown = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (postsMode == "records" || o.optString("visibility", "private") == "public") shown.add(o)
        }
        val empty = findViewById<TextView>(R.id.postsEmpty)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (postsMode == "posts")
            "아직 올린 게시물이 없어요.\n위 버튼으로 러닝 기록을 게시해보세요!"
        else "아직 기록한 러닝이 없어요.\n러닝 탭에서 첫 기록을 남겨보세요!"

        var row: LinearLayout? = null
        for (i in shown.indices) {
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row)
            }
            val cell = layoutInflater.inflate(R.layout.cell_post, row, false)
            bindPost(cell, shown[i])
            row!!.addView(cell)
        }
        if (shown.size % 2 == 1) row?.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
    }

    /** 러닝 기록에서 비공개 러닝을 골라 게시물로 올리기(공개 전환 + 캡션) */
    private fun showRegisterPicker() {
        val privates = ArrayList<JSONObject>()
        for (i in 0 until allRuns.length()) {
            val o = allRuns.getJSONObject(i)
            if (o.optString("visibility", "private") != "public") privates.add(o)
        }
        if (privates.isEmpty()) {
            Toast.makeText(this, "올릴 수 있는 비공개 러닝이 없어요. 모두 게시됐어요!", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = privates.map { o ->
            val d = runCatching { iso.parse(o.optString("started_at").take(19)) }.getOrNull()
            "${d?.let { labelFmt.format(it) } ?: "-"} · ${TrackActivity.formatKm(o.optDouble("distance_m"))}"
        }.toTypedArray()
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("게시할 러닝 선택")
            .setItems(labels) { _, i -> confirmPublish(privates[i]) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmPublish(o: JSONObject) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val cap = EditText(this).apply { hint = "한마디 (선택) — 예: 한강 노을 보며 🏃" }
        box.addView(cap)
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("피드에 게시할까요?")
            .setMessage("${TrackActivity.formatKm(o.optDouble("distance_m"))} 러닝을 공개합니다.")
            .setView(box)
            .setPositiveButton("게시") { _, _ ->
                val rid = o.optString("id")
                val caption = cap.text.toString().trim()
                val api = ApiClient(Session(this))
                api.setRunVisibility(rid, "public") { r ->
                    runOnUiThread {
                        r.onSuccess {
                            if (caption.isNotEmpty()) api.patchRun(rid, JSONObject().put("caption", caption))
                            Toast.makeText(this, "게시했어요! 피드에서 볼 수 있어요 🎉", Toast.LENGTH_SHORT).show()
                            postsMode = "posts"; styleTabs(); loadPosts()
                        }.onFailure { Toast.makeText(this, "게시 실패", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun bindPost(cell: View, o: JSONObject) {
        val started = runCatching { iso.parse(o.optString("started_at").take(19)) }.getOrNull()
        cell.findViewById<TextView>(R.id.postDistance).text =
            String.format(Locale.US, "%.2f km", o.optDouble("distance_m") / 1000.0)
        cell.findViewById<TextView>(R.id.postDate).text = started?.let { dateFmt.format(it) } ?: ""
        cell.findViewById<TextView>(R.id.postBadge).text =
            if (o.optString("visibility", "private") == "public") "공개" else "비공개"
        cell.findViewById<RoutePreviewView>(R.id.postRoute).setRoute(parseRoute(o.optJSONObject("geom")))
        cell.setOnClickListener { openRunDetail(o) }
    }

    private fun parseRoute(geom: JSONObject?): List<DoubleArray> {
        val coords = geom?.optJSONArray("coordinates") ?: return emptyList()
        return runCatching {
            (0 until coords.length()).map {
                val c = coords.getJSONArray(it); doubleArrayOf(c.getDouble(0), c.getDouble(1))
            }
        }.getOrDefault(emptyList())
    }

    private fun openRunDetail(o: JSONObject) {
        val geom = o.optJSONObject("geom") ?: run {
            Toast.makeText(this, "이 기록엔 경로 데이터가 없어요", Toast.LENGTH_SHORT).show(); return
        }
        val tagsArr = o.optJSONArray("tags")
        val tags = if (tagsArr == null || tagsArr.length() == 0) ""
            else (0 until tagsArr.length()).joinToString(" ") { "#" + tagsArr.optString(it) }
        startActivity(
            Intent(this, TrackViewActivity::class.java)
                .putExtra("serverGeoJson", geom.toString())
                .putExtra("distanceM", o.optDouble("distance_m"))
                .putExtra("durationMs", o.optLong("duration_ms"))
                .putExtra("caption", o.optString("caption").takeIf { it != "null" } ?: "")
                .putExtra("tags", tags)
                .putExtra("visibility", o.optString("visibility", "private"))
                .putExtra("startedAtIso", o.optString("started_at"))
        )
    }

    // ── ☰ 메뉴 (바텀시트) ─────────────────────────────────
    private fun showMenu() {
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_bg)
            setPadding(px(10), px(14), px(10), px(18))
        }
        box.addView(TextView(this).apply {
            text = "메뉴"; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.textDark)); setPadding(px(14), 0, 0, px(8))
        })
        val dialog = android.app.Dialog(this, R.style.RundexDialog)
        val entries = listOf(
            Triple("🏅", "칭호 보관함", TitleActivity::class.java),
            Triple("📜", "러닝 기록", RunHistoryActivity::class.java),
            Triple("🔖", "코스 보관함", BookmarksActivity::class.java),
            Triple("📣", "피드", FeedActivity::class.java),
            Triple("👥", "친구 찾기", UsersActivity::class.java),
            Triple("🤝", "크루", CrewActivity::class.java),
            Triple("⚙️", "설정", SettingsActivity::class.java),
        )
        for ((emoji, label, cls) in entries) {
            box.addView(TextView(this).apply {
                text = "$emoji   $label"; textSize = 15f
                setTextColor(getColor(R.color.textDark))
                setPadding(px(14), px(14), px(14), px(14))
                setOnClickListener { dialog.dismiss(); startActivity(Intent(this@ProfileActivity, cls)) }
            })
        }
        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun editName() {
        val view = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val input = view.findViewById<EditText>(R.id.nameInput)
        val handleInput = view.findViewById<EditText>(R.id.handleInput)
        val bioInput = view.findViewById<EditText>(R.id.bioInput)
        input.setText(getSharedPreferences("profile", MODE_PRIVATE).getString("name", "러너"))
        input.setSelection(input.text.length)

        val session = Session(this)
        session.userId?.let { uid ->
            ApiClient(session).getProfile(uid) { r ->
                r.onSuccess { arr ->
                    val o = arr.optJSONObject(0) ?: return@onSuccess
                    val h = o.optString("handle")
                    val bio = o.optString("bio")
                    runOnUiThread {
                        if (!h.isNullOrBlank() && h != "null") handleInput.setText(h)
                        if (bio.isNotBlank() && bio != "null") bioInput.setText(bio)
                    }
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
            val handle = handleInput.text.toString().trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
            val bio = bioInput.text.toString().trim().take(80)
            getSharedPreferences("profile", MODE_PRIVATE).edit().putString("name", name).apply()
            refresh()
            session.userId?.let { uid ->
                val body = JSONObject().put("display_name", name).put("bio", bio)
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

    /** 보유 칭호 중 프로필 대표로 노출할 칭호 선택 */
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
                getSharedPreferences("profile", MODE_PRIVATE).edit().putString("rep_title_id", id).apply()
                refresh()
                Session(this).userId?.let { uid ->
                    ApiClient(Session(this)).patchMe(uid,
                        JSONObject().put("rep_title_ids", JSONArray(listOf(id))))
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
