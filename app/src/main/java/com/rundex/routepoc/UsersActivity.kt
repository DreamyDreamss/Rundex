package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast

/** 친구 찾기 — 핸들·이름으로 사용자 검색 후 단방향 팔로우. 서버(Supabase) 연동. */
class UsersActivity : Activity() {

    private val rows = ArrayList<SocialRow>()
    private val ids = ArrayList<String>()
    private lateinit var adapter: SocialRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        adapter = SocialRowAdapter(this, rows)
        val list = findViewById<ListView>(R.id.userList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> followAt(pos) }

        findViewById<EditText>(R.id.userSearchInput).setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(v.text.toString().trim()); true
            } else false
        }
    }

    private fun search(query: String) {
        if (query.length < 2) {
            Toast.makeText(this, "2자 이상 입력하세요", Toast.LENGTH_SHORT).show(); return
        }
        if (!ApiConfig.enabled) {
            Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        ApiClient(Session(this)).searchUsers(query) { result ->
            result.onSuccess { arr ->
                runOnUiThread {
                    rows.clear(); ids.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        ids.add(o.optString("id"))
                        val name = o.optString("display_name").ifEmpty { "러너" }
                        val handle = o.optString("handle").takeIf { it.isNotEmpty() }?.let { "@$it" }
                        rows.add(SocialRow(name.first().toString(), name, handle, "＋ 팔로우"))
                    }
                    if (rows.isEmpty()) rows.add(SocialRow("🔍", "검색 결과가 없습니다", "다른 이름으로 검색해보세요"))
                    adapter.notifyDataSetChanged()
                }
            }.onFailure {
                runOnUiThread { Toast.makeText(this, "검색 실패", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun followAt(pos: Int) {
        if (pos >= ids.size) return
        val me = Session(this).userId
        if (me == null) {
            Toast.makeText(this, "로그인 후 팔로우할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        ApiClient(Session(this)).follow(me, ids[pos]) { result ->
            runOnUiThread {
                result.onSuccess { Toast.makeText(this, "팔로우했어요", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, "팔로우 실패: ${it.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
