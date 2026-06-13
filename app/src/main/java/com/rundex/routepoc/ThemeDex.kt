package com.rundex.routepoc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 테마 도감의 한 장소(명소). 경로가 [radiusM] 이내를 지나면 수집. */
data class ThemePlace(val id: String, val name: String, val lat: Double, val lon: Double, val radiusM: Double)

/** 큐레이션 컬렉션(예: 한강 다리, 서울 5대 궁) */
data class ThemeCollection(
    val slug: String,
    val title: String,
    val emoji: String,
    val desc: String,
    val places: List<ThemePlace>,
)

/** 한 번의 러닝에서 컬렉션별로 새로 수집한 장소 */
data class ThemeDelta(val slug: String, val title: String, val newPlaces: List<ThemePlace>, val completed: Boolean)

/** 경로 ↔ 테마 장소 통과 판정 (서버 ST_DWithin 의 클라 버전) */
object ThemeMatcher {
    /** 경로가 반경 내를 지난 장소 id 집합 */
    fun collectedPlaceIds(path: List<LatLngPoint>, places: List<ThemePlace>): Set<String> {
        if (path.isEmpty()) return emptySet()
        val out = HashSet<String>()
        for (place in places) {
            val hit = path.any { p ->
                Haversine.meters(p.lat, p.lon, place.lat, place.lon) <= place.radiusM
            }
            if (hit) out.add(place.id)
        }
        return out
    }
}

/** 테마 정의 로더 — assets의 시드 사용(서버가 권위, 로컬은 선반영). */
object ThemeRepo {
    @Volatile private var cache: List<ThemeCollection>? = null

    fun all(context: Context): List<ThemeCollection> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: runCatching {
                val json = context.assets.open("themes/seoul_themes.json")
                    .bufferedReader().use { it.readText() }
                parse(json)
            }.getOrDefault(emptyList()).also { cache = it }
        }
    }

    fun parse(json: String): List<ThemeCollection> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val places = c.getJSONArray("places")
            ThemeCollection(
                slug = c.getString("slug"),
                title = c.getString("title"),
                emoji = c.optString("emoji", "🌟"),
                desc = c.optString("desc", ""),
                places = (0 until places.length()).map { j ->
                    val p = places.getJSONObject(j)
                    ThemePlace(
                        id = p.getString("id"),
                        name = p.getString("name"),
                        lat = p.getDouble("lat"),
                        lon = p.getDouble("lon"),
                        radiusM = p.optDouble("radiusM", 120.0),
                    )
                },
            )
        }
    }
}

/** 수집 진행 저장(<dir>/themes.json): slug → 수집한 placeId 목록 */
class ThemeStore(dir: File) {
    private val file = File(dir, "themes.json")
    init { dir.mkdirs() }

    fun collected(slug: String): Set<String> {
        if (!file.exists()) return emptySet()
        val root = JSONObject(file.readText())
        val arr = root.optJSONArray(slug) ?: return emptySet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    /** 모든 장소를 모은(완성된) 컬렉션 slug 집합 */
    fun completedSlugs(collections: List<ThemeCollection>): Set<String> =
        collections.filter { it.places.isNotEmpty() && collected(it.slug).size >= it.places.size }
            .map { it.slug }.toSet()

    /**
     * 러닝 경로를 모든 컬렉션에 반영하고, 새로 수집한 장소/완성 여부를 반환.
     * 멱등하지 않음(누적) — 이미 수집한 장소는 delta에서 제외된다.
     */
    fun applyRun(path: List<LatLngPoint>, collections: List<ThemeCollection>): List<ThemeDelta> {
        val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val deltas = mutableListOf<ThemeDelta>()
        for (col in collections) {
            val before = root.optJSONArray(col.slug)?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toMutableSet()
            } ?: mutableSetOf()
            val hitIds = ThemeMatcher.collectedPlaceIds(path, col.places)
            val newIds = hitIds - before
            if (newIds.isEmpty()) continue
            before.addAll(newIds)
            root.put(col.slug, JSONArray(before.toList()))
            val newPlaces = col.places.filter { it.id in newIds }
            deltas.add(ThemeDelta(col.slug, col.title, newPlaces, completed = before.size >= col.places.size))
        }
        if (deltas.isNotEmpty()) file.writeText(root.toString())
        return deltas
    }
}
