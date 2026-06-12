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
    fun `골드 이후 200km당 별이 붙는다`() {
        assertEquals(0, Grades.starsOf(200_000.0))
        assertEquals(0, Grades.starsOf(399_999.0))
        assertEquals(1, Grades.starsOf(400_000.0))
        assertEquals(2, Grades.starsOf(600_000.0))
        assertEquals(0, Grades.starsOf(50_000.0))
    }
}
