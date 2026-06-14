package com.rundex.routepoc

import android.content.Context
import java.io.File

/**
 * 미업로드 러닝 재전송 — 어느 화면(피드·프로필·러닝)에서 재개돼도 큐를 비운다.
 * 성공 시 onUploaded 콜백으로 화면 새로고침을 유도(피드 갱신 안 되던 문제 해결).
 */
object UploadFlusher {
    fun flush(context: Context, onUploaded: () -> Unit = {}) {
        if (!ApiConfig.enabled) return
        val session = Session(context)
        if (session.userId == null) return
        val store = PendingUploadStore(File(context.filesDir, "data"))
        val items = store.all()
        if (items.isEmpty()) return
        val api = ApiClient(session)
        items.forEach { (localId, payload) ->
            api.submitRun(payload) { r -> r.onSuccess { store.remove(localId); onUploaded() } }
        }
    }
}
