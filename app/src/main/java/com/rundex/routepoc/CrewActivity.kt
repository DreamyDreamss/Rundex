package com.rundex.routepoc

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast

/** 크루 — 내 크루 목록, 생성, 코드 가입, 멤버 보기. 서버(Supabase) 연동. */
class CrewActivity : Activity() {

    private val rows = ArrayList<SocialRow>()
    private val crewIds = ArrayList<String>()
    private lateinit var adapter: SocialRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew)

        adapter = SocialRowAdapter(this, rows)
        val list = findViewById<ListView>(R.id.crewList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> showMembers(pos) }

        findViewById<Button>(R.id.createCrewButton).setOnClickListener { createCrew() }
        findViewById<Button>(R.id.joinCrewButton).setOnClickListener { joinCrew() }

        if (!ApiConfig.enabled) {
            findViewById<android.widget.TextView>(R.id.crewHeader).text = "👥 내 크루\n(서버 연결 전)"
        }
        loadMyCrews()
    }

    private fun loadMyCrews() {
        val me = Session(this).userId
        if (me == null) {
            rows.clear()
            rows.add(SocialRow("👥", "아직 가입한 크루가 없습니다", "크루를 만들거나 코드로 가입해보세요"))
            adapter.notifyDataSetChanged()
            return
        }
        ApiClient(Session(this)).listMyCrews(me) { result ->
            result.onSuccess { arr ->
                runOnUiThread {
                    rows.clear(); crewIds.clear()
                    for (i in 0 until arr.length()) {
                        val crew = arr.getJSONObject(i).optJSONObject("crews") ?: continue
                        crewIds.add(crew.optString("id"))
                        rows.add(
                            SocialRow(
                                "👥", crew.optString("name"),
                                "가입 코드 ${crew.optString("join_code")}", "멤버 ›"
                            )
                        )
                    }
                    if (rows.isEmpty()) rows.add(SocialRow("👥", "아직 가입한 크루가 없습니다", "크루를 만들거나 코드로 가입해보세요"))
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun createCrew() {
        if (!ApiConfig.enabled) { needServer(); return }
        val input = EditText(this).apply { hint = "크루 이름" }
        AlertDialog.Builder(this)
            .setTitle("크루 만들기")
            .setView(input)
            .setPositiveButton("만들기") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                ApiClient(Session(this)).createCrew(name) { result ->
                    runOnUiThread {
                        result.onSuccess {
                            Toast.makeText(this, "크루를 만들었어요 (코드 ${it.optString("join_code")})", Toast.LENGTH_LONG).show()
                            loadMyCrews()
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
        AlertDialog.Builder(this)
            .setTitle("코드로 가입")
            .setView(input)
            .setPositiveButton("가입") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isEmpty()) return@setPositiveButton
                ApiClient(Session(this)).joinCrew(code) { result ->
                    runOnUiThread {
                        result.onSuccess {
                            Toast.makeText(this, "${it.optString("name")} 크루에 가입했어요", Toast.LENGTH_SHORT).show()
                            loadMyCrews()
                        }.onFailure { Toast.makeText(this, "가입 실패: 코드를 확인하세요", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showMembers(pos: Int) {
        if (pos >= crewIds.size) return
        ApiClient(Session(this)).getCrewMembers(crewIds[pos]) { result ->
            result.onSuccess { arr ->
                val names = (0 until arr.length()).mapNotNull { i ->
                    val p = arr.getJSONObject(i).optJSONObject("profiles") ?: return@mapNotNull null
                    val role = arr.getJSONObject(i).optString("role")
                    "${p.optString("display_name", "러너")}${if (role == "owner") " 👑" else ""}"
                }
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("크루 멤버 ${names.size}명")
                        .setItems(names.toTypedArray(), null)
                        .setPositiveButton("닫기", null)
                        .show()
                }
            }
        }
    }

    private fun needServer() =
        Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show()
}
