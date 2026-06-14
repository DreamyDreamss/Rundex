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
        findViewById<TextView>(R.id.viewBack).setOnClickListener { finish() }

        val serverGeo = intent.getStringExtra("serverGeoJson")
        // 경로 좌표([lon,lat])와 통계를 로컬/서버 중 하나에서 구성
        val pts: List<DoubleArray>
        if (serverGeo != null) {
            pts = parseLonLat(serverGeo)
            val dist = intent.getDoubleExtra("distanceM", 0.0)
            val dur = intent.getLongExtra("durationMs", 0L)
            val cap = intent.getStringExtra("caption").orEmpty()
            val tags = intent.getStringExtra("tags").orEmpty()
            val vis = intent.getStringExtra("visibility")
            val regionCount = intent.getIntExtra("regionCount", -1)
            val startedIso = intent.getStringExtra("startedAtIso")

            fillStats(
                dist = dist, dur = dur,
                dateLabel = startedIso?.let { isoToLabel(it) },
                caption = cap, tags = tags, visibility = vis,
                elevText = null,                       // 서버 런은 고도 데이터 없음
                regionsText = if (regionCount >= 0) "${regionCount}곳" else null,
            )
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
            // 🔖 코스 보관함에 저장 — 이 경로를 내 코스(비공개)로 만들고 북마크
            findViewById<Button>(R.id.bookmarkCourseButton).apply {
                visibility = android.view.View.VISIBLE
                setOnClickListener { bookmarkCourse(pts) }
            }
        } else {
            val track = TrackStore(File(filesDir, "tracks")).load(intent.getStringExtra("trackId")!!)
            pts = track.points.map { doubleArrayOf(it.lon, it.lat) }
            fillStats(
                dist = track.distanceMeters, dur = track.durationMs,
                dateLabel = msToLabel(track.startedAtMs),
                caption = "", tags = "", visibility = null,
                elevText = "${track.elevationGainM.toInt()} m",
                regionsText = null,
            )
            renderSplits(RunStats.splitsMs(track.points))
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

    /** 상세 카드 채우기 — 거리 헤드라인 + 컬러 스탯 그리드 + 캡션/태그/배지 */
    private fun fillStats(
        dist: Double, dur: Long, dateLabel: String?,
        caption: String, tags: String, visibility: String?,
        elevText: String?, regionsText: String?,
    ) {
        findViewById<TextView>(R.id.viewDistance).text = TrackActivity.formatKm(dist)
        findViewById<TextView>(R.id.viewDate).text = dateLabel ?: ""

        findViewById<TextView>(R.id.stTime).text = TrackActivity.formatDuration(dur)
        findViewById<TextView>(R.id.stPace).text = TrackActivity.formatPace(dist, dur)
        findViewById<TextView>(R.id.stSpeed).text =
            "${String.format(Locale.US, "%.1f", RunStats.avgSpeedKmh(dist, dur))} km/h"
        findViewById<TextView>(R.id.stKcal).text = "${RunStats.calorieKcal(dist).toInt()} kcal"
        findViewById<TextView>(R.id.stElev).text = elevText ?: "—"
        findViewById<TextView>(R.id.stRegions).text = regionsText ?: "—"

        findViewById<TextView>(R.id.viewCaption).apply {
            text = caption
            setVisibility(if (caption.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
        }
        findViewById<TextView>(R.id.viewTags).apply {
            text = tags
            setVisibility(if (tags.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
        }
        findViewById<TextView>(R.id.viewBadge).apply {
            if (visibility == null) { setVisibility(android.view.View.GONE) }
            else {
                text = if (visibility == "public") "🔓 공개" else "🔒 비공개"
                setVisibility(android.view.View.VISIBLE)
            }
        }
    }

    /** 1km 구간 페이스 표 — 가장 빠른 구간은 강조 */
    private fun renderSplits(splits: List<Long>) {
        if (splits.isEmpty()) return
        val card = findViewById<android.view.View>(R.id.splitsCard)
        val box = findViewById<android.widget.LinearLayout>(R.id.splitsContainer)
        card.visibility = android.view.View.VISIBLE
        val fastest = splits.indices.minByOrNull { splits[it] } ?: -1
        val maxMs = splits.maxOrNull()?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
        splits.forEachIndexed { i, ms ->
            val row = layoutInflater.inflate(R.layout.row_split, box, false)
            val hot = i == fastest
            row.findViewById<TextView>(R.id.splitIdx).text = "${i + 1}"
            row.findViewById<TextView>(R.id.splitPace).text = RunStats.formatPaceSec(ms / 1000.0)
            val bar = row.findViewById<android.view.View>(R.id.splitBar)
            bar.layoutParams = (bar.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                weight = (ms / maxMs).toFloat().coerceIn(0.08f, 1f)
            }
            bar.setBackgroundColor(getColor(if (hot) R.color.primary else R.color.gradeCard))
            if (hot) {
                row.findViewById<TextView>(R.id.splitIdx).setTextColor(getColor(R.color.primary))
                row.findViewById<TextView>(R.id.splitPace).setTextColor(getColor(R.color.primary))
            }
            box.addView(row)
        }
    }

    private val isoFmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val labelFmt = java.text.SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREA)
    private fun isoToLabel(iso: String): String? =
        runCatching { isoFmt.parse(iso.take(19))?.let { labelFmt.format(it) } }.getOrNull()
    private fun msToLabel(ms: Long): String = labelFmt.format(java.util.Date(ms))

    /** 피드/타인의 경로를 내 코스(비공개)로 만들어 코스 보관함에 북마크 */
    private fun bookmarkCourse(pts: List<DoubleArray>) {
        val session = Session(this)
        val uid = session.userId
        if (!ApiConfig.enabled || uid == null) {
            Toast.makeText(this, "서버 연결 후 사용할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        if (pts.size < 2) { Toast.makeText(this, "경로가 없어요", Toast.LENGTH_SHORT).show(); return }
        val dist = intent.getDoubleExtra("distanceM", 0.0)
        val km = String.format(Locale.US, "%.1f", dist / 1000.0)
        val cap = intent.getStringExtra("caption").orEmpty().takeIf { it.isNotBlank() }
        val name = cap ?: "저장한 코스 ${km}km"
        val ewkt = "SRID=4326;LINESTRING(" + pts.joinToString(", ") { "${it[0]} ${it[1]}" } + ")"
        val body = org.json.JSONObject()
            .put("name", name).put("distance_m", dist)
            .put("difficulty", 2).put("geom", ewkt).put("is_public", false)
        val api = ApiClient(session)
        api.createRoute(body) { r ->
            r.onSuccess { route ->
                val rid = route.optString("id")
                api.addBookmark(uid, rid) {
                    runOnUiThread {
                        Toast.makeText(this, "코스 보관함에 저장했어요 🔖", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                runOnUiThread { Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show() }
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
