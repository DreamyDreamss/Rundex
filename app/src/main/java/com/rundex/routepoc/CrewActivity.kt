package com.rundex.routepoc

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** 크루 — 내 크루 + 추천 크루 카드, 생성, 코드 가입. 서버(Supabase) 연동. */
class CrewActivity : Activity() {

    private sealed class CRow
    private class CSection(val title: String, val count: Int) : CRow()
    private class CCrew(val id: String, val name: String, val sub: String, val joined: Boolean) : CRow()
    private class CEmpty(val text: String) : CRow()

    private val rows = ArrayList<CRow>()
    private lateinit var adapter: CrewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew)

        adapter = CrewAdapter()
        findViewById<ListView>(R.id.crewList).adapter = adapter

        findViewById<Button>(R.id.createCrewButton).setOnClickListener { createCrew() }
        findViewById<Button>(R.id.joinCrewButton).setOnClickListener { joinCrew() }

        if (!ApiConfig.enabled) {
            findViewById<TextView>(R.id.crewHeader).text = "👥 크루 (서버 연결 전)"
        }
    }

    override fun onResume() {
        super.onResume()
        loadCrews()
    }

    /** 내 크루 + 추천(공개) 크루를 카드 목록으로 구성 */
    private fun loadCrews() {
        val me = Session(this).userId ?: return
        ApiClient(Session(this)).listMyCrews(me) { mine ->
            ApiClient(Session(this)).listPublicCrews { pub ->
                runOnUiThread {
                    rows.clear()
                    val joinedIds = HashSet<String>()
                    val mineList = ArrayList<CCrew>()
                    mine.getOrNull()?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val c = arr.getJSONObject(i).optJSONObject("crews") ?: continue
                            joinedIds.add(c.optString("id"))
                            mineList.add(CCrew(c.optString("id"), c.optString("name"),
                                "가입 코드 ${c.optString("join_code")}", true))
                        }
                    }
                    rows.add(CSection("내 크루", mineList.size))
                    if (mineList.isEmpty()) rows.add(CEmpty("아직 가입한 크루가 없어요. 추천 크루에 가입해보세요!"))
                    else rows.addAll(mineList)

                    val recoList = ArrayList<CCrew>()
                    pub.getOrNull()?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val c = arr.getJSONObject(i)
                            val id = c.optString("id")
                            if (joinedIds.contains(id)) continue
                            val desc = c.optString("description").takeIf { it != "null" && it.isNotBlank() } ?: "러닝 크루"
                            recoList.add(CCrew(id, c.optString("name"),
                                "멤버 ${c.optInt("member_count")}명 · $desc", false))
                        }
                    }
                    rows.add(CSection("추천 크루", recoList.size))
                    if (recoList.isEmpty()) rows.add(CEmpty("추천할 공개 크루가 아직 없어요."))
                    else rows.addAll(recoList)

                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun openCrew(id: String) {
        if (id.isEmpty()) return
        startActivity(android.content.Intent(this, CrewDetailActivity::class.java).putExtra("crewId", id))
    }

    private inner class CrewAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 3
        override fun getItemViewType(p: Int) = when (rows[p]) {
            is CSection -> 0; is CCrew -> 1; else -> 2
        }
        override fun isEnabled(p: Int) = rows[p] is CCrew

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return when (val row = rows[position]) {
                is CSection -> {
                    val v = convertView ?: layoutInflater.inflate(R.layout.row_title_section, parent, false)
                    v.findViewById<TextView>(R.id.sectionTitle).text =
                        if (row.title == "내 크루") "👟 ${row.title}" else "✨ ${row.title}"
                    v.findViewById<TextView>(R.id.sectionCount).text = "${row.count}"
                    v
                }
                is CEmpty -> {
                    val v = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                    (v as TextView).apply {
                        text = row.text; setTextColor(getColor(R.color.textGrey)); textSize = 13f
                        setPadding(56, 8, 56, 24)
                    }
                    v
                }
                is CCrew -> {
                    val v = convertView ?: layoutInflater.inflate(R.layout.row_crew, parent, false)
                    v.findViewById<TextView>(R.id.crewIcon).text = if (row.joined) "👟" else "🏃"
                    v.findViewById<TextView>(R.id.crewName).text = row.name
                    v.findViewById<TextView>(R.id.crewSub).text = row.sub
                    v.findViewById<TextView>(R.id.crewAction).text = if (row.joined) "입장 ›" else "가입 ›"
                    v.setOnClickListener { openCrew(row.id) }
                    v
                }
            }
        }
    }

    private fun createCrew() {
        if (!ApiConfig.enabled) { needServer(); return }
        val input = EditText(this).apply { hint = "크루 이름" }
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("크루 만들기")
            .setView(input)
            .setPositiveButton("만들기") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                ApiClient(Session(this)).createCrew(name) { result ->
                    runOnUiThread {
                        result.onSuccess {
                            Toast.makeText(this, "크루를 만들었어요 (코드 ${it.optString("join_code")})", Toast.LENGTH_LONG).show()
                            loadCrews()
                        }.onFailure { Toast.makeText(this, "생성 실패: ${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun joinCrew() {
        if (!ApiConfig.enabled) { needServer(); return }
        val input = EditText(this).apply { hint = "가입 코드 (6자리)" }
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("코드로 가입")
            .setView(input)
            .setPositiveButton("가입") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isEmpty()) return@setPositiveButton
                ApiClient(Session(this)).joinCrew(code) { result ->
                    runOnUiThread {
                        result.onSuccess {
                            Toast.makeText(this, "${it.optString("name")} 크루에 가입했어요", Toast.LENGTH_SHORT).show()
                            loadCrews()
                        }.onFailure { Toast.makeText(this, "가입 실패: 코드를 확인하세요", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun needServer() =
        Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show()
}
