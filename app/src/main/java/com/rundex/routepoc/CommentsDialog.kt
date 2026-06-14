package com.rundex.routepoc

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** 러닝 댓글 다이얼로그 — 목록 + 입력. 피드/프로필에서 💬 탭 시 호출. */
object CommentsDialog {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun show(activity: Activity, runId: String, onChanged: () -> Unit = {}) {
        if (!ApiConfig.enabled) {
            Toast.makeText(activity, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_bg)
            setPadding(px(20), px(18), px(20), px(14))
        }
        root.addView(TextView(activity).apply {
            text = "💬 댓글"; textSize = 18f
            setTextColor(activity.getColor(R.color.textDark))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val listBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(300)).apply {
                topMargin = px(10)
            }
        }
        root.addView(scroll)

        // 입력 줄
        val inputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(12), 0, 0)
        }
        val input = EditText(activity).apply {
            hint = "댓글 달기…"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setBackgroundResource(R.drawable.edit_field_bg)
            setPadding(px(14), px(12), px(14), px(12))
            textSize = 14f
        }
        val send = TextView(activity).apply {
            text = "전송"; textSize = 14f
            setTextColor(activity.getColor(android.R.color.white))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.btn_primary)
            gravity = Gravity.CENTER
            setPadding(px(18), px(12), px(18), px(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = px(8)
            }
        }
        inputRow.addView(input); inputRow.addView(send)
        root.addView(inputRow)

        val dialog = Dialog(activity, R.style.RundexDialog).apply {
            setContentView(root)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        fun renderComments(arr: JSONArray) {
            listBox.removeAllViews()
            if (arr.length() == 0) {
                listBox.addView(TextView(activity).apply {
                    text = "첫 댓글을 남겨보세요!"
                    setTextColor(activity.getColor(R.color.textGrey)); textSize = 13f
                    setPadding(0, px(16), 0, px(16)); gravity = Gravity.CENTER
                })
                return
            }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optJSONObject("profiles")?.optString("display_name")?.takeIf { it.isNotBlank() } ?: "러너"
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, px(8), 0, px(8))
                }
                cell.addView(TextView(activity).apply {
                    text = "$name  ·  ${relTime(o.optString("created_at"))}"
                    setTextColor(activity.getColor(R.color.textGrey)); textSize = 11f
                })
                cell.addView(TextView(activity).apply {
                    text = o.optString("text")
                    setTextColor(activity.getColor(R.color.textDark)); textSize = 14f
                    setPadding(0, px(2), 0, 0)
                })
                listBox.addView(cell)
            }
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        fun reload() {
            ApiClient(Session(activity)).getRunComments(runId) { r ->
                activity.runOnUiThread { r.onSuccess { renderComments(it) } }
            }
        }

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val uid = Session(activity).userId ?: run {
                Toast.makeText(activity, "로그인 후 댓글을 달 수 있어요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            input.setText("")
            ApiClient(Session(activity)).postRunComment(runId, uid, text) { r ->
                activity.runOnUiThread {
                    r.onSuccess { reload(); onChanged() }
                        .onFailure { Toast.makeText(activity, "댓글 전송 실패", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        reload()
        dialog.show()
    }

    private fun relTime(isoStr: String): String {
        if (isoStr.isBlank()) return ""
        val t = runCatching { iso.parse(isoStr.take(19))?.time }.getOrNull() ?: return ""
        val diff = System.currentTimeMillis() - t
        return when {
            diff < 60_000 -> "방금"
            diff < 3_600_000 -> "${diff / 60_000}분 전"
            diff < 86_400_000 -> "${diff / 3_600_000}시간 전"
            diff < 604_800_000 -> "${diff / 86_400_000}일 전"
            else -> SimpleDateFormat("M월 d일", Locale.KOREA).format(java.util.Date(t))
        }
    }
}
