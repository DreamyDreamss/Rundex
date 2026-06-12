package com.rundex.routepoc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
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
    private var trackSource: GeoJsonSource? = null
    private lateinit var store: TrackStore

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
        store = TrackStore(File(filesDir, "tracks"))

        statsText = findViewById(R.id.statsText)
        startStopButton = findViewById(R.id.startStopButton)
        startStopButton.setOnClickListener { onStartStop() }
        findViewById<Button>(R.id.historyButton).setOnClickListener { showHistory() }

        mapView = findViewById(R.id.trackMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(37.5512, 126.9882)).zoom(13.0).build()
            m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                trackSource = GeoJsonSource("track-src").also(style::addSource)
                style.addLayer(
                    LineLayer("track-layer", "track-src").withProperties(
                        lineColor("#007AFF"), lineWidth(5f)
                    )
                )
                redrawTrack()
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
                Toast.makeText(this, "기록 저장됨 (${formatKm(track.distanceMeters)})", Toast.LENGTH_LONG).show()
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
            refreshStats()
            redrawTrack()
        }
    }

    private fun refreshStats() {
        if (TrackRecorder.recording) {
            val elapsed = System.currentTimeMillis() - TrackRecorder.startedAtMs
            statsText.text =
                "${formatKm(TrackRecorder.distanceMeters)} · ${formatDuration(elapsed)} · ${formatPace(TrackRecorder.distanceMeters, elapsed)}"
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
