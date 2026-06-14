package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.widget.TextView

/** 공유 하단 네비게이션 — 현재 탭 강조 + REORDER_TO_FRONT 전환(애니메이션 없음) */
object NavBar {

    fun setup(activity: Activity, currentNavId: Int) {
        val tabs = mapOf(
            R.id.navFeed to FeedActivity::class.java,
            R.id.navRun to TrackActivity::class.java,
            R.id.navDex to DexActivity::class.java,
            R.id.navProfile to ProfileActivity::class.java,
        )
        val icons = mapOf(
            R.id.navFeed to R.id.navFeedIcon,
            R.id.navRun to R.id.navRunIcon,
            R.id.navDex to R.id.navDexIcon,
            R.id.navProfile to R.id.navProfileIcon,
        )
        tabs.forEach { (navId, target) ->
            val item = activity.findViewById<android.view.View>(navId) ?: return@forEach
            // 라벨 없이 아이콘만 — 현재 탭은 선명, 나머지는 흐리게
            activity.findViewById<TextView>(icons.getValue(navId))?.alpha =
                if (navId == currentNavId) 1f else 0.4f
            if (navId != currentNavId) {
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
