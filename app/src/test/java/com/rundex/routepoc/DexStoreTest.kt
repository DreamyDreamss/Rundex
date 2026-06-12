package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DexStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val names = mapOf("A" to "가동", "B" to "나동")

    @Test
    fun `첫 적용은 신규 발견으로 기록된다`() {
        val dex = DexStore(tmp.root)
        val r = dex.applyRun(mapOf("A" to 1200.0), names, 1500.0, 1000L)
        assertEquals(1, r.newRegions.size)
        assertEquals("가동", r.newRegions[0].name)
        assertEquals(1, dex.discoveredCount())
        assertEquals(1500.0, dex.lifetimeMeters(), 1e-9)
    }

    @Test
    fun `누적으로 등급 임계를 넘으면 승급이 보고된다`() {
        val dex = DexStore(tmp.root)
        dex.applyRun(mapOf("A" to 9_000.0), names, 9_000.0, 1000L)
        val r = dex.applyRun(mapOf("A" to 1_500.0), names, 1_500.0, 2000L)
        assertEquals(0, r.newRegions.size)
        assertEquals(1, r.gradeUps.size)
        assertEquals(Grade.CARD, r.gradeUps[0].from)
        assertEquals(Grade.BRONZE, r.gradeUps[0].to)
        assertTrue(dex.bestGradeReached(Grade.BRONZE))
    }

    @Test
    fun `entries는 최근 발견순으로 정렬된다`() {
        val dex = DexStore(tmp.root)
        dex.applyRun(mapOf("A" to 100.0), names, 100.0, 1000L)
        dex.applyRun(mapOf("B" to 100.0), names, 100.0, 2000L)
        assertEquals(listOf("나동", "가동"), dex.entries().map { it.name })
    }
}
