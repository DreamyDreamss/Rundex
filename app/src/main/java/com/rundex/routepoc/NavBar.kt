package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.widget.TextView

/** 공유 하단 네비게이션 — 현재 탭 강조 + REORDER_TO_FRONT 전환(애니메이션 없음) */
object NavBar {

    fun setup(activity: Activity, currentNavId: Int) {
        val tabs = mapOf(
            R.id.navPlan to MainActivity::class.java,
            R.id.navRun to TrackActivity::class.java,
            R.id.navDex to DexActivity::class.java,
            R.id.navProfile to ProfileActivity::class.java,
        )
        val labels = mapOf(
            R.id.navPlan to R.id.navPlanLabel,
            R.id.navRun to R.id.navRunLabel,
            R.id.navDex to R.id.navDexLabel,
            R.id.navProfile to R.id.navProfileLabel,
        )
        tabs.forEach { (navId, target) ->
            val item = activity.findViewById<android.view.View>(navId) ?: return@forEach
            if (navId == currentNavId) {
                activity.findViewById<TextView>(labels.getValue(navId))
                    .setTextColor(activity.getColor(R.color.primary))
            } else {
                item.setOnClickListener {
                    activity.startActivity(
                        Intent(activity, target)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(0, 0)
                }
            }
        }
    }
}
