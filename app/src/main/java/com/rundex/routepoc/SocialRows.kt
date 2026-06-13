package com.rundex.routepoc

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/** 피드·추천코스·친구·크루 공용 인스타식 카드 행 */
data class SocialRow(
    val icon: String,          // 이모지 또는 이니셜 1자
    val title: String,
    val sub: String? = null,
    val accessory: String? = null, // 우측 칩 텍스트 (＋팔로우, 👍 등)
)

class SocialRowAdapter(
    private val activity: Activity,
    private val items: List<SocialRow>,
) : ArrayAdapter<SocialRow>(activity, R.layout.row_social, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: activity.layoutInflater.inflate(R.layout.row_social, parent, false)
        val row = items[position]
        v.findViewById<TextView>(R.id.socialIcon).text = row.icon
        v.findViewById<TextView>(R.id.socialTitle).text = row.title
        v.findViewById<TextView>(R.id.socialSub).apply {
            text = row.sub
            visibility = if (row.sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        v.findViewById<TextView>(R.id.socialAccessory).apply {
            text = row.accessory
            visibility = if (row.accessory.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        return v
    }
}
