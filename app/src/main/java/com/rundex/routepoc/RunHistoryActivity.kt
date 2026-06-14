package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 내 러닝 기록 — 카드형 상세 목록. 경로 미리보기·풀 스탯·공개전환·상세 진입. 서버(runs) 원천. */
class RunHistoryActivity : Activity() {

    private val items = ArrayList<JSONObject>()
    private lateinit var adapter: RunAdapter
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val dateFmt = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)
    private val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_history)
        findViewById<TextView>(R.id.runHistBack).setOnClickListener { finish() }
        adapter = RunAdapter()
        findViewById<ListView>(R.id.runHistList).adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val session = Session(this)
        val uid = session.userId
        if (!ApiConfig.enabled || uid == null) {
            showEmpty("서버 연결 후 기록을 볼 수 있어요")
            return
        }
        ApiClient(session).listMyRuns(uid) { r ->
            runOnUiThread {
                r.onSuccess { arr -> render(arr) }
                    .onFailure { showEmpty("기록을 불러올 수 없어요.\n잠시 후 다시 시도해주세요") }
            }
        }
    }

    private fun render(arr: JSONArray) {
        items.clear()
        for (i in 0 until arr.length()) items.add(arr.getJSONObject(i))
        adapter.notifyDataSetChanged()

        // 요약 스트립 집계
        var totalM = 0.0; var weekM = 0.0
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        for (o in items) {
            val d = o.optDouble("distance_m")
            totalM += d
            val started = runCatching { iso.parse(o.optString("started_at").take(19))?.time }.getOrNull() ?: 0L
            if (started >= weekAgo) weekM += d
        }
        findViewById<TextView>(R.id.runHistCount).text = "${items.size}개"
        findViewById<TextView>(R.id.sumRuns).text = "${items.size}"
        findViewById<TextView>(R.id.sumKm).text = String.format(Locale.US, "%.1f", totalM / 1000.0)
        findViewById<TextView>(R.id.sumWeek).text = String.format(Locale.US, "%.1f", weekM / 1000.0)

        val empty = findViewById<TextView>(R.id.runHistEmpty)
        if (items.isEmpty()) {
            empty.text = "아직 저장된 러닝이 없어요.\n러닝 탭에서 첫 기록을 남겨보세요!"
            empty.visibility = View.VISIBLE
        } else empty.visibility = View.GONE
    }

    private fun showEmpty(msg: String) {
        items.clear(); adapter.notifyDataSetChanged()
        val empty = findViewById<TextView>(R.id.runHistEmpty)
        empty.text = msg; empty.visibility = View.VISIBLE
    }

    private fun parseRoute(geom: JSONObject?): List<DoubleArray> {
        val coords = geom?.optJSONArray("coordinates") ?: return emptyList()
        return runCatching {
            (0 until coords.length()).map {
                val c = coords.getJSONArray(it)
                doubleArrayOf(c.getDouble(0), c.getDouble(1))
            }
        }.getOrDefault(emptyList())
    }

    private fun embeddedCount(o: JSONObject, key: String): Int =
        o.optJSONArray(key)?.optJSONObject(0)?.optInt("count") ?: 0

    private fun toggleVisibility(o: JSONObject) {
        val isPublic = o.optString("visibility", "private") == "public"
        val next = if (isPublic) "private" else "public"
        ApiClient(Session(this)).setRunVisibility(o.optString("id"), next) { r ->
            runOnUiThread {
                r.onSuccess {
                    Toast.makeText(this,
                        if (next == "public") "피드에 공개했어요" else "비공개로 전환했어요",
                        Toast.LENGTH_SHORT).show()
                    load()
                }.onFailure { Toast.makeText(this, "전환 실패", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun openDetail(o: JSONObject) {
        val geom = o.optJSONObject("geom")
        if (geom == null) {
            Toast.makeText(this, "이 기록엔 경로 데이터가 없어요", Toast.LENGTH_SHORT).show()
            return
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
                .putExtra("regionCount", embeddedCount(o, "run_region_ledger"))
                .putExtra("startedAtIso", o.optString("started_at"))
        )
    }

    private inner class RunAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.row_run, parent, false)
            val o = items[position]
            val started = runCatching { iso.parse(o.optString("started_at").take(19)) }.getOrNull()
            val isPublic = o.optString("visibility", "private") == "public"
            val distM = o.optDouble("distance_m")
            val durMs = o.optLong("duration_ms")

            v.findViewById<TextView>(R.id.runDate).text = started?.let { dateFmt.format(it) } ?: "-"
            v.findViewById<TextView>(R.id.runWhen).text =
                started?.let { "${timeFmt.format(it)} 시작" } ?: ""
            v.findViewById<TextView>(R.id.runBadge).apply {
                text = if (isPublic) "🔓 공개" else "🔒 비공개"
            }

            val route = parseRoute(o.optJSONObject("geom"))
            v.findViewById<RoutePreviewView>(R.id.runRoute).setRoute(route)

            val caption = o.optString("caption").takeIf { it.isNotBlank() && it != "null" } ?: ""
            v.findViewById<TextView>(R.id.runCaption).apply {
                text = caption
                visibility = if (caption.isBlank()) View.GONE else View.VISIBLE
            }
            val tagsArr = o.optJSONArray("tags")
            val tags = if (tagsArr == null || tagsArr.length() == 0) ""
                else (0 until tagsArr.length()).joinToString(" ") { "#" + tagsArr.optString(it) }
            v.findViewById<TextView>(R.id.runTags).apply {
                text = tags
                visibility = if (tags.isBlank()) View.GONE else View.VISIBLE
            }

            v.findViewById<TextView>(R.id.runStatKm).text = String.format(Locale.US, "%.2f", distM / 1000.0)
            v.findViewById<TextView>(R.id.runStatTime).text = TrackActivity.formatDuration(durMs)
            v.findViewById<TextView>(R.id.runStatPace).text = paceShort(distM, durMs)
            v.findViewById<TextView>(R.id.runStatRegions).text =
                "${embeddedCount(o, "run_region_ledger")}곳"

            val likes = embeddedCount(o, "run_reactions")
            v.findViewById<TextView>(R.id.runLikes).text = "❤️ $likes"

            v.findViewById<TextView>(R.id.runVisBtn).apply {
                text = if (isPublic) "🔒 비공개로" else "🔓 공개하기"
                setOnClickListener { toggleVisibility(o) }
            }
            v.findViewById<TextView>(R.id.runDetailBtn).setOnClickListener { openDetail(o) }
            v.findViewById<RoutePreviewView>(R.id.runRoute).setOnClickListener { openDetail(o) }
            return v
        }
    }

    /** 평균 페이스 m'ss" (50m 미만이면 -) */
    private fun paceShort(meters: Double, ms: Long): String {
        if (meters < 50.0) return "-'--\""
        val sec = (ms / 1000.0) / (meters / 1000.0)
        return String.format(Locale.US, "%d'%02d\"", (sec / 60).toInt(), (sec % 60).toInt())
    }
}
