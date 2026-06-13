package com.rundex.routepoc

import org.json.JSONObject
import java.io.File

data class TitleContext(
    val discoveredCount: Int,
    val lifetimeMeters: Double,
    val anyBronze: Boolean,
    val anySilver: Boolean,
    val anyGold: Boolean,
    val runStartHour: Int, // 0~23
    val completedThemeSlugs: Set<String> = emptySet(),
)

data class TitleDef(val id: String, val name: String, val desc: String, val cond: (TitleContext) -> Boolean)

object Titles {
    val all = listOf(
        // 발견(넓이)
        TitleDef("first_card", "첫 발걸음", "첫 동을 발견한다") { it.discoveredCount >= 1 },
        TitleDef("explorer_10", "10동 탐험가", "동 10곳을 발견한다") { it.discoveredCount >= 10 },
        TitleDef("explorer_25", "25동 유랑가", "동 25곳을 발견한다") { it.discoveredCount >= 25 },
        TitleDef("explorer_50", "50동 개척자", "동 50곳을 발견한다") { it.discoveredCount >= 50 },
        TitleDef("explorer_100", "백동 정복자", "동 100곳을 발견한다") { it.discoveredCount >= 100 },
        // 누적 거리(꾸준함)
        TitleDef("dist_10", "첫 10K", "누적 10km를 달린다") { it.lifetimeMeters >= 10_000.0 },
        TitleDef("dist_21", "하프 러너", "누적 21.0975km를 달린다") { it.lifetimeMeters >= 21_097.5 },
        TitleDef("dist_42", "풀코스", "누적 42.195km를 달린다") { it.lifetimeMeters >= 42_195.0 },
        TitleDef("dist_100", "백킬로 클럽", "누적 100km를 달린다") { it.lifetimeMeters >= 100_000.0 },
        TitleDef("dist_200", "이백킬로 클럽", "누적 200km를 달린다") { it.lifetimeMeters >= 200_000.0 },
        TitleDef("dist_500", "오백킬로 레전드", "누적 500km를 달린다") { it.lifetimeMeters >= 500_000.0 },
        // 단골(깊이)
        TitleDef("first_bronze", "첫 단골", "한 동에서 브론즈에 도달한다") { it.anyBronze },
        TitleDef("first_silver", "실버 단골", "한 동에서 실버에 도달한다") { it.anySilver },
        TitleDef("first_gold", "골드 단골", "한 동에서 골드에 도달한다") { it.anyGold },
        // 시간대(한정)
        TitleDef("early_bird", "얼리버드", "05시~07시에 러닝을 시작한다") { it.runStartHour in 5..7 },
        TitleDef("night_owl", "야간 러너", "22시~04시에 러닝을 시작한다") { it.runStartHour >= 22 || it.runStartHour < 4 },
        // 테마 완성(컬렉션)
        TitleDef("theme_hangang", "한강 다리 정복자", "한강 다리를 모두 밟는다") { "hangang-bridges" in it.completedThemeSlugs },
        TitleDef("theme_palaces", "궁궐 순례자", "5대 궁을 모두 방문한다") { "five-palaces" in it.completedThemeSlugs },
        TitleDef("theme_landmarks", "랜드마크 헌터", "서울 랜드마크를 모두 모은다") { "seoul-landmarks" in it.completedThemeSlugs },
        TitleDef("theme_parks", "한강 공원 마스터", "한강 공원을 모두 누빈다") { "hangang-parks" in it.completedThemeSlugs },
        TitleDef("theme_mountains", "서울 등반가", "서울의 봉우리를 모두 오른다") { "seoul-mountains" in it.completedThemeSlugs },
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
