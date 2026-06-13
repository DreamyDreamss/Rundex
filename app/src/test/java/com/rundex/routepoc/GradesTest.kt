package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class GradesTest {

    @Test
    fun `등급 임계 - 10km 브론즈, 50km 실버, 200km 골드`() {
        assertEquals(Grade.CARD, Grades.gradeOf(0.0))
        assertEquals(Grade.CARD, Grades.gradeOf(9_999.0))
        assertEquals(Grade.BRONZE, Grades.gradeOf(10_000.0))
        assertEquals(Grade.SILVER, Grades.gradeOf(50_000.0))
        assertEquals(Grade.GOLD, Grades.gradeOf(200_000.0))
    }

    @Test
    fun `골드 이후 별 간격은 로그형으로 넓어진다`() {
        // 별 k까지 추가거리 = 50km·k(k+1)/2 → 별1 +50, 별2 +150, 별3 +300km
        assertEquals(0, Grades.starsOf(200_000.0))
        assertEquals(0, Grades.starsOf(249_999.0))
        assertEquals(1, Grades.starsOf(250_000.0))   // +50km
        assertEquals(1, Grades.starsOf(349_999.0))
        assertEquals(2, Grades.starsOf(350_000.0))   // +150km
        assertEquals(2, Grades.starsOf(499_999.0))
        assertEquals(3, Grades.starsOf(500_000.0))   // +300km
        assertEquals(0, Grades.starsOf(50_000.0))
    }

    @Test
    fun `별 임계 거리 공식`() {
        assertEquals(50_000.0, Grades.starThresholdMeters(1), 1e-6)
        assertEquals(150_000.0, Grades.starThresholdMeters(2), 1e-6)
        assertEquals(300_000.0, Grades.starThresholdMeters(3), 1e-6)
    }
}
