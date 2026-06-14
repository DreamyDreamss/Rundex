package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.TextView

/**
 * 러닝 탭 상단 세그먼트 토글 [🏃 기록 | 🗺 계획].
 * 기록(TrackActivity)·계획(MainActivity) 두 화면이 같은 러닝 탭 안에서 즉시 전환된다.
 */
object RunSegment {

    enum class Tab { RECORD, PLAN }

    fun setup(activity: Activity, active: Tab) {
        val record = activity.findViewById<TextView>(R.id.segRecord)
        val plan = activity.findViewById<TextView>(R.id.segPlan)
        if (record == null || plan == null) return

        style(record, active == Tab.RECORD)
        style(plan, active == Tab.PLAN)

        record.setOnClickListener {
            if (active != Tab.RECORD) switchTo(activity, TrackActivity::class.java)
        }
        plan.setOnClickListener {
            if (active != Tab.PLAN) switchTo(activity, MainActivity::class.java)
        }
    }

    private fun style(tv: TextView, on: Boolean) {
        if (on) {
            tv.setBackgroundResource(R.drawable.seg_active_bg)
            tv.setTextColor(Color.WHITE)
        } else {
            tv.background = null
            tv.setTextColor(tv.context.getColor(R.color.textGrey))
        }
    }

    private fun switchTo(activity: Activity, target: Class<*>) {
        activity.startActivity(
            Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
