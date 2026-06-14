package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView

/** 홈 피드 — 사람들이 공개한 러닝 기록을 최신순으로(인스타형 카드). 서버(Supabase) 연동. */
class FeedActivity : Activity() {

    private val items = ArrayList<FeedItem>()
    private lateinit var adapter: FeedAdapter
    private var followingMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)
        NavBar.setup(this, R.id.navFeed)

        adapter = FeedAdapter(this, items, { pos -> toggleLike(pos) }, { pos -> openRunDetail(pos) }, { pos -> openProfile(pos) })
        findViewById<ListView>(R.id.feedList).adapter = adapter
        findViewById<TextView>(R.id.feedTabAll).setOnClickListener { switchTab(false) }
        findViewById<TextView>(R.id.feedTabFollowing).setOnClickListener { switchTab(true) }
        findViewById<TextView>(R.id.feedNotif).setOnClickListener { showNotifications() }
        findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.feedRefresh).apply {
            setColorSchemeColors(getColor(R.color.primary))
            setOnRefreshListener { load() }
        }
        styleTabs()
    }

    private fun switchTab(following: Boolean) {
        if (followingMode == following) return
        followingMode = following
        styleTabs()
        load()
    }

    private fun styleTabs() {
        val all = findViewById<TextView>(R.id.feedTabAll)
        val fol = findViewById<TextView>(R.id.feedTabFollowing)
        for ((tv, on) in listOf(all to !followingMode, fol to followingMode)) {
            if (on) { tv.setBackgroundResource(R.drawable.seg_active_bg); tv.setTextColor(android.graphics.Color.WHITE) }
            else { tv.background = null; tv.setTextColor(getColor(R.color.textGrey)) }
        }
    }

    /** 피드 카드 탭 → 그 런의 전체 경로·기록 상세 */
    private fun openRunDetail(pos: Int) {
        if (pos >= items.size) return
        val it = items[pos]
        if (it.route.size < 2) {
            android.widget.Toast.makeText(this, "이 게시물엔 경로가 없어요", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val coords = org.json.JSONArray()
        it.route.forEach { p -> coords.put(org.json.JSONArray().put(p[0]).put(p[1])) }
        val geojson = org.json.JSONObject().put("type", "LineString").put("coordinates", coords).toString()
        startActivity(
            android.content.Intent(this, TrackViewActivity::class.java)
                .putExtra("serverGeoJson", geojson)
                .putExtra("distanceM", it.distanceM)
                .putExtra("durationMs", it.durationMs)
                .putExtra("caption", it.caption)
        )
    }

    /** 피드 작성자 탭 → 공개 프로필 */
    private fun openProfile(pos: Int) {
        if (pos >= items.size) return
        val it = items[pos]
        if (it.userId.isBlank()) return
        startActivity(
            android.content.Intent(this, PublicProfileActivity::class.java)
                .putExtra("userId", it.userId)
                .putExtra("name", it.name)
        )
    }

    /** 좋아요 토글 — 낙관적 갱신 후 서버 반영 */
    private fun toggleLike(pos: Int) {
        if (pos >= items.size) return
        val session = Session(this)
        val uid = session.userId ?: return
        val item = items[pos]
        val nowLiked = !item.likedByMe
        item.likedByMe = nowLiked
        item.likeCount = (item.likeCount + if (nowLiked) 1 else -1).coerceAtLeast(0)
        adapter.notifyDataSetChanged()
        val api = ApiClient(session)
        if (nowLiked) api.likeRun(item.runId, uid) else api.unlikeRun(item.runId, uid)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val empty = findViewById<TextView>(R.id.feedEmpty)
        if (!ApiConfig.enabled) {
            empty.text = "서버 연결 전입니다.\n러닝을 공개하면 여기에 올라옵니다."
            empty.visibility = View.VISIBLE
            return
        }
        val api = ApiClient(Session(this))
        if (followingMode) {
            val me = Session(this).userId
            if (me == null) { runOnUiThread { render(org.json.JSONArray()) }; return }
            api.listFollowing(me) { fr ->
                val ids = fr.getOrNull()?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("followee_id") } } ?: emptyList()
                api.getFeedByUsers(ids) { r -> runOnUiThread { r.onSuccess { render(it) }.onFailure { showError() } } }
            }
        } else {
            api.getPublicFeed { r -> runOnUiThread { r.onSuccess { render(it) }.onFailure { showError() } } }
        }
    }

    private fun render(arr: org.json.JSONArray) {
        findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.feedRefresh).isRefreshing = false
        items.clear()
        for (i in 0 until arr.length()) items.add(FeedAdapter.fromJson(arr.getJSONObject(i)))
        adapter.notifyDataSetChanged()
        val empty = findViewById<TextView>(R.id.feedEmpty)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (followingMode) "팔로우한 사람의 공개 러닝이 없어요.\n친구를 팔로우해보세요!"
        else "아직 공개된 러닝이 없어요.\n첫 기록을 공개해보세요!"
    }

    /** 🔔 알림 — 내 러닝 좋아요 / 나를 팔로우한 사람 (최신순) */
    private fun showNotifications() {
        if (!ApiConfig.enabled) {
            android.widget.Toast.makeText(this, "서버 연결 전입니다", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient(Session(this)).getNotifications { r ->
            runOnUiThread {
                r.onSuccess { arr -> renderNotifications(arr) }
                    .onFailure { android.widget.Toast.makeText(this, "알림을 불러올 수 없어요", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun renderNotifications(arr: org.json.JSONArray) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        if (arr.length() == 0) {
            box.addView(TextView(this).apply {
                text = "아직 새로운 알림이 없어요.\n러닝을 공개하면 좋아요·팔로우 소식이 여기에 모여요 🔔"
                setTextColor(getColor(R.color.textGrey)); textSize = 14f
                setPadding(0, pad / 2, 0, pad / 2)
            })
        } else {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "러너"
                val line = when (o.optString("type")) {
                    "like" -> "❤️  $name 님이 회원님의 러닝을 좋아합니다"
                    "follow" -> "👤  $name 님이 회원님을 팔로우했습니다"
                    else -> "🔔  $name"
                }
                box.addView(TextView(this).apply {
                    text = line + "\n" + relTime(o.optString("at"))
                    setTextColor(getColor(R.color.textDark)); textSize = 14f
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, pad / 3, 0, pad / 3)
                })
                if (i < arr.length() - 1) box.addView(View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0x14000000)
                })
            }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        android.app.AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("🔔 알림")
            .setView(scroll)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun relTime(iso: String): String {
        if (iso.isBlank() || iso == "null") return ""
        val t = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(iso.take(19))?.time
        }.getOrNull() ?: return ""
        val diff = System.currentTimeMillis() - t
        return when {
            diff < 60_000 -> "방금 전"
            diff < 3_600_000 -> "${diff / 60_000}분 전"
            diff < 86_400_000 -> "${diff / 3_600_000}시간 전"
            diff < 604_800_000 -> "${diff / 86_400_000}일 전"
            else -> java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(t))
        }
    }

    private fun showError() {
        findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.feedRefresh).isRefreshing = false
        val empty = findViewById<TextView>(R.id.feedEmpty)
        empty.text = "피드를 불러올 수 없어요.\n잠시 후 다시 시도해주세요."
        empty.visibility = View.VISIBLE
    }
}
