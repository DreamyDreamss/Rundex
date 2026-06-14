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

    /** 클릭 히트테스트용 — 발견 동의 코드/이름/외곽링 좌표([lon,lat]) */
    private data class DexRegion(val code: String, val name: String, val rings: List<List<DoubleArray>>)
    private val regions = ArrayList<DexRegion>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_dex)
        NavBar.setup(this, R.id.navDex)

        findViewById<TextView>(R.id.openThemeDexButton).setOnClickListener {
            startActivity(Intent(this, ThemeDexActivity::class.java))
        }

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
        showRegionRuns(hit.code, hit.name)
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

    private fun showRegionRuns(code: String, name: String) {
        val session = Session(this)
        if (!ApiConfig.enabled || session.userId == null) return
        ApiClient(session).getMyRunsInRegion(code) { result ->
            runOnUiThread {
                result.onSuccess { arr ->
                    val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val fmt = java.text.SimpleDateFormat("M/d HH:mm", Locale.KOREA)
                    val labels = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        val d = runCatching { iso.parse(o.optString("started_at").take(19)) }.getOrNull()
                        val badge = if (o.optString("visibility") == "public") "🔓" else "🔒"
                        "${d?.let { fmt.format(it) } ?: "-"} · " +
                            "${TrackActivity.formatKm(o.optDouble("distance_m"))} · " +
                            "${TrackActivity.formatDuration(o.optLong("duration_ms"))}  $badge"
                    }
                    val b = android.app.AlertDialog.Builder(this, R.style.RundexDialog)
                        .setTitle("📍 $name")
                        .setPositiveButton("닫기", null)
                    if (labels.isEmpty()) b.setMessage("아직 이 동네에서 기록한 러닝이 없어요.")
                    else b.setItems(labels.toTypedArray(), null)
                    b.show()
                }.onFailure {
                    android.widget.Toast.makeText(this, "기록을 불러올 수 없어요", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            regions.add(DexRegion(o.optString("region_code"), o.optString("name"), outerRings(geomObj)))
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
