package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast

/** 친구 찾기 — 핸들·이름 검색 후 팔로우/언팔로우 토글. 서버(Supabase) 연동. */
class UsersActivity : Activity() {

    private val rows = ArrayList<SocialRow>()
    private val ids = ArrayList<String>()
    private val names = ArrayList<String>()
    private val handles = ArrayList<String?>()
    private val following = HashSet<String>()
    private lateinit var adapter: SocialRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        adapter = SocialRowAdapter(this, rows)
        val list = findViewById<ListView>(R.id.userList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> openProfile(pos) }

        findViewById<EditText>(R.id.userSearchInput).setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(v.text.toString().trim()); true } else false
        }

        // 내 팔로잉 미리 로드 → 이후 추천 러너 표시
        Session(this).userId?.let { me ->
            if (ApiConfig.enabled) ApiClient(Session(this)).listFollowing(me) { r ->
                r.onSuccess { arr ->
                    runOnUiThread {
                        following.clear()
                        for (i in 0 until arr.length()) following.add(arr.getJSONObject(i).optString("followee_id"))
                        loadSuggested()
                    }
                }.onFailure { runOnUiThread { loadSuggested() } }
            }
        }
    }

    /** 검색 전 기본 화면 — 추천 러너 노출 */
    private fun loadSuggested() {
        if (!ApiConfig.enabled) return
        val me = Session(this).userId
        ApiClient(Session(this)).suggestedUsers { r ->
            runOnUiThread {
                r.onSuccess { arr ->
                    ids.clear(); names.clear(); handles.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("id") == me) continue
                        ids.add(o.optString("id"))
                        names.add(o.optString("display_name").ifEmpty { "러너" })
                        val h = o.optString("handle").takeIf { it.isNotBlank() && it != "null" }
                        handles.add((h?.let { "@$it · " } ?: "") + "러닝 ${o.optInt("runs")} · 팔로워 ${o.optInt("followers")}")
                    }
                    render()
                    if (ids.isEmpty()) {
                        rows.add(SocialRow("✨", "추천할 러너가 아직 없어요", "이름·@아이디로 검색해보세요"))
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun search(query: String) {
        if (query.length < 2) { Toast.makeText(this, "2자 이상 입력하세요", Toast.LENGTH_SHORT).show(); return }
        if (!ApiConfig.enabled) { Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show(); return }
        val me = Session(this).userId
        ApiClient(Session(this)).searchUsers(query) { result ->
            result.onSuccess { arr ->
                runOnUiThread {
                    rows.clear(); ids.clear(); names.clear(); handles.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("id") == me) continue   // 본인 제외
                        ids.add(o.optString("id"))
                        names.add(o.optString("display_name").ifEmpty { "러너" })
                        handles.add(o.optString("handle").takeIf { it.isNotEmpty() }?.let { "@$it" })
                    }
                    render()
                    if (ids.isEmpty()) {
                        rows.add(SocialRow("🔍", "검색 결과가 없습니다", "이름이나 @아이디로 검색해보세요"))
                        adapter.notifyDataSetChanged()
                    }
                }
            }.onFailure { runOnUiThread { Toast.makeText(this, "검색 실패", Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun render() {
        rows.clear()
        for (i in ids.indices) {
            val on = following.contains(ids[i])
            rows.add(SocialRow(names[i].first().toString(), names[i], handles[i], if (on) "팔로잉 ✓" else "＋ 팔로우"))
        }
        adapter.notifyDataSetChanged()
    }

    /** 검색 결과 탭 → 그 러너의 공개 프로필 (거기서 팔로우) */
    private fun openProfile(pos: Int) {
        if (pos >= ids.size) return
        startActivity(
            android.content.Intent(this, PublicProfileActivity::class.java)
                .putExtra("userId", ids[pos])
                .putExtra("name", names[pos])
        )
    }

    private fun toggleFollow(pos: Int) {
        if (pos >= ids.size) return
        val me = Session(this).userId ?: run {
            Toast.makeText(this, "로그인 후 팔로우할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        val target = ids[pos]
        val api = ApiClient(Session(this))
        if (following.contains(target)) {
            following.remove(target); render()
            api.unfollow(me, target) { r -> runOnUiThread { r.onSuccess { Toast.makeText(this, "언팔로우했어요", Toast.LENGTH_SHORT).show() } } }
        } else {
            following.add(target); render()
            api.follow(me, target) { r ->
                runOnUiThread {
                    r.onSuccess { Toast.makeText(this, "${names[pos]}님을 팔로우했어요", Toast.LENGTH_SHORT).show() }
                        .onFailure { following.remove(target); render(); Toast.makeText(this, "팔로우 실패", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}
