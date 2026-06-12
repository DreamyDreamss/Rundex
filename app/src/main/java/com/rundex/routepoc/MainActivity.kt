package com.rundex.routepoc

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// AppCompat 의존성 없이 플랫폼 Activity 사용 (res 최소화)
class MainActivity : Activity() {

    private lateinit var mapView: MapView
    private lateinit var distanceText: TextView
    private var map: MapLibreMap? = null
    private var routeSource: GeoJsonSource? = null
    private var waypointSource: GeoJsonSource? = null
    private var meSource: GeoJsonSource? = null

    private val state = RouteState()
    private val routing = RoutingClient()
    private var requestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        distanceText = findViewById(R.id.distanceText)
        findViewById<Button>(R.id.undoButton).setOnClickListener {
            if (!requestInFlight) {
                state.undo()
                redraw()
            }
        }
        findViewById<Button>(R.id.myLocationButton).setOnClickListener { goToMyLocation() }

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(37.5512, 126.9882)) // 서울 남산 인근
                .zoom(13.0)
                .build()
            m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setUpLayers(style)
                m.addOnMapClickListener { latLng ->
                    onMapTap(latLng)
                    true
                }
            }
        }
    }

    private fun setUpLayers(style: Style) {
        routeSource = GeoJsonSource("route-src").also(style::addSource)
        waypointSource = GeoJsonSource("waypoint-src").also(style::addSource)
        style.addLayer(
            LineLayer("route-layer", "route-src").withProperties(
                lineColor("#FF3B30"),
                lineWidth(4f),
            )
        )
        style.addLayer(
            CircleLayer("waypoint-layer", "waypoint-src").withProperties(
                circleRadius(6f),
                circleColor("#007AFF"),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(2f),
            )
        )
        meSource = GeoJsonSource("me-src").also(style::addSource)
        style.addLayer(
            CircleLayer("me-layer", "me-src").withProperties(
                circleRadius(7f),
                circleColor("#34C759"),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(2f),
            )
        )
    }

    private fun goToMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ), 2
            )
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    Toast.makeText(this, "위치를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                meSource?.setGeoJson(Point.fromLngLat(loc.longitude, loc.latitude))
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0)
                )
            }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 2 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            goToMyLocation()
        }
    }

    private fun onMapTap(latLng: LatLng) {
        if (requestInFlight) return // 응답 대기 중 중복 탭 무시
        val tapped = LatLngPoint(latLng.latitude, latLng.longitude)
        val from = state.waypoints.lastOrNull()
        state.addWaypoint(tapped)
        redraw()
        if (from == null) return // 첫 점은 라우팅 없음

        requestInFlight = true
        routing.fetchRoute(from, tapped) { result ->
            runOnUiThread {
                requestInFlight = false
                result.onSuccess { leg ->
                    state.addLeg(leg)
                    redraw()
                }.onFailure { e ->
                    state.undo() // 방금 추가한 점 롤백
                    redraw()
                    Toast.makeText(this, "경로 탐색 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun redraw() {
        waypointSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                state.waypoints.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
            )
        )
        val path = state.fullPath()
        if (path.size >= 2) {
            routeSource?.setGeoJson(
                LineString.fromLngLats(path.map { Point.fromLngLat(it.lon, it.lat) })
            )
        } else {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
        val km = state.totalDistanceMeters() / 1000.0
        distanceText.text = if (state.waypoints.isEmpty()) {
            "지도를 탭해 경로를 만드세요"
        } else {
            String.format("총 거리 %.2f km", km)
        }
    }

    // MapView 수명주기 포워딩
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
