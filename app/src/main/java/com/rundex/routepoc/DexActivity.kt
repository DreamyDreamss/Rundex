package com.rundex.routepoc

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import java.io.File
import java.util.Locale

/** 발견 도감 — 내가 뛴 동네를 지도에 등급색으로 칠하는 "도화지" 화면. */
class DexActivity : android.app.Activity() {

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var dexSource: GeoJsonSource? = null

    /** 클릭 히트테스트용 — 발견 동의 코드/이름/누적거리/외곽링 좌표([lon,lat]) */
    private data class DexRegion(val code: String, val name: String, val totalM: Double, val rings: List<List<DoubleArray>>)
    private val regions = ArrayList<DexRegion>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_dex)
        NavBar.setup(this, R.id.navDex)

        findViewById<TextView>(R.id.openThemeDexButton).setOnClickListener {
            startActivity(Intent(this, ThemeDexActivity::class.java))
        }
        findViewById<View>(R.id.dexSummaryCard).setOnClickListener { showDashboard() }

        mapView = findViewById(R.id.dexMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                val src = GeoJsonSource("dex-src")
                dexSource = src
                style.addSource(src)
                style.addLayer(
                    FillLayer("dex-fill", "dex-src").withProperties(
                        PropertyFactory.fillColor(Expression.get("color")),
                        PropertyFactory.fillOpacity(0.55f),
                    )
                )
                style.addLayer(
                    LineLayer("dex-line", "dex-src").withProperties(
                        PropertyFactory.lineColor(Expression.get("color")),
                        PropertyFactory.lineWidth(1.6f),
                    )
                )
                loadDex()
            }
            m.addOnMapClickListener { latLng -> onMapTap(latLng) }
        }
    }

    /** 색칠된 동을 탭하면 그 동네에서 내가 뛴 기록을 보여준다 */
    private fun onMapTap(latLng: LatLng): Boolean {
        val hit = regions.firstOrNull { r ->
            r.rings.any { ring -> pointInRing(latLng.longitude, latLng.latitude, ring) }
        } ?: return false
        showRegionRuns(hit)
        return true
    }

    /** 레이 캐스팅 점-다각형 포함 판정 (ring: [lon,lat] 목록) */
    private fun pointInRing(lon: Double, lat: Double, ring: List<DoubleArray>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i][0]; val yi = ring[i][1]
            val xj = ring[j][0]; val yj = ring[j][1]
            if (((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            ) inside = !inside
            j = i
        }
        return inside
    }

    /** 지오메트리(Polygon/MultiPolygon)에서 외곽 링들만 추출 */
    private fun outerRings(geom: JSONObject): List<List<DoubleArray>> {
        val type = geom.optString("type")
        val coords = geom.optJSONArray("coordinates") ?: return emptyList()
        fun ring(arr: JSONArray): List<DoubleArray> = (0 until arr.length()).map {
            val c = arr.getJSONArray(it); doubleArrayOf(c.getDouble(0), c.getDouble(1))
        }
        return when (type) {
            "Polygon" -> if (coords.length() > 0) listOf(ring(coords.getJSONArray(0))) else emptyList()
            "MultiPolygon" -> (0 until coords.length()).mapNotNull { p ->
                val poly = coords.getJSONArray(p)
                if (poly.length() > 0) ring(poly.getJSONArray(0)) else null
            }
            else -> emptyList()
        }
    }

    /** 색칠된 동 탭 → 등급·누적거리 + 그 동네 러닝 목록(탭하면 경로 상세) 다이얼로그 */
    private fun showRegionRuns(hit: DexRegion) {
        val session = Session(this)
        if (!ApiConfig.enabled || session.userId == null) return

        val view = layoutInflater.inflate(R.layout.dialog_region, null)
        val grade = Grades.gradeOf(hit.totalM)
        val colorHex = gradeColorHex(hit.totalM)
        view.findViewById<TextView>(R.id.regionName).text = "📍 ${hit.name}"
        runCatching {
            (view.findViewById<View>(R.id.regionDot).background.mutate()
                as android.graphics.drawable.GradientDrawable).setColor(Color.parseColor(colorHex))
        }
        view.findViewById<TextView>(R.id.regionGradeBadge).text = grade.label
        view.findViewById<TextView>(R.id.regionKm).text =
            String.format(Locale.US, "%.1f km", hit.totalM / 1000.0)
        view.findViewById<TextView>(R.id.regionGrade).text = grade.label
        view.findViewById<TextView>(R.id.regionRunCount).text = "…"

        val dialog = android.app.Dialog(this, R.style.RundexDialog).apply {
            setContentView(view)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        view.findViewById<TextView>(R.id.regionClose).setOnClickListener { dialog.dismiss() }

        val container = view.findViewById<android.widget.LinearLayout>(R.id.regionRunsContainer)
        val countView = view.findViewById<TextView>(R.id.regionRunCount)
        val label = view.findViewById<TextView>(R.id.regionRunsLabel)
        dialog.show()

        ApiClient(session).getMyRunsInRegion(hit.code) { result ->
            runOnUiThread {
                result.onSuccess { arr ->
                    countView.text = "${arr.length()}회"
                    container.removeAllViews()
                    if (arr.length() == 0) {
                        label.text = "아직 이 동네에서 기록한 러닝이 없어요"
                        return@runOnUiThread
                    }
                    val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val fmt = java.text.SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val row = layoutInflater.inflate(R.layout.row_region_run, container, false)
                        val d = runCatching { iso.parse(o.optString("started_at").take(19)) }.getOrNull()
                        row.findViewById<TextView>(R.id.rrDate).text = d?.let { fmt.format(it) } ?: "-"
                        row.findViewById<TextView>(R.id.rrStats).text =
                            "${TrackActivity.formatKm(o.optDouble("distance_m"))} · " +
                            "${TrackActivity.formatDuration(o.optLong("duration_ms"))} · " +
                            "이 동 ${o.optInt("meters")}m"
                        row.findViewById<TextView>(R.id.rrBadge).text =
                            if (o.optString("visibility") == "public") "🔓" else "🔒"
                        val geo = o.optString("geojson").takeIf { it.isNotBlank() && it != "null" }
                        row.setOnClickListener {
                            if (geo == null) {
                                android.widget.Toast.makeText(this, "이 기록엔 경로가 없어요", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                startActivity(
                                    Intent(this, TrackViewActivity::class.java)
                                        .putExtra("serverGeoJson", geo)
                                        .putExtra("distanceM", o.optDouble("distance_m"))
                                        .putExtra("durationMs", o.optLong("duration_ms"))
                                        .putExtra("caption", o.optString("caption").takeIf { it != "null" } ?: "")
                                        .putExtra("visibility", o.optString("visibility", "private"))
                                        .putExtra("startedAtIso", o.optString("started_at"))
                                )
                                dialog.dismiss()
                            }
                        }
                        container.addView(row)
                    }
                }.onFailure {
                    android.widget.Toast.makeText(this, "기록을 불러올 수 없어요", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
    }

    private val sidoMap = linkedMapOf(
        "11" to "서울", "21" to "부산", "22" to "대구", "23" to "인천", "24" to "광주",
        "25" to "대전", "26" to "울산", "29" to "세종", "31" to "경기", "32" to "강원",
        "33" to "충북", "34" to "충남", "35" to "전북", "36" to "전남", "37" to "경북",
        "38" to "경남", "39" to "제주",
    )

    /** 도감 대시보드 — 등급 인벤토리 + 시도별 정복 현황 + 누적 거리 */
    private fun showDashboard() {
        val index = RegionRepo.get(this)
        val totalBySido = HashMap<String, Int>()
        index?.regions?.forEach { r -> totalBySido.merge(r.code.take(2), 1, Int::plus) }

        val discBySido = HashMap<String, Int>()
        var g = 0; var s = 0; var b = 0; var c = 0; var sumM = 0.0
        for (r in regions) {
            discBySido.merge(r.code.take(2), 1, Int::plus)
            sumM += r.totalM
            when (Grades.gradeOf(r.totalM)) {
                Grade.GOLD -> g++; Grade.SILVER -> s++; Grade.BRONZE -> b++; Grade.CARD -> c++
            }
        }
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(px(22), px(20), px(22), px(8))
        }
        fun label(t: String, size: Float, color: Int, top: Int = 0, bold: Boolean = false) =
            TextView(this).apply {
                text = t; textSize = size; setTextColor(getColor(color))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, px(top), 0, 0)
            }

        content.addView(label("🎨 도감 대시보드", 20f, R.color.textDark, 0, true))
        val totalAll = index?.totalCount ?: 3482
        val pct = regions.size * 100.0 / totalAll
        content.addView(label("전국 ${regions.size} / $totalAll 동  ·  ${String.format(Locale.US, "%.1f", pct)}%", 14f, R.color.textGrey, 4))
        content.addView(label("🥇 골드 $g    🥈 실버 $s    🥉 브론즈 $b    🃏 발견 $c", 14f, R.color.textDark, 14, true))
        content.addView(label("총 누적 ${String.format(Locale.US, "%.1f", sumM / 1000.0)} km", 13f, R.color.primary, 6, true))
        content.addView(View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, px(1)).apply { topMargin = px(16) }
            setBackgroundColor(getColor(R.color.divider))
        })
        content.addView(label("🗺 시도별 정복 현황", 14f, R.color.textDark, 14, true))

        // 발견 많은 시도 우선, 그다음 전체 동수
        val order = sidoMap.keys.filter { (totalBySido[it] ?: 0) > 0 }
            .sortedWith(compareByDescending<String> { discBySido[it] ?: 0 }.thenByDescending { totalBySido[it] ?: 0 })
        for (code in order) {
            val disc = discBySido[code] ?: 0
            val tot = totalBySido[code] ?: 0
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, px(7), 0, px(7))
            }
            row.addView(TextView(this).apply {
                text = sidoMap[code]; textSize = 14f; setTextColor(getColor(R.color.textDark))
                layoutParams = android.widget.LinearLayout.LayoutParams(px(48), -2)
                if (disc > 0) setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            row.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = if (tot > 0) (disc * 1000 / tot) else 0
                progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, px(8), 1f).apply {
                    leftMargin = px(8); rightMargin = px(8)
                }
            })
            row.addView(TextView(this).apply {
                text = "$disc/$tot"; textSize = 12f
                setTextColor(getColor(if (disc > 0) R.color.textDark else R.color.textGrey))
                layoutParams = android.widget.LinearLayout.LayoutParams(px(58), -2)
                gravity = android.view.Gravity.END
            })
            content.addView(row)
        }

        val scroll = android.widget.ScrollView(this).apply { addView(content) }
        android.app.AlertDialog.Builder(this, R.style.RundexDialog)
            .setView(scroll)
            .setPositiveButton("닫기", null)
            .show()
    }

    override fun onResume() { super.onResume(); mapView.onResume(); if (map != null) loadDex() }

    private fun loadDex() {
        val index = RegionRepo.get(this)
        val total = index?.totalCount ?: 3482
        val session = Session(this)
        val uid = session.userId
        if (!ApiConfig.enabled || uid == null) { showEmpty(0, total); return }

        ApiClient(session).getMyDexGeo(uid) { result ->
            runOnUiThread {
                result.onSuccess { arr -> render(arr, total) }
                    .onFailure { showEmpty(0, total) }
            }
        }
    }

    private fun render(arr: JSONArray, total: Int) {
        val features = ArrayList<org.maplibre.geojson.Feature>()
        regions.clear()
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        var any = false

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val geo = o.optString("geojson")
            if (geo.isBlank()) continue
            val color = gradeColorHex(o.optDouble("total_m"))
            val geomObj = JSONObject(geo)
            regions.add(DexRegion(o.optString("region_code"), o.optString("name"), o.optDouble("total_m"), outerRings(geomObj)))
            val featJson = JSONObject()
                .put("type", "Feature")
                .put(
                    "properties", JSONObject()
                        .put("color", color)
                        .put("name", o.optString("name"))
                        .put("code", o.optString("region_code"))
                )
                .put("geometry", JSONObject(geo))
            features.add(org.maplibre.geojson.Feature.fromJson(featJson.toString()))
            // 경계 계산
            forEachCoord(JSONObject(geo)) { lon, lat ->
                any = true
                if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon; if (lon > maxLon) maxLon = lon
            }
        }

        dexSource?.setGeoJson(FeatureCollection.fromFeatures(features))

        // 등급 인벤토리 집계
        var g = 0; var s = 0; var b = 0; var c = 0
        for (r in regions) when (Grades.gradeOf(r.totalM)) {
            Grade.GOLD -> g++; Grade.SILVER -> s++; Grade.BRONZE -> b++; Grade.CARD -> c++
        }
        findViewById<TextView>(R.id.tierGold).text = "🥇 $g"
        findViewById<TextView>(R.id.tierSilver).text = "🥈 $s"
        findViewById<TextView>(R.id.tierBronze).text = "🥉 $b"
        findViewById<TextView>(R.id.tierCard).text = "🃏 $c"

        val count = arr.length()
        findViewById<TextView>(R.id.dexEmpty).visibility = if (count == 0) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.dexHeader).text =
            "🎨 전국 $count / $total 동"
        val pct = if (total > 0) (count * 1000.0 / total).toInt() else 0
        findViewById<android.widget.ProgressBar>(R.id.dexCompletion).progress = pct
        findViewById<TextView>(R.id.dexHeader).append(
            "   ·   ${String.format(Locale.US, "%.1f", count * 100.0 / total)}%"
        )

        if (any) {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(maxLat, maxLon)).include(LatLng(minLat, minLon)).build()
            map?.let { m ->
                try { m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120)) } catch (_: Exception) {}
            }
        }
    }

    private fun showEmpty(count: Int, total: Int) {
        dexSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        findViewById<TextView>(R.id.dexEmpty).visibility = View.VISIBLE
        findViewById<TextView>(R.id.dexHeader).text = "🎨 전국 $count / $total 동"
        findViewById<android.widget.ProgressBar>(R.id.dexCompletion).progress = 0
    }

    /** 누적거리 → 등급 색상 hex (colors.xml 과 동일 팔레트) */
    private fun gradeColorHex(m: Double): String = when (Grades.gradeOf(m)) {
        Grade.CARD -> "#AEB2BC"
        Grade.BRONZE -> "#CD7F32"
        Grade.SILVER -> "#8E9196"
        Grade.GOLD -> "#F5A623"
    }

    /** Polygon/MultiPolygon 좌표를 순회 (경계 계산용) */
    private fun forEachCoord(geom: JSONObject, f: (Double, Double) -> Unit) {
        val coords = geom.optJSONArray("coordinates") ?: return
        fun walk(node: Any?) {
            if (node is JSONArray) {
                if (node.length() >= 2 && node.opt(0) is Number && node.opt(1) is Number) {
                    f(node.getDouble(0), node.getDouble(1))
                } else {
                    for (i in 0 until node.length()) walk(node.opt(i))
                }
            }
        }
        walk(coords)
    }

    // ── MapView 생명주기 ─────────────────────────────────────────
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
