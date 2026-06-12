package com.rundex.routepoc

import android.content.Context

/** assets의 행정동 GeoJSON을 1회 로드해 공유하는 지연 싱글톤. 실패 시 null(도감 비활성). */
object RegionRepo {

    @Volatile
    private var index: RegionIndex? = null
    @Volatile
    private var loadFailed = false

    fun get(context: Context): RegionIndex? {
        if (loadFailed) return null
        index?.let { return it }
        synchronized(this) {
            index?.let { return it }
            return try {
                val json = context.assets.open("regions/seoul.geojson")
                    .bufferedReader().use { it.readText() }
                RegionIndex.fromGeoJson(json).also { index = it }
            } catch (e: Exception) {
                loadFailed = true
                null
            }
        }
    }
}
