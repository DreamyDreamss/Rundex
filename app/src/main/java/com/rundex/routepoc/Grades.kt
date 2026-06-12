package com.rundex.routepoc

enum class Grade(val label: String) {
    CARD("발견"), BRONZE("브론즈"), SILVER("실버"), GOLD("골드");
}

/** 누적 거리 → 등급. 임계: 10km/50km/200km, 골드 이후 200km당 ★ (상한 개방) */
object Grades {
    const val BRONZE_M = 10_000.0
    const val SILVER_M = 50_000.0
    const val GOLD_M = 200_000.0

    fun gradeOf(totalMeters: Double): Grade = when {
        totalMeters >= GOLD_M -> Grade.GOLD
        totalMeters >= SILVER_M -> Grade.SILVER
        totalMeters >= BRONZE_M -> Grade.BRONZE
        else -> Grade.CARD
    }

    fun starsOf(totalMeters: Double): Int =
        if (totalMeters < GOLD_M) 0 else ((totalMeters - GOLD_M) / GOLD_M).toInt()

    fun badge(totalMeters: Double): String = when (gradeOf(totalMeters)) {
        Grade.CARD -> "🃏"
        Grade.BRONZE -> "🥉"
        Grade.SILVER -> "🥈"
        Grade.GOLD -> "🥇" + "★".repeat(starsOf(totalMeters))
    }
}
