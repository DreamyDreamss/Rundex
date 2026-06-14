package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

/** 다른 러너의 공개 프로필 — 통계 + 공개 러닝 목록 + 팔로우. 피드/친구찾기에서 진입. */
class PublicProfileActivity : Activity() {

    private val items = ArrayList<FeedItem>()
    private lateinit var adapter: FeedAdapter
    private lateinit var userId: String
    private var iFollow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_public_profile)
        userId = intent.getStringExtra("userId") ?: run { finish(); return }

        findViewById<TextView>(R.id.ppBack).setOnClickListener { finish() }
        adapter = FeedAdapter(this, items, { pos -> toggleLike(pos) }, { pos -> openRun(pos) })
        findViewById<ListView>(R.id.ppRunList).adapter = adapter
        findViewById<TextView>(R.id.ppFollow).setOnClickListener { toggleFollow() }

        // 진입 시 이름 미리 채우기(있으면)
        intent.getStringExtra("name")?.let {
            findViewById<TextView>(R.id.ppTitle).text = it
            findViewById<TextView>(R.id.ppName).text = it
            findViewById<TextView>(R.id.ppAvatar).text = it.take(1)
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
        loadRuns()
    }

    private fun loadProfile() {
        if (!ApiConfig.enabled) return
        ApiClient(Session(this)).getPublicProfile(userId) { r ->
            runOnUiThread {
                r.onSuccess { o -> bindProfile(o) }
            }
        }
    }

    private fun bindProfile(o: JSONObject) {
        val name = o.optString("display_name").ifBlank { "러너" }
        findViewById<TextView>(R.id.ppTitle).text = name
        findViewById<TextView>(R.id.ppName).text = name
        findViewById<TextView>(R.id.ppAvatar).text = name.take(1)
        val handle = o.optString("handle").takeIf { it.isNotBlank() && it != "null" }
        findViewById<TextView>(R.id.ppHandle).text = handle?.let { "@$it" } ?: ""
        findViewById<TextView>(R.id.ppBio).apply {
            val bio = o.optString("bio").takeIf { it.isNotBlank() && it != "null" } ?: ""
            text = bio
            visibility = if (bio.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.ppDex).text = "${o.optInt("dex")}"
        findViewById<TextView>(R.id.ppRuns).text = "${o.optInt("runs")}"
        findViewById<TextView>(R.id.ppTitles).text = "${o.optInt("titles")}"
        findViewById<TextView>(R.id.ppFollowers).text = "${o.optInt("followers")}"
        iFollow = o.optBoolean("i_follow")
        styleFollow()
    }

    private fun styleFollow() {
        findViewById<TextView>(R.id.ppFollow).apply {
            if (userId == Session(this@PublicProfileActivity).userId) {
                visibility = View.GONE; return
            }
            text = if (iFollow) "팔로잉 ✓" else "＋ 팔로우"
            setBackgroundResource(if (iFollow) R.drawable.btn_secondary else R.drawable.btn_primary)
            setTextColor(getColor(if (iFollow) R.color.textDark else android.R.color.white))
        }
    }

    private fun toggleFollow() {
        val me = Session(this).userId ?: run {
            Toast.makeText(this, "로그인 후 팔로우할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        if (me == userId) return
        val api = ApiClient(Session(this))
        val fol = findViewById<TextView>(R.id.ppFollowers)
        val cur = fol.text.toString().toIntOrNull() ?: 0
        if (iFollow) {
            iFollow = false; fol.text = "${(cur - 1).coerceAtLeast(0)}"; styleFollow()
            api.unfollow(me, userId)
        } else {
            iFollow = true; fol.text = "${cur + 1}"; styleFollow()
            api.follow(me, userId) { r ->
                runOnUiThread { r.onFailure { iFollow = false; fol.text = "$cur"; styleFollow() } }
            }
        }
    }

    private fun loadRuns() {
        if (!ApiConfig.enabled) return
        ApiClient(Session(this)).getFeedByUsers(listOf(userId)) { r ->
            runOnUiThread {
                r.onSuccess { arr ->
                    items.clear()
                    for (i in 0 until arr.length()) items.add(FeedAdapter.fromJson(arr.getJSONObject(i)))
                    adapter.notifyDataSetChanged()
                    findViewById<TextView>(R.id.ppEmpty).visibility =
                        if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun toggleLike(pos: Int) {
        if (pos >= items.size) return
        val uid = Session(this).userId ?: return
        val item = items[pos]
        val nowLiked = !item.likedByMe
        item.likedByMe = nowLiked
        item.likeCount = (item.likeCount + if (nowLiked) 1 else -1).coerceAtLeast(0)
        adapter.notifyDataSetChanged()
        val api = ApiClient(Session(this))
        if (nowLiked) api.likeRun(item.runId, uid) else api.unlikeRun(item.runId, uid)
    }

    private fun openRun(pos: Int) {
        if (pos >= items.size) return
        val it = items[pos]
        if (it.route.size < 2) {
            Toast.makeText(this, "이 게시물엔 경로가 없어요", Toast.LENGTH_SHORT).show(); return
        }
        val coords = org.json.JSONArray()
        it.route.forEach { p -> coords.put(org.json.JSONArray().put(p[0]).put(p[1])) }
        val geojson = JSONObject().put("type", "LineString").put("coordinates", coords).toString()
        startActivity(
            android.content.Intent(this, TrackViewActivity::class.java)
                .putExtra("serverGeoJson", geojson)
                .putExtra("distanceM", it.distanceM)
                .putExtra("durationMs", it.durationMs)
                .putExtra("caption", it.caption)
                .putExtra("startedAtIso", it.whenText)
        )
    }
}
