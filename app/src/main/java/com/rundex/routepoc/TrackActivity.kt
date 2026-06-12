package com.rundex.routepoc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackActivity : Activity(), TrackRecorder.Listener {

    private lateinit var mapView: MapView
    private lateinit var statsText: TextView
    private lateinit var startStopButton: Button
    private var map: MapLibreMap? = null
    private var trackSource: GeoJsonSource? = null
    private var planSource: GeoJsonSource? = null
    private var meSource: GeoJsonSource? = null
    private lateinit var store: TrackStore

    // 따라 뛰기 상태
    private var plannedRoute: PlannedRoute? = null
    private var offRoute = false
    private var lastWarnMs = 0L

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshStats()
            ticker.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_track)
        NavBar.setup(this, R.id.navRun)
        store = TrackStore(File(filesDir, "tracks"))

        statsText = findViewById(R.id.statsText)
        startStopButton = findViewById(R.id.startStopButton)
        startStopButton.setOnClickListener { onStartStop() }
        findViewById<Button>(R.id.historyButton).setOnClickListener { showHistory() }

        if (intent.getBooleanExtra("follow", false)) {
            plannedRoute = PlannedRouteStore(File(filesDir, "data")).load()
            if (plannedRoute == null) {
                Toast.makeText(this, "계획 경로를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        mapView = findViewById(R.id.trackMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(37.5512, 126.9882)).zoom(13.0).build()
            m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                // 계획 경로(회색)를 먼저 깔고 그 위에 내 궤적(파랑), 위치 점 순서로
                planSource = GeoJsonSource("plan-src").also(style::addSource)
                style.addLayer(
                    LineLayer("plan-layer", "plan-src").withProperties(
                        lineColor("#9E9E9E"), lineWidth(7f)
                    )
                )
                trackSource = GeoJsonSource("track-src").also(style::addSource)
                style.addLayer(
                    LineLayer("track-layer", "track-src").withProperties(
                        lineColor("#007AFF"), lineWidth(5f)
                    )
                )
                meSource = GeoJsonSource("me-src").also(style::addSource)
                style.addLayer(
                    CircleLayer("me-layer", "me-src").withProperties(
                        circleRadius(8f),
                        circleColor("#34C759"),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(3f),
                    )
                )
                redrawTrack()
                plannedRoute?.let { plan ->
                    planSource?.setGeoJson(
                        LineString.fromLngLats(plan.points.map { Point.fromLngLat(it.lon, it.lat) })
                    )
                    m.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(plan.points.first().lat, plan.points.first().lon))
                        .zoom(15.0).build()
                }
            }
        }
        updateButton()
    }

    private fun onStartStop() {
        if (TrackRecorder.recording) {
            stopService(Intent(this, TrackingService::class.java))
            val track = TrackRecorder.stop(System.currentTimeMillis())
            if (track.points.size >= 2) {
                store.save(track)
                applyRunToDex(track)
            } else {
                Toast.makeText(this, "좌표가 부족해 저장하지 않음", Toast.LENGTH_LONG).show()
            }
        } else {
            if (!hasPermissions()) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ), 1
                )
                return
            }
            TrackRecorder.start(System.currentTimeMillis())
            startForegroundService(Intent(this, TrackingService::class.java))
        }
        updateButton()
        refreshStats()
    }

    /** 러닝 결과를 도감·칭호에 반영하고 결과 다이얼로그 표시 */
    private fun applyRunToDex(track: SavedTrack) {
        val dataDir = File(filesDir, "data")
        val index = RegionRepo.get(this)
        var result: ApplyResult? = null
        var newTitles: List<TitleDef> = emptyList()
        if (index != null) {
            val byRegion = RunProcessor.distanceByRegion(track.points, index)
            val names = index.regions.associate { it.code to it.name }
            val dex = DexStore(dataDir)
            result = dex.applyRun(byRegion, names, track.distanceMeters, track.startedAtMs)
            val hour = java.util.Calendar.getInstance()
                .apply { timeInMillis = track.startedAtMs }
                .get(java.util.Calendar.HOUR_OF_DAY)
            val ctx = TitleContext(
                discoveredCount = dex.discoveredCount(),
                lifetimeMeters = dex.lifetimeMeters(),
                anyBronze = dex.bestGradeReached(Grade.BRONZE),
                anyGold = dex.bestGradeReached(Grade.GOLD),
                runStartHour = hour,
            )
            newTitles = TitleEngine.evaluate(ctx, TitleStore(dataDir), System.currentTimeMillis())
        }

        val sb = StringBuilder()
        sb.append("${formatKm(track.distanceMeters)} · ${formatDuration(track.durationMs)} 기록 저장됨")
        if (index == null) {
            sb.append("\n\n도감 데이터를 불러올 수 없어 도감 반영을 건너뜀")
        } else if (result != null) {
            if (result.newRegions.isNotEmpty()) {
                sb.append("\n\n🗺 새 동 ${result.newRegions.size}곳 발견!")
                sb.append("\n${result.newRegions.joinToString(", ") { it.name }}")
            }
            result.gradeUps.forEach {
                sb.append("\n⬆ ${it.name} ${it.from.label} → ${it.to.label} 승급!")
            }
            if (result.newRegions.isEmpty() && result.gradeUps.isEmpty()) {
                sb.append("\n\n도감 변동 없음 (서울 밖이거나 기존 지역)")
            }
        }
        newTitles.forEach { sb.append("\n🏅 칭호 획득: [${it.name}]") }

        AlertDialog.Builder(this)
            .setTitle("러닝 완료")
            .setMessage(sb.toString())
            .setPositiveButton("확인", null)
            .setNeutralButton("도감 보기") { _, _ ->
                startActivity(Intent(this, DexActivity::class.java))
            }
            .show()
    }

    private fun hasPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (hasPermissions()) onStartStop()
        else Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_LONG).show()
    }

    private fun showHistory() {
        val summaries = store.list()
        if (summaries.isEmpty()) {
            Toast.makeText(this, "저장된 기록 없음", Toast.LENGTH_SHORT).show()
            return
        }
        val fmt = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
        val labels = summaries.map {
            "${fmt.format(Date(it.startedAtMs))} · ${formatKm(it.distanceMeters)} · ${formatDuration(it.durationMs)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("저장된 기록")
            .setItems(labels) { _, i ->
                startActivity(
                    Intent(this, TrackViewActivity::class.java)
                        .putExtra("trackId", summaries[i].id)
                )
            }
            .show()
    }

    // TrackRecorder.Listener — Service 콜백(메인 루퍼)에서 호출됨
    override fun onUpdate() {
        runOnUiThread {
            updateMeAndCamera()
            checkDeviation()
            refreshStats()
            redrawTrack()
        }
    }

    /** 최신 수신 픽스(필터 무관)로 위치 점 갱신 + 기록 중이면 카메라 팔로우 */
    private fun updateMeAndCamera() {
        val lat = TrackRecorder.lastRawLat
        val lon = TrackRecorder.lastRawLon
        if (lat.isNaN()) return
        meSource?.setGeoJson(Point.fromLngLat(lon, lat))
        val m = map ?: return
        if (TrackRecorder.recording) {
            if (m.cameraPosition.zoom < 14.0) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16.0))
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)))
            }
        }
    }

    /** 따라 뛰기 중 경로 이탈(40m 초과) 감지 — 진동은 10초 쿨다운 */
    private fun checkDeviation() {
        val plan = plannedRoute ?: return
        if (!TrackRecorder.recording || TrackRecorder.lastRawLat.isNaN()) return
        val d = RouteDeviation.minDistanceMeters(
            TrackRecorder.lastRawLat, TrackRecorder.lastRawLon, plan.points
        )
        val nowOff = d > OFF_ROUTE_M
        if (nowOff && System.currentTimeMillis() - lastWarnMs > WARN_COOLDOWN_MS) {
            lastWarnMs = System.currentTimeMillis()
            vibrate()
        }
        offRoute = nowOff
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun refreshStats() {
        if (TrackRecorder.recording) {
            val now = System.currentTimeMillis()
            val elapsed = now - TrackRecorder.startedAtMs
            val dist = TrackRecorder.distanceMeters
            val currentPace = RunStats.currentPaceSecPerKm(TrackRecorder.points, now)
            val splits = RunStats.splitsMs(TrackRecorder.points)
            val sb = StringBuilder()
            if (offRoute) sb.append("⚠ 경로 이탈!\n")
            sb.append("${formatKm(dist)} · ${formatDuration(elapsed)} · 평균 ${formatPace(dist, elapsed)}")
            sb.append("\n현재 ")
            sb.append(currentPace?.let { "${RunStats.formatPaceSec(it)}/km" } ?: "-'--\"/km")
            sb.append(" · ${String.format(Locale.US, "%.1f", RunStats.avgSpeedKmh(dist, elapsed))} km/h")
            sb.append(" · ${RunStats.calorieKcal(dist).toInt()} kcal")
            if (splits.isNotEmpty()) {
                sb.append("\n랩 ${splits.size}km ${RunStats.formatPaceSec(splits.last() / 1000.0)}")
            }
            statsText.text = sb.toString()
        } else if (TrackRecorder.points.isEmpty()) {
            statsText.text = "기록 대기 중"
        }
    }

    private fun redrawTrack() {
        val pts = TrackRecorder.points
        if (pts.size >= 2) {
            trackSource?.setGeoJson(
                LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })
            )
        } else {
            trackSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    private fun updateButton() {
        startStopButton.text = if (TrackRecorder.recording) "기록 종료" else "기록 시작"
    }

    override fun onStart() {
        super.onStart(); mapView.onStart()
        TrackRecorder.addListener(this)
        ticker.post(tick)
    }

    override fun onStop() {
        super.onStop(); mapView.onStop()
        TrackRecorder.removeListener(this)
        ticker.removeCallbacks(tick)
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    companion object {
        private const val OFF_ROUTE_M = 40.0
        private const val WARN_COOLDOWN_MS = 10_000L

        fun formatKm(meters: Double): String = String.format(Locale.US, "%.2f km", meters / 1000.0)

        fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format(Locale.US, "%d:%02d", m, s)
        }

        /** 평균 페이스 min/km. 50m 미만이면 "-" */
        fun formatPace(meters: Double, ms: Long): String {
            if (meters < 50.0) return "-'--\"/km"
            val paceSecPerKm = (ms / 1000.0) / (meters / 1000.0)
            val m = (paceSecPerKm / 60).toInt()
            val s = (paceSecPerKm % 60).toInt()
            return String.format(Locale.US, "%d'%02d\"/km", m, s)
        }
    }
}
