package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

/** 크루 상세 — 멤버·챌린지·채팅. 가입 안 했으면 가입 버튼. */
class CrewDetailActivity : Activity() {

    private lateinit var crewId: String
    private var isMember = false
    private val chat = ArrayList<Pair<String, String>>()   // (이름, 메시지)
    private lateinit var chatAdapter: ArrayAdapter<Pair<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_detail)
        crewId = intent.getStringExtra("crewId") ?: run { finish(); return }

        chatAdapter = object : ArrayAdapter<Pair<String, String>>(this, R.layout.row_chat, chat) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.row_chat, parent, false)
                val (name, text) = getItem(position)!!
                v.findViewById<TextView>(R.id.chatName).text = name
                v.findViewById<TextView>(R.id.chatText).text = text
                return v
            }
        }
        findViewById<ListView>(R.id.crewChat).adapter = chatAdapter

        findViewById<TextView>(R.id.chatSend).setOnClickListener { sendMessage() }
        findViewById<TextView>(R.id.joinBar).setOnClickListener { join() }
        findViewById<TextView>(R.id.challengeSet).setOnClickListener { setChallengeDialog() }
    }

    private val poll = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (isMember) loadMessages()
            loadChallenge()
            poll.postDelayed(this, 6000)
        }
    }

    override fun onResume() {
        super.onResume()
        loadDetail(); loadChallenge(); loadMessages()
        poll.postDelayed(tick, 6000)   // 채팅·챌린지 6초마다 갱신(살아있는 대화)
    }

    override fun onPause() {
        super.onPause()
        poll.removeCallbacks(tick)
    }

    private fun api() = ApiClient(Session(this))

    private fun loadDetail() {
        api().crewDetail(crewId) { r ->
            r.onSuccess { o ->
                runOnUiThread {
                    findViewById<TextView>(R.id.crewName).text = o.optString("name")
                    findViewById<TextView>(R.id.crewDesc).text = o.optString("description").takeIf { it != "null" } ?: ""
                    val members = o.optJSONArray("members")
                    val names = (0 until (members?.length() ?: 0)).joinToString(", ") { i ->
                        val m = members!!.getJSONObject(i)
                        m.optString("name") + if (m.optString("role") == "owner") " 👑" else ""
                    }
                    findViewById<TextView>(R.id.crewMembers).text =
                        "👥 멤버 ${members?.length() ?: 0}명 · $names"
                    isMember = o.optBoolean("isMember")
                    findViewById<View>(R.id.chatInputRow).visibility = if (isMember) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.joinBar).visibility = if (isMember) View.GONE else View.VISIBLE
                    findViewById<TextView>(R.id.challengeSet).visibility = if (isMember) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun loadChallenge() {
        api().crewChallenge(crewId) { r ->
            r.onSuccess { o ->
                runOnUiThread {
                    val title = findViewById<TextView>(R.id.challengeTitle)
                    val text = findViewById<TextView>(R.id.challengeProgressText)
                    val bar = findViewById<ProgressBar>(R.id.challengeBar)
                    if (o == null) {
                        title.text = "🏁 진행 중인 챌린지 없음"
                        text.text = "멤버라면 '설정'으로 챌린지를 만들어보세요"
                        bar.progress = 0
                    } else {
                        val prog = o.optDouble("progressM") / 1000.0
                        val target = o.optDouble("targetM") / 1000.0
                        title.text = "🏁 ${o.optString("title")}"
                        text.text = "${String.format(Locale.US, "%.1f", prog)} / ${String.format(Locale.US, "%.0f", target)} km" +
                            (o.optString("periodEnd").takeIf { it.isNotBlank() && it != "null" }?.let { " · ~$it" } ?: "")
                        bar.progress = if (target > 0) (prog / target * 1000).toInt().coerceIn(0, 1000) else 0
                    }
                }
            }
        }
    }

    private fun loadMessages() {
        api().getCrewMessages(crewId) { r ->
            r.onSuccess { arr ->
                runOnUiThread {
                    val before = chat.size
                    chat.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optJSONObject("profiles")?.optString("display_name") ?: "러너"
                        chat.add(name to o.optString("text"))
                    }
                    chatAdapter.notifyDataSetChanged()
                    // 새 메시지가 생겼으면 맨 아래로 스크롤
                    if (chat.size != before && chat.isNotEmpty()) {
                        findViewById<ListView>(R.id.crewChat).post {
                            findViewById<ListView>(R.id.crewChat).setSelection(chat.size - 1)
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val input = findViewById<EditText>(R.id.chatInput)
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val uid = Session(this).userId ?: return
        input.setText("")
        api().postCrewMessage(crewId, uid, text) { r ->
            runOnUiThread {
                r.onSuccess { loadMessages() }
                    .onFailure { Toast.makeText(this, "전송 실패", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun join() {
        api().joinCrewById(crewId) { r ->
            runOnUiThread {
                r.onSuccess {
                    Toast.makeText(this, "크루에 가입했어요 🎉", Toast.LENGTH_SHORT).show()
                    loadDetail(); loadMessages()
                }.onFailure { Toast.makeText(this, "가입 실패", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun setChallengeDialog() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val title = EditText(this).apply { hint = "챌린지 이름 (예: 이번 주 100km)" }
        val km = EditText(this).apply { hint = "목표 거리 (km)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val days = EditText(this).apply { hint = "기간 (일)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        box.addView(title); box.addView(km); box.addView(days)
        android.app.AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("크루 챌린지 만들기")
            .setView(box)
            .setPositiveButton("만들기") { _, _ ->
                val t = title.text.toString().trim().ifEmpty { "크루 챌린지" }
                val target = km.text.toString().toDoubleOrNull() ?: 50.0
                val d = days.text.toString().toIntOrNull() ?: 7
                api().setCrewChallenge(crewId, t, target, d) { r ->
                    runOnUiThread {
                        r.onSuccess { loadChallenge() }
                            .onFailure { Toast.makeText(this, "생성 실패", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
