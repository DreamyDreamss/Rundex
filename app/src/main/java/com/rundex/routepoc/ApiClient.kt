package com.rundex.routepoc

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 백엔드(REST) 클라이언트. [BACKEND_DESIGN.md]의 계약을 그대로 따른다.
 * [ApiConfig.enabled]가 false면 모든 호출을 조용히 스킵해 로컬 전용 모드로 동작한다.
 *
 * 응답 파싱은 화면별로 진행하고, 여기서는 원시 JSON(JSONObject/JSONArray)을 콜백으로 넘긴다.
 */
class ApiClient(
    private val session: Session,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ── 공개 엔드포인트 ────────────────────────────────────────────

    /** 러닝 종료 시 업로드. 서버 RPC가 도감/등급/칭호/테마를 권위 재계산해 응답. */
    fun submitRun(payload: JSONObject, cb: (Result<JSONObject>) -> Unit = {}) =
        postObject("/rest/v1/rpc/submit_run", JSONObject().put("payload", payload), cb)

    fun getThemes(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/theme_collections?select=*", cb)

    /** 칭호 보유율(희소성) — title_rarity 뷰 */
    fun getTitleRarity(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/title_rarity?select=*", cb)

    /** 내가 획득한 칭호 — 코드 + 획득일 */
    fun getMyTitles(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/user_titles?user_id=eq.$userId&select=earned_at,titles(code)", cb)

    /** 대표 칭호 등 프로필 갱신 (본인 행) */
    fun patchMe(userId: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject("PATCH", "/rest/v1/profiles?id=eq.$userId", body, cb)

    /** 내 프로필 조회 — 닉네임/핸들/대표칭호 */
    fun getProfile(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/profiles?id=eq.$userId&select=display_name,handle,bio,rep_title_ids", cb)

    /** 내 통계(서버 원천) — 발견동/러닝수/총거리/칭호수 */
    fun getMyStats(cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/my_stats", JSONObject(), cb)

    /** 다른 사용자 공개 프로필 — 이름·핸들·발견동·공개러닝수·칭호·팔로워·내가 팔로우중인지 */
    fun getPublicProfile(userId: String, cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/public_profile", JSONObject().put("p_user", userId), cb)

    /** 팔로워/팔로잉 목록 — kind: "followers" | "following" */
    fun followList(userId: String, kind: String, cb: (Result<JSONArray>) -> Unit) {
        val req = build("/rest/v1/rpc/follow_list")
            ?.post(JSONObject().put("p_user", userId).put("p_kind", kind).toString().toRequestBody(jsonMedia))
            ?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }

    /** 내 알림 — 내 러닝에 좋아요 / 나를 팔로우 (최신순) */
    fun getNotifications(cb: (Result<JSONArray>) -> Unit) {
        val req = build("/rest/v1/rpc/my_notifications")
            ?.post("{}".toRequestBody(jsonMedia))?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }

    /** 도감 지도용 — 내가 발견한 동의 경계(GeoJSON)+누적거리 */
    fun getMyDexGeo(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/my_dex_geo?user_id=eq.$userId&select=region_code,name,total_m,geojson", cb)

    /** 특정 행정동에서 내가 뛴 런 목록 (RPC) */
    fun getMyRunsInRegion(regionCode: String, cb: (Result<JSONArray>) -> Unit) {
        val req = build("/rest/v1/rpc/my_runs_in_region")
            ?.post(JSONObject().put("p_region_code", regionCode).toString().toRequestBody(jsonMedia))
            ?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }

    // 추천 경로(Discover) — PostgREST 필터/정렬 쿼리
    fun listRoutes(query: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/routes?$query", cb)
    fun createRoute(body: JSONObject, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject("POST", "/rest/v1/routes", body, cb)
    fun rateRoute(body: JSONObject, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject("POST", "/rest/v1/route_ratings", body, cb)

    // 경로 북마크
    fun addBookmark(userId: String, routeId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject(
            "POST", "/rest/v1/route_bookmarks",
            JSONObject().put("user_id", userId).put("route_id", routeId), cb
        )
    fun removeBookmark(userId: String, routeId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendNoBody("DELETE", "/rest/v1/route_bookmarks?user_id=eq.$userId&route_id=eq.$routeId", cb)
    fun listMyBookmarks(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray(
            "/rest/v1/route_bookmarks?user_id=eq.$userId" +
                "&select=route_id,routes(id,name,distance_m,difficulty,geom)&order=created_at.desc", cb
        )

    // 소셜
    fun getFeed(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/activities?select=*&order=created_at.desc&limit=50", cb)

    /** 인스타형 홈 피드 — 공개 런 + 프로필 + 경로(GeoJSON), 최신순 */
    fun getPublicFeed(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/public_feed?order=created_at.desc&limit=50", cb)

    /** 특정 사용자들(팔로잉)의 공개 런만 */
    fun getFeedByUsers(userIds: List<String>, cb: (Result<JSONArray>) -> Unit) {
        if (userIds.isEmpty()) return cb(Result.success(JSONArray()))
        val inList = userIds.joinToString(",")
        getArray("/rest/v1/public_feed?user_id=in.($inList)&order=created_at.desc&limit=50", cb)
    }

    /** 피드 좋아요 */
    fun likeRun(runId: String, userId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject(
            "POST", "/rest/v1/run_reactions",
            JSONObject().put("run_id", runId).put("user_id", userId).put("emoji", "❤️"), cb
        )
    fun unlikeRun(runId: String, userId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendNoBody("DELETE", "/rest/v1/run_reactions?run_id=eq.$runId&user_id=eq.$userId", cb)

    /** 러닝 댓글 목록 (작성자·좋아요수·내가 눌렀는지 포함, 오래된순) */
    fun getRunComments(runId: String, cb: (Result<JSONArray>) -> Unit) {
        val req = build("/rest/v1/rpc/run_comments_list")
            ?.post(JSONObject().put("p_run_id", runId).toString().toRequestBody(jsonMedia))
            ?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }

    /** 댓글 작성 */
    fun postRunComment(runId: String, userId: String, text: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject(
            "POST", "/rest/v1/run_comments",
            JSONObject().put("run_id", runId).put("user_id", userId).put("text", text), cb
        )

    /** 댓글 좋아요 / 취소 */
    fun likeComment(commentId: String, userId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject("POST", "/rest/v1/comment_likes",
            JSONObject().put("comment_id", commentId).put("user_id", userId), cb)
    fun unlikeComment(commentId: String, userId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendNoBody("DELETE", "/rest/v1/comment_likes?comment_id=eq.$commentId&user_id=eq.$userId", cb)

    /** 내 러닝 기록(서버) — 최신순. 공개여부·경로(GeoJSON)·캡션 포함 */
    fun listMyRuns(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray(
            "/rest/v1/runs?user_id=eq.$userId" +
                "&select=id,started_at,ended_at,distance_m,duration_ms,visibility,caption,tags,geom," +
                "run_region_ledger(count),run_reactions(count)" +
                "&order=started_at.desc&limit=100", cb
        )

    /** 내 러닝 필드 수정 (캡션 등, 본인 행) */
    fun patchRun(runId: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject("PATCH", "/rest/v1/runs?id=eq.$runId", body, cb)

    /** 과거 런 공개/비공개 전환 (RPC) */
    fun setRunVisibility(runId: String, visibility: String, cb: (Result<JSONObject>) -> Unit = {}) =
        postObject(
            "/rest/v1/rpc/set_run_visibility",
            JSONObject().put("p_run_id", runId).put("p_visibility", visibility), cb
        )

    /** 핸들·이름으로 사용자 검색 (친구 찾기) */
    fun searchUsers(query: String, cb: (Result<JSONArray>) -> Unit) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        getArray(
            "/rest/v1/profiles?select=id,handle,display_name,rep_title_ids" +
                "&or=(handle.ilike.*$q*,display_name.ilike.*$q*)&limit=30", cb
        )
    }

    fun follow(followerId: String, followeeId: String, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject(
            "POST", "/rest/v1/follows",
            JSONObject().put("follower_id", followerId).put("followee_id", followeeId), cb
        )

    /** 내가 팔로우 중인 사용자 id 목록 */
    fun listFollowing(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/follows?follower_id=eq.$userId&select=followee_id", cb)

    fun unfollow(followerId: String, followeeId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendNoBody(
            "DELETE",
            "/rest/v1/follows?follower_id=eq.$followerId&followee_id=eq.$followeeId", cb
        )

    fun react(activityId: String, emoji: String, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject(
            "POST", "/rest/v1/reactions",
            JSONObject().put("activity_id", activityId).put("emoji", emoji), cb
        )

    // 크루
    fun createCrew(name: String, cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/create_crew", JSONObject().put("crew_name", name), cb)
    fun joinCrew(code: String, cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/join_crew", JSONObject().put("code", code), cb)
    fun listMyCrews(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/crew_members?user_id=eq.$userId&select=role,crews(id,name,join_code)", cb)
    fun getCrewMembers(crewId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/crew_members?crew_id=eq.$crewId&select=role,profiles(display_name,handle)", cb)

    // 크루 커뮤니티 ──────────────────────────────────────────────
    /** 추천(공개) 크루 목록 — 멤버수·가입여부 */
    fun listPublicCrews(cb: (Result<JSONArray>) -> Unit) {
        val req = build("/rest/v1/rpc/list_public_crews")
            ?.post("{}".toRequestBody(jsonMedia))?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }
    fun joinCrewById(crewId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        postObject("/rest/v1/rpc/join_crew_by_id", JSONObject().put("p_crew_id", crewId), cb)
    fun crewDetail(crewId: String, cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/crew_detail", JSONObject().put("p_crew_id", crewId), cb)
    /** 활성 챌린지 — 없으면 null */
    fun crewChallenge(crewId: String, cb: (Result<JSONObject?>) -> Unit) {
        val req = build("/rest/v1/rpc/crew_challenge")
            ?.post(JSONObject().put("p_crew_id", crewId).toString().toRequestBody(jsonMedia))?.build()
            ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { s -> if (s.isBlank() || s.trim() == "null") null else JSONObject(s) }) }
    }
    fun setCrewChallenge(crewId: String, title: String, targetKm: Double, days: Int, cb: (Result<JSONObject>) -> Unit = {}) =
        postObject(
            "/rest/v1/rpc/set_crew_challenge",
            JSONObject().put("p_crew_id", crewId).put("p_title", title).put("p_target_km", targetKm).put("p_days", days), cb
        )
    fun getCrewMessages(crewId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray(
            "/rest/v1/crew_messages?crew_id=eq.$crewId" +
                "&select=text,created_at,profiles(display_name)&order=created_at.asc&limit=100", cb
        )
    fun postCrewMessage(crewId: String, userId: String, text: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject(
            "POST", "/rest/v1/crew_messages",
            JSONObject().put("crew_id", crewId).put("user_id", userId).put("text", text), cb
        )

    // ── 내부 HTTP 헬퍼 ────────────────────────────────────────────

    private fun postObject(path: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit) =
        sendObject("POST", path, body, cb)

    private fun sendObject(method: String, path: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit) {
        val req = build(path)?.method(method, body.toString().toRequestBody(jsonMedia))?.build()
            ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONObject(it) }) }
    }

    private fun getArray(path: String, cb: (Result<JSONArray>) -> Unit) {
        val req = build(path)?.get()?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }

    /** PostgREST POST/PATCH는 표현(배열)을 반환 — 첫 객체만 꺼내준다 */
    private fun sendArrayAsObject(method: String, path: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit) {
        val req = build(path)
            ?.header("Prefer", "return=representation")
            ?.method(method, body.toString().toRequestBody(jsonMedia))?.build()
            ?: return cb(skipped())
        enqueue(req) { raw ->
            cb(raw.map { s ->
                val t = s.trim()
                if (t.startsWith("[")) JSONArray(t).optJSONObject(0) ?: JSONObject()
                else if (t.startsWith("{")) JSONObject(t) else JSONObject()
            })
        }
    }

    /** 본문 없는 요청(DELETE 등) — 응답 본문이 비어 있어도 성공 처리 */
    private fun sendNoBody(method: String, path: String, cb: (Result<JSONObject>) -> Unit) {
        val req = build(path)?.method(method, null)?.build() ?: return cb(skipped())
        enqueue(req) { raw ->
            cb(raw.map { s -> if (s.isBlank()) JSONObject() else JSONObject(s) })
        }
    }

    /** ApiConfig가 꺼져 있으면 null → 호출부는 조용히 스킵 */
    private fun build(path: String): Request.Builder? {
        if (!ApiConfig.enabled) return null
        val b = Request.Builder()
            .url(ApiConfig.BASE_URL.trimEnd('/') + path)
            .header("X-Device-Id", session.deviceId)
        if (ApiConfig.ANON_KEY.isNotBlank()) b.header("apikey", ApiConfig.ANON_KEY)
        session.token?.let { b.header("Authorization", "Bearer $it") }
            ?: ApiConfig.ANON_KEY.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private fun enqueue(req: Request, retryOn401: Boolean = true, done: (Result<String>) -> Unit) {
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                // 401(만료된 익명 토큰) → 토큰 갱신 후 1회 재시도. 데이터가 '갑자기 다 안나오는' 현상 방지.
                if (response.code == 401 && retryOn401 && session.refreshToken != null) {
                    response.close()
                    val fresh = refreshTokenBlocking()
                    if (fresh != null) {
                        val req2 = req.newBuilder().header("Authorization", "Bearer $fresh").build()
                        enqueue(req2, retryOn401 = false, done = done)
                    } else done(Result.failure(IOException("HTTP 401 (refresh failed)")))
                    return
                }
                response.use {
                    if (!it.isSuccessful) { done(Result.failure(IOException("HTTP ${it.code}"))); return }
                    done(Result.success(it.body?.string().orEmpty()))
                }
            }
        })
    }

    /** 만료된 액세스 토큰을 refresh_token으로 동기 갱신(콜백 스레드에서 호출). 실패 시 null. */
    private fun refreshTokenBlocking(): String? {
        val rt = session.refreshToken ?: return null
        val req = Request.Builder()
            .url(ApiConfig.BASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=refresh_token")
            .header("apikey", ApiConfig.ANON_KEY)
            .header("Authorization", "Bearer ${ApiConfig.ANON_KEY}")
            .post(JSONObject().put("refresh_token", rt).toString().toRequestBody(jsonMedia))
            .build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val o = JSONObject(r.body?.string().orEmpty())
                o.optString("access_token").takeIf { it.isNotEmpty() }?.let { session.token = it }
                o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { session.refreshToken = it }
                session.token
            }
        }.getOrNull()
    }

    private fun <T> skipped(): Result<T> = Result.failure(IllegalStateException("ApiConfig disabled"))
}
