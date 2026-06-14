package com.rundex.routepoc

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** 인스타형 댓글 바텀시트 — 아바타·이름·시간·텍스트·답글 + 이모지 퀵바 + 입력. */
object CommentsDialog {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val QUICK = listOf("❤️", "😭", "👏", "🥹", "😍", "😮", "😂", "🙌")

    fun show(activity: Activity, runId: String, authorName: String? = null, onChanged: () -> Unit = {}) {
        if (!ApiConfig.enabled) {
            Toast.makeText(activity, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.sheet_bg)
        }
        // 드래그 핸들
        root.addView(android.view.View(activity).apply {
            setBackgroundColor(0xFFD0D0D5.toInt())
            layoutParams = LinearLayout.LayoutParams(px(40), px(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = px(10); bottomMargin = px(8)
            }
        })
        root.addView(TextView(activity).apply {
            text = "댓글"; textSize = 16f; gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD); setTextColor(activity.getColor(R.color.textDark))
            setPadding(0, 0, 0, px(10))
        })
        root.addView(android.view.View(activity).apply {
            setBackgroundColor(activity.getColor(R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1))
        })

        val listBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(8))
        }
        val scroll = ScrollView(activity).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(scroll)

        // 이모지 퀵바
        val emojiRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(8), px(8), px(8), px(4))
        }
        root.addView(android.view.View(activity).apply {
            setBackgroundColor(activity.getColor(R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1))
        })

        // 입력 줄
        val inputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(px(14), px(6), px(14), px(12))
        }
        val avatar = TextView(activity).apply {
            text = "🏃"; textSize = 16f; gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_circle)
            layoutParams = LinearLayout.LayoutParams(px(36), px(36)).apply { marginEnd = px(10) }
        }
        val input = EditText(activity).apply {
            hint = (authorName?.let { "${it}님에게 " } ?: "") + "댓글 추가…"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setBackgroundResource(R.drawable.edit_field_bg)
            setPadding(px(14), px(10), px(14), px(10)); textSize = 14f
        }
        val send = TextView(activity).apply {
            text = "게시"; textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(activity.getColor(R.color.primary))
            setPadding(px(12), px(10), px(6), px(10))
        }
        inputRow.addView(avatar); inputRow.addView(input); inputRow.addView(send)

        for (e in QUICK) emojiRow.addView(TextView(activity).apply {
            text = e; textSize = 22f; setPadding(px(8), px(2), px(8), px(2))
            setOnClickListener { input.append(e); input.setSelection(input.text.length) }
        })
        root.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(emojiRow) })
        root.addView(inputRow)

        val dialog = Dialog(activity, R.style.RundexDialog).apply { setContentView(root) }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            val h = (activity.resources.displayMetrics.heightPixels * 0.72).toInt()
            setLayout(LinearLayout.LayoutParams.MATCH_PARENT, h)
        }

        fun renderComments(arr: JSONArray) {
            listBox.removeAllViews()
            if (arr.length() == 0) {
                listBox.addView(TextView(activity).apply {
                    text = "첫 댓글을 남겨보세요!"
                    setTextColor(activity.getColor(R.color.textGrey)); textSize = 13f
                    setPadding(0, px(20), 0, px(20)); gravity = Gravity.CENTER
                })
                return
            }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: o.optJSONObject("profiles")?.optString("display_name")?.takeIf { it.isNotBlank() } ?: "러너"
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, px(10), 0, px(10))
                }
                row.addView(TextView(activity).apply {
                    text = name.take(1); textSize = 15f; gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_icon_circle)
                    setTextColor(activity.getColor(R.color.iconTint))
                    layoutParams = LinearLayout.LayoutParams(px(38), px(38)).apply { marginEnd = px(12) }
                })
                val col = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                // 이름 · 시간
                val head = SpannableStringBuilder()
                head.append(name); head.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val t = "   " + relTime(o.optString("created_at"))
                val s0 = head.length; head.append(t)
                head.setSpan(ForegroundColorSpan(activity.getColor(R.color.textGrey)), s0, head.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                col.addView(TextView(activity).apply { text = head; textSize = 13f; setTextColor(activity.getColor(R.color.textDark)) })
                col.addView(TextView(activity).apply {
                    text = o.optString("text"); textSize = 14f
                    setTextColor(activity.getColor(R.color.textDark)); setPadding(0, px(2), 0, 0)
                })
                col.addView(TextView(activity).apply {
                    text = "답글 달기"; textSize = 11f
                    setTextColor(activity.getColor(R.color.textGrey)); setPadding(0, px(4), 0, 0)
                    setOnClickListener { input.requestFocus() }
                })
                row.addView(col)
                // 우측 하트 — 서버 저장(댓글 좋아요)
                val cid = o.optString("id")
                val likeCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(px(8), px(2), 0, 0)
                }
                val likedInit = o.optBoolean("liked_by_me")
                val countInit = o.optInt("likes")
                val heart = TextView(activity).apply { text = if (likedInit) "❤️" else "🤍"; textSize = 15f; gravity = Gravity.CENTER }
                val cnt = TextView(activity).apply {
                    text = if (countInit > 0) "$countInit" else ""
                    textSize = 11f; setTextColor(activity.getColor(R.color.textGrey)); gravity = Gravity.CENTER
                }
                var liked = likedInit; var count = countInit
                heart.setOnClickListener {
                    val uid = Session(activity).userId ?: return@setOnClickListener
                    liked = !liked
                    count = (count + if (liked) 1 else -1).coerceAtLeast(0)
                    heart.text = if (liked) "❤️" else "🤍"
                    cnt.text = if (count > 0) "$count" else ""
                    val api = ApiClient(Session(activity))
                    if (liked) api.likeComment(cid, uid) else api.unlikeComment(cid, uid)
                }
                likeCol.addView(heart); likeCol.addView(cnt)
                row.addView(likeCol)
                listBox.addView(row)
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
            diff < 3_600_000 -> "${diff / 60_000}분"
            diff < 86_400_000 -> "${diff / 3_600_000}시간"
            diff < 604_800_000 -> "${diff / 86_400_000}일"
            else -> SimpleDateFormat("M월 d일", Locale.KOREA).format(java.util.Date(t))
        }
    }
}
