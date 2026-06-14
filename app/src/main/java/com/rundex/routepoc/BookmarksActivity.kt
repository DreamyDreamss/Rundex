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
import org.json.JSONObject
import java.util.Locale

/** 코스 보관함 — 북마크한 추천 코스를 카드로. 경로 미리보기 + 따라 달리기 + 삭제. */
class BookmarksActivity : Activity() {

    private val items = ArrayList<JSONObject>()   // routes 객체
    private lateinit var adapter: BmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        findViewById<TextView>(R.id.bmBack).setOnClickListener { finish() }
        adapter = BmAdapter()
        findViewById<ListView>(R.id.bmList).adapter = adapter
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        val session = Session(this)
        val uid = session.userId
        if (!ApiConfig.enabled || uid == null) { showEmpty("서버 연결 후 사용할 수 있어요"); return }
        ApiClient(session).listMyBookmarks(uid) { r ->
            runOnUiThread {
                r.onSuccess { arr ->
                    items.clear()
                    for (i in 0 until arr.length()) {
                        arr.getJSONObject(i).optJSONObject("routes")?.let { items.add(it) }
                    }
                    adapter.notifyDataSetChanged()
                    findViewById<TextView>(R.id.bmCount).text = "${items.size}개"
                    val empty = findViewById<TextView>(R.id.bmEmpty)
                    empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    if (items.isEmpty()) empty.text = "북마크한 코스가 없어요.\n러닝 → 추천 코스에서 저장해보세요!"
                }.onFailure { showEmpty("코스를 불러올 수 없어요") }
            }
        }
    }

    private fun showEmpty(msg: String) {
        items.clear(); adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.bmEmpty).apply { text = msg; visibility = View.VISIBLE }
    }

    private fun parseRoute(geom: JSONObject?): List<DoubleArray> {
        val coords = geom?.optJSONArray("coordinates") ?: return emptyList()
        return runCatching {
            (0 until coords.length()).map {
                val c = coords.getJSONArray(it); doubleArrayOf(c.getDouble(0), c.getDouble(1))
            }
        }.getOrDefault(emptyList())
    }

    private fun runCourse(o: JSONObject) {
        val geom = o.optJSONObject("geom") ?: run {
            Toast.makeText(this, "이 코스엔 경로 데이터가 없어요", Toast.LENGTH_SHORT).show(); return
        }
        startActivity(
            Intent(this, TrackViewActivity::class.java)
                .putExtra("serverGeoJson", geom.toString())
                .putExtra("distanceM", o.optDouble("distance_m"))
        )
    }

    private fun remove(o: JSONObject) {
        val uid = Session(this).userId ?: return
        ApiClient(Session(this)).removeBookmark(uid, o.optString("id")) { r ->
            runOnUiThread {
                r.onSuccess { Toast.makeText(this, "북마크에서 삭제했어요", Toast.LENGTH_SHORT).show(); load() }
                    .onFailure { Toast.makeText(this, "삭제 실패", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private inner class BmAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.row_bookmark, parent, false)
            val o = items[position]
            v.findViewById<RoutePreviewView>(R.id.bmRoute).setRoute(parseRoute(o.optJSONObject("geom")))
            v.findViewById<TextView>(R.id.bmName).text = o.optString("name")
            val km = String.format(Locale.US, "%.1f", o.optDouble("distance_m") / 1000.0)
            v.findViewById<TextView>(R.id.bmSub).text =
                "${km}km · 난이도 ${"★".repeat(o.optInt("difficulty", 1).coerceIn(1, 5))}"
            v.findViewById<TextView>(R.id.bmRun).setOnClickListener { runCourse(o) }
            v.findViewById<RoutePreviewView>(R.id.bmRoute).setOnClickListener { runCourse(o) }
            v.findViewById<TextView>(R.id.bmRemove).setOnClickListener { remove(o) }
            return v
        }
    }
}
