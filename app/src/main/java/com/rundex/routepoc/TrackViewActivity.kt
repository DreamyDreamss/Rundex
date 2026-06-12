package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
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

/** 저장된 기록 1개를 지도 궤적 + 측정값으로 보여주는 읽기 전용 화면 */
class TrackViewActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_track_view)

        val trackId = intent.getStringExtra("trackId")!!
        val track = TrackStore(File(filesDir, "tracks")).load(trackId)

        findViewById<TextView>(R.id.viewStatsText).text =
            "${TrackActivity.formatKm(track.distanceMeters)} · " +
            "${TrackActivity.formatDuration(track.durationMs)} · " +
            TrackActivity.formatPace(track.distanceMeters, track.durationMs)

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
                source.setGeoJson(
                    LineString.fromLngLats(track.points.map { Point.fromLngLat(it.lon, it.lat) })
                )
                val bounds = LatLngBounds.Builder()
                track.points.forEach { bounds.include(LatLng(it.lat, it.lon)) }
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            }
        }
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
