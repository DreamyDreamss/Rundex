package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TitleEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun ctx(
        discovered: Int = 0,
        lifetime: Double = 0.0,
        bronze: Boolean = false,
        gold: Boolean = false,
        hour: Int = 12,
    ) = TitleContext(discovered, lifetime, bronze, gold, hour)

    @Test
    fun `첫 발견은 first_card만 부여한다`() {
        val store = TitleStore(tmp.root)
        val granted = TitleEngine.evaluate(ctx(discovered = 1), store, 1000L)
        assertEquals(listOf("first_card"), granted.map { it.id })
    }

    @Test
    fun `같은 조건 재평가는 빈 목록 - 멱등`() {
        val store = TitleStore(tmp.root)
        TitleEngine.evaluate(ctx(discovered = 1), store, 1000L)
        assertEquals(emptyList<TitleDef>(), TitleEngine.evaluate(ctx(discovered = 1), store, 2000L))
    }

    @Test
    fun `복수 조건 동시 충족 시 모두 부여한다`() {
        val store = TitleStore(tmp.root)
        val granted = TitleEngine.evaluate(ctx(discovered = 10, bronze = true), store, 1000L)
        val ids = granted.map { it.id }
        assertTrue(ids.containsAll(listOf("first_card", "explorer_10", "first_bronze")))
    }

    @Test
    fun `야간 시작 러닝은 night_owl을 부여한다`() {
        val store = TitleStore(tmp.root)
        val ids = TitleEngine.evaluate(ctx(hour = 23), store, 1000L).map { it.id }
        assertTrue(ids.contains("night_owl"))
        assertTrue(!TitleEngine.evaluate(ctx(hour = 12), TitleStore(tmp.newFolder()), 1000L)
            .map { it.id }.contains("night_owl"))
    }
}
