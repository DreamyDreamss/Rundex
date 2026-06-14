package com.rundex.routepoc

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 인스타형 피드 카드 1개 (공개 런) */
data class FeedItem(
    val runId: String,
    val userId: String,
    val name: String,
    val whenText: String,
    val chip: String,
    val distanceText: String,
    val durationText: String,
    val regionsText: String,
    val route: List<DoubleArray>,   // [lon,lat] 목록
    val caption: String,
    val tags: String,               // "#한강 #야간런"
    var likeCount: Int,
    var likedByMe: Boolean,
    val commentCount: Int,
    val distanceM: Double,
    val durationMs: Long,
)

class FeedAdapter(
    private val activity: Activity,
    private val items: List<FeedItem>,
    private val onToggleLike: (Int) -> Unit = {},
    private val onOpen: (Int) -> Unit = {},
    private val onOpenProfile: (Int) -> Unit = {},
    private val onOpenComments: (Int) -> Unit = {},
) : ArrayAdapter<FeedItem>(activity, R.layout.row_feed, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: activity.layoutInflater.inflate(R.layout.row_feed, parent, false)
        val it = items[position]
        // 카드(경로 영역 등) 탭 → 상세 보기. 좋아요 버튼은 자체 리스너로 소비.
        v.findViewById<View>(R.id.feedRoute).setOnClickListener { onOpen(position) }
        // 작성자(아바타·이름) 탭 → 공개 프로필
        v.findViewById<View>(R.id.feedAvatar).setOnClickListener { onOpenProfile(position) }
        v.findViewById<View>(R.id.feedName).setOnClickListener { onOpenProfile(position) }
        v.findViewById<TextView>(R.id.feedLike).apply {
            text = (if (it.likedByMe) "❤️ " else "🤍 ") + it.likeCount
            setOnClickListener { onToggleLike(position) }
        }
        v.findViewById<TextView>(R.id.feedComment).apply {
            text = "💬 ${it.commentCount}"
            setOnClickListener { onOpenComments(position) }
        }
        v.findViewById<TextView>(R.id.feedName).text = it.name
        v.findViewById<TextView>(R.id.feedWhen).text = it.whenText
        v.findViewById<TextView>(R.id.feedChip).text = it.chip
        v.findViewById<TextView>(R.id.feedDistance).text = it.distanceText
        v.findViewById<TextView>(R.id.feedDuration).text = it.durationText
        v.findViewById<TextView>(R.id.feedRegions).text = it.regionsText
        v.findViewById<RoutePreviewView>(R.id.feedRoute).setRoute(it.route)
        v.findViewById<TextView>(R.id.feedCaption).apply {
            text = it.caption
            visibility = if (it.caption.isBlank()) View.GONE else View.VISIBLE
        }
        v.findViewById<TextView>(R.id.feedTags).apply {
            text = it.tags
            visibility = if (it.tags.isBlank()) View.GONE else View.VISIBLE
        }
        return v
    }

    companion object {
        private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        private val out = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)

        /** public_feed 한 행 → FeedItem */
        fun fromJson(o: JSONObject): FeedItem {
            val km = o.optDouble("distance_m") / 1000.0
            val durMs = o.optLong("duration_ms")
            val regions = o.optInt("region_count")
            return FeedItem(
                name = o.optString("display_name").ifBlank { "러너" },
                whenText = relativeWhen(o.optString("started_at")),
                chip = "🏃 러닝",
                distanceText = String.format(Locale.US, "%.2f km", km),
                durationText = formatDuration(durMs),
                regionsText = "${regions}곳",
                route = parseLineString(o.optString("geojson")),
                caption = o.optString("caption").takeIf { it != "null" } ?: "",
                tags = parseTags(o.optJSONArray("tags")),
                runId = o.optString("run_id"),
                userId = o.optString("user_id"),
                likeCount = o.optInt("like_count"),
                likedByMe = o.optBoolean("liked_by_me"),
                commentCount = o.optInt("comment_count"),
                distanceM = o.optDouble("distance_m"),
                durationMs = o.optLong("duration_ms"),
            )
        }

        private fun parseTags(arr: org.json.JSONArray?): String {
            if (arr == null || arr.length() == 0) return ""
            return (0 until arr.length()).joinToString(" ") { "#" + arr.optString(it) }
        }

        private fun parseLineString(geojson: String): List<DoubleArray> {
            if (geojson.isBlank()) return emptyList()
            return runCatching {
                val coords = JSONObject(geojson).getJSONArray("coordinates")
                (0 until coords.length()).map { i ->
                    val c = coords.getJSONArray(i)
                    doubleArrayOf(c.getDouble(0), c.getDouble(1))
                }
            }.getOrDefault(emptyList())
        }

        private fun relativeWhen(iso8601: String): String {
            val d: Date = runCatching { iso.parse(iso8601.take(19)) }.getOrNull() ?: return ""
            return out.format(d)
        }

        private fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
