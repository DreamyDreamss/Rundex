package com.rundex.routepoc

enum class Grade(val label: String) {
    CARD("발견"), BRONZE("브론즈"), SILVER("실버"), GOLD("골드");
}

/**
 * 누적 거리 → 등급. 임계: 10km/50km/200km (앞 등급은 빨리, 골드는 단골만 — 로그형 체감).
 * 골드 이후 ★은 로그형: k번째 별까지 추가 거리 = STAR_BASE_M·k(k+1)/2
 * (별1=+50km, 별2=+100km, 별3=+150km… 간격이 점점 넓어져 최고 단골일수록 천천히 오른다)
 */
object Grades {
    const val BRONZE_M = 10_000.0
    const val SILVER_M = 50_000.0
    const val GOLD_M = 200_000.0
    const val STAR_BASE_M = 50_000.0

    fun gradeOf(totalMeters: Double): Grade = when {
        totalMeters >= GOLD_M -> Grade.GOLD
        totalMeters >= SILVER_M -> Grade.SILVER
        totalMeters >= BRONZE_M -> Grade.BRONZE
        else -> Grade.CARD
    }

    fun starsOf(totalMeters: Double): Int {
        if (totalMeters < GOLD_M) return 0
        val over = totalMeters - GOLD_M
        var k = 0
        // k(k+1)/2 * STAR_BASE_M <= over 인 최대 k
        while ((k + 1).toLong() * (k + 2) / 2.0 * STAR_BASE_M <= over) k++
        return k
    }

    /** k개째 별에 도달하는 데 필요한 골드 이후 누적 거리(m) */
    fun starThresholdMeters(k: Int): Double = k.toLong() * (k + 1) / 2.0 * STAR_BASE_M

    fun badge(totalMeters: Double): String = when (gradeOf(totalMeters)) {
        Grade.CARD -> "🃏"
        Grade.BRONZE -> "🥉"
        Grade.SILVER -> "🥈"
        Grade.GOLD -> "🥇" + "★".repeat(starsOf(totalMeters))
    }
}
