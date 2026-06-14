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
        list.setOnItemClickListener { _, _, pos, _ -> openCrew(pos) }

        findViewById<Button>(R.id.createCrewButton).setOnClickListener { createCrew() }
        findViewById<Button>(R.id.joinCrewButton).setOnClickListener { joinCrew() }

        if (!ApiConfig.enabled) {
            findViewById<android.widget.TextView>(R.id.crewHeader).text = "👥 크루\n(서버 연결 전)"
        }
    }

    override fun onResume() {
        super.onResume()
        loadCrews()
    }

    /** 내 크루 + 추천(공개) 크루를 한 목록으로 구성 */
    private fun loadCrews() {
        val me = Session(this).userId ?: return
        ApiClient(Session(this)).listMyCrews(me) { mine ->
            ApiClient(Session(this)).listPublicCrews { pub ->
                runOnUiThread {
                    rows.clear(); crewIds.clear()
                    val joinedIds = HashSet<String>()

                    rows.add(SocialRow("👥", "내 크루")); crewIds.add("")
                    mine.getOrNull()?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val c = arr.getJSONObject(i).optJSONObject("crews") ?: continue
                            joinedIds.add(c.optString("id"))
                            crewIds.add(c.optString("id"))
                            rows.add(SocialRow("👟", c.optString("name"), "가입 코드 ${c.optString("join_code")}", "›"))
                        }
                    }
                    if (crewIds.count { it.isNotEmpty() } == 0) {
                        rows.add(SocialRow("•", "아직 가입한 크루가 없어요", "아래 추천 크루에 가입해보세요")); crewIds.add("")
                    }

                    rows.add(SocialRow("✨", "추천 크루")); crewIds.add("")
                    pub.getOrNull()?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val c = arr.getJSONObject(i)
                            val id = c.optString("id")
                            if (joinedIds.contains(id)) continue
                            crewIds.add(id)
                            val desc = c.optString("description").takeIf { it != "null" }?.takeIf { it.isNotBlank() } ?: "러닝 크루"
                            rows.add(SocialRow("🏃", c.optString("name"), "멤버 ${c.optInt("member_count")}명 · $desc", "가입 ›"))
                        }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun openCrew(pos: Int) {
        val id = crewIds.getOrNull(pos).orEmpty()
        if (id.isEmpty()) return
        startActivity(android.content.Intent(this, CrewDetailActivity::class.java).putExtra("crewId", id))
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
                    AlertDialog.Builder(this, R.style.RundexDialog)
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
