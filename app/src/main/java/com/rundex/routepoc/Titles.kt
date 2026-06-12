package com.rundex.routepoc

import org.json.JSONObject
import java.io.File

data class TitleContext(
    val discoveredCount: Int,
    val lifetimeMeters: Double,
    val anyBronze: Boolean,
    val anyGold: Boolean,
    val runStartHour: Int, // 0~23
)

data class TitleDef(val id: String, val name: String, val desc: String, val cond: (TitleContext) -> Boolean)

object Titles {
    val all = listOf(
        TitleDef("first_card", "첫 발걸음", "첫 동을 발견한다") { it.discoveredCount >= 1 },
        TitleDef("explorer_10", "10동 탐험가", "동 10곳을 발견한다") { it.discoveredCount >= 10 },
        TitleDef("explorer_50", "50동 탐험가", "동 50곳을 발견한다") { it.discoveredCount >= 50 },
        TitleDef("dist_42", "풀코스", "누적 42.195km를 달린다") { it.lifetimeMeters >= 42_195.0 },
        TitleDef("dist_100", "백킬로 클럽", "누적 100km를 달린다") { it.lifetimeMeters >= 100_000.0 },
        TitleDef("first_bronze", "첫 단골", "한 동에서 브론즈에 도달한다") { it.anyBronze },
        TitleDef("first_gold", "골드 단골", "한 동에서 골드에 도달한다") { it.anyGold },
        TitleDef("night_owl", "야간 러너", "22시~04시에 러닝을 시작한다") { it.runStartHour >= 22 || it.runStartHour < 4 },
    )
}

/** 보유 칭호 저장(<dir>/titles.json) — id → 획득시각 */
class TitleStore(private val dir: File) {
    private val file = File(dir, "titles.json")

    init {
        dir.mkdirs()
    }

    fun owned(): Map<String, Long> {
        if (!file.exists()) return emptyMap()
        val o = JSONObject(file.readText())
        return o.keys().asSequence().associateWith { o.getLong(it) }
    }

    fun grant(id: String, nowMs: Long) {
        val o = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        if (!o.has(id)) {
            o.put(id, nowMs)
            file.writeText(o.toString())
        }
    }
}

object TitleEngine {
    /** 조건 충족 + 미보유 칭호를 부여하고 신규 획득 목록 반환 (멱등) */
    fun evaluate(ctx: TitleContext, store: TitleStore, nowMs: Long): List<TitleDef> {
        val owned = store.owned()
        val granted = Titles.all.filter { !owned.containsKey(it.id) && it.cond(ctx) }
        granted.forEach { store.grant(it.id, nowMs) }
        return granted
    }
}
