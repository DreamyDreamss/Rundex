package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.util.Locale

/** 저장된 기록 1개를 지도 궤적 + 측정값으로 보여주는 읽기 전용 화면 */
class TrackViewActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_track_view)

        val serverGeo = intent.getStringExtra("serverGeoJson")
        // 경로 좌표([lon,lat])와 통계를 로컬/서버 중 하나에서 구성
        val pts: List<DoubleArray>
        if (serverGeo != null) {
            pts = parseLonLat(serverGeo)
            val dist = intent.getDoubleExtra("distanceM", 0.0)
            val dur = intent.getLongExtra("durationMs", 0L)
            val cap = intent.getStringExtra("caption").orEmpty()
            val sb = StringBuilder()
            if (cap.isNotBlank()) sb.append("“$cap”\n\n")
            sb.append("${TrackActivity.formatKm(dist)} · ${TrackActivity.formatDuration(dur)} · ")
            sb.append("평균 ${TrackActivity.formatPace(dist, dur)}")
            sb.append("\n${String.format(Locale.US, "%.1f", RunStats.avgSpeedKmh(dist, dur))} km/h")
            sb.append(" · ${RunStats.calorieKcal(dist).toInt()} kcal")
            findViewById<TextView>(R.id.viewStatsText).text = sb.toString()
            // 서버 런: 포인트별 타임스탬프가 없어 스플릿/GPX/스토리카드는 비활성
            findViewById<Button>(R.id.shareCardButton).visibility = android.view.View.GONE
            findViewById<Button>(R.id.exportGpxButton).visibility = android.view.View.GONE
            // 대신 '이 코스 따라 뛰기' 제공 — 이 경로를 내 계획으로 저장하고 러닝 시작
            findViewById<Button>(R.id.followCourseButton).apply {
                visibility = android.view.View.VISIBLE
                setOnClickListener {
                    val ll = pts.map { LatLngPoint(it[1], it[0]) }
                    if (ll.size < 2) return@setOnClickListener
                    PlannedRouteStore(File(filesDir, "data"))
                        .save(PlannedRoute(ll, intent.getDoubleExtra("distanceM", 0.0)))
                    Toast.makeText(this@TrackViewActivity, "이 코스를 불러왔어요! 러닝 시작에서 따라 달려요", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@TrackViewActivity, TrackActivity::class.java).putExtra("follow", true))
                }
            }
        } else {
            val track = TrackStore(File(filesDir, "tracks")).load(intent.getStringExtra("trackId")!!)
            pts = track.points.map { doubleArrayOf(it.lon, it.lat) }
            val sb = StringBuilder()
            sb.append("${TrackActivity.formatKm(track.distanceMeters)} · ")
            sb.append("${TrackActivity.formatDuration(track.durationMs)} · ")
            sb.append("평균 ${TrackActivity.formatPace(track.distanceMeters, track.durationMs)}")
            sb.append("\n${String.format(Locale.US, "%.1f", RunStats.avgSpeedKmh(track.distanceMeters, track.durationMs))} km/h")
            sb.append(" · ${RunStats.calorieKcal(track.distanceMeters).toInt()} kcal")
            sb.append(" · ▲${track.elevationGainM.toInt()}m")
            RunStats.splitsMs(track.points).forEachIndexed { i, ms ->
                sb.append("\n${i + 1} km — ${RunStats.formatPaceSec(ms / 1000.0)}")
            }
            findViewById<TextView>(R.id.viewStatsText).text = sb.toString()
            findViewById<Button>(R.id.shareCardButton).setOnClickListener { shareStoryCard(track) }
            findViewById<Button>(R.id.exportGpxButton).setOnClickListener { exportGpx(track) }
        }

        mapView = findViewById(R.id.viewMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                val source = GeoJsonSource("saved-src").also(style::addSource)
                style.addLayer(
                    LineLayer("saved-layer", "saved-src").withProperties(
                        lineColor("#FF3B30"), lineWidth(5f)
                    )
                )
                if (pts.size >= 2) {
                    source.setGeoJson(LineString.fromLngLats(pts.map { Point.fromLngLat(it[0], it[1]) }))
                    val bounds = LatLngBounds.Builder()
                    pts.forEach { bounds.include(LatLng(it[1], it[0])) }
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                }
            }
        }
    }

    private fun parseLonLat(geojson: String): List<DoubleArray> = runCatching {
        val coords = org.json.JSONObject(geojson).getJSONArray("coordinates")
        (0 until coords.length()).map {
            val c = coords.getJSONArray(it); doubleArrayOf(c.getDouble(0), c.getDouble(1))
        }
    }.getOrDefault(emptyList())

    private fun exportGpx(track: SavedTrack) {
        val dir = File(cacheDir, "gpx").apply { mkdirs() }
        val f = File(dir, "run-${track.id}.gpx")
        f.writeText(GpxExporter.toGpx(track))
        val uri = FileProvider.getUriForFile(this, "com.rundex.routepoc.fileprovider", f)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "GPX 공유"
            )
        )
    }

    /** 경로+측정값을 이미지 카드로 렌더해 공유(인스타 스토리 등) */
    private fun shareStoryCard(track: SavedTrack) {
        val dataDir = File(filesDir, "data")
        val owned = TitleStore(dataDir).owned()
        val repId = getSharedPreferences("profile", MODE_PRIVATE).getString("rep_title_id", null)
            ?.takeIf { owned.containsKey(it) } ?: owned.maxByOrNull { it.value }?.key
        val repTitle = repId?.let { id -> Titles.all.firstOrNull { it.id == id }?.name }
        val discovered = DexStore(dataDir).discoveredCount()

        // 스토리 카드(그라데이션 배경 + 경로/수치/배지). 합성은 가벼워 동기 렌더.
        shareBitmap(ShareCardRenderer.render(track, repTitle, discovered), track.id)
    }

    private fun shareBitmap(bmp: android.graphics.Bitmap, id: String) {
        val dir = File(cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "card-$id.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(this, "com.rundex.routepoc.fileprovider", f)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "스토리 공유"
            )
        )
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
