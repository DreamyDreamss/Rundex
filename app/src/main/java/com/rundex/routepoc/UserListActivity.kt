package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView

/** 팔로워 / 팔로잉 목록 — 탭하면 그 러너의 공개 프로필. */
class UserListActivity : Activity() {

    private val rows = ArrayList<SocialRow>()
    private val ids = ArrayList<String>()
    private val names = ArrayList<String>()
    private lateinit var adapter: SocialRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)
        val userId = intent.getStringExtra("userId") ?: run { finish(); return }
        val kind = intent.getStringExtra("kind") ?: "followers"
        findViewById<TextView>(R.id.ulTitle).text = if (kind == "followers") "팔로워" else "팔로잉"
        findViewById<TextView>(R.id.ulBack).setOnClickListener { finish() }

        adapter = SocialRowAdapter(this, rows)
        val list = findViewById<ListView>(R.id.ulList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos < ids.size) startActivity(
                Intent(this, PublicProfileActivity::class.java)
                    .putExtra("userId", ids[pos]).putExtra("name", names[pos]))
        }

        ApiClient(Session(this)).followList(userId, kind) { r ->
            runOnUiThread {
                r.onSuccess { arr ->
                    rows.clear(); ids.clear(); names.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name").ifBlank { "러너" }
                        val handle = o.optString("handle").takeIf { it.isNotBlank() && it != "null" }
                        ids.add(o.optString("id")); names.add(name)
                        rows.add(SocialRow(name.take(1), name, handle?.let { "@$it" },
                            if (o.optBoolean("i_follow")) "팔로잉 ✓" else null))
                    }
                    adapter.notifyDataSetChanged()
                    findViewById<TextView>(R.id.ulEmpty).apply {
                        text = if (kind == "followers") "아직 팔로워가 없어요" else "아직 팔로우한 사람이 없어요"
                        visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
