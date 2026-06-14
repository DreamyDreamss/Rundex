package com.rundex.routepoc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
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
import java.util.Locale

// AppCompat 의존성 없이 플랫폼 Activity 사용 (res 최소화)
class MainActivity : Activity() {

    private lateinit var mapView: MapView
    private lateinit var statDistanceValue: TextView
    private lateinit var statTimeValue: TextView
    private lateinit var statRegionValue: TextView
    private lateinit var snapIcon: ImageView
    private lateinit var snapLabel: TextView
    private var map: MapLibreMap? = null
    private var routeSource: GeoJsonSource? = null
    private var waypointSource: GeoJsonSource? = null
    private var meSource: GeoJsonSource? = null
    private var searchSource: GeoJsonSource? = null

    // 주소·장소 검색
    private lateinit var searchInput: EditText
    private lateinit var searchClear: ImageView
    private lateinit var suggestionList: ListView
    private val geocoding = GeocodingClient()
    private val suggestionAdapter = SuggestionAdapter()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var suppressWatcher = false

    private val state = RouteState()
    private val routing = RoutingClient()
    private val elevation = ElevationClient()
    private var requestInFlight = false
    private var snapToRoad = true

    // 경로계획 예상 상승고도(best-effort, 비동기) — 마지막 조회 결과 + 디바운스
    private var planGainM: Int? = null
    private val elevHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var elevRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)
        NavBar.setup(this, R.id.navRun)
        RunSegment.setup(this, RunSegment.Tab.PLAN)

        statDistanceValue = findViewById(R.id.statDistanceValue)
        statTimeValue = findViewById(R.id.statTimeValue)
        statRegionValue = findViewById(R.id.statRegionValue)
        snapIcon = findViewById(R.id.snapIcon)
        snapLabel = findViewById(R.id.snapLabel)

        findViewById<View>(R.id.snapAction).setOnClickListener { toggleSnap() }
        findViewById<View>(R.id.undoAction).setOnClickListener {
            if (!requestInFlight) {
                state.undo()
                redraw()
            }
        }
        findViewById<View>(R.id.returnAction).setOnClickListener { returnToStart() }
        findViewById<View>(R.id.resetAction).setOnClickListener { confirmReset() }
        findViewById<ImageView>(R.id.myLocationButton).setOnClickListener { goToMyLocation() }
        findViewById<Button>(R.id.runRouteButton).setOnClickListener { saveCourse() }
        setUpSearch()

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
        searchSource = GeoJsonSource("search-src").also(style::addSource)
        style.addLayer(
            CircleLayer("search-layer", "search-src").withProperties(
                circleRadius(9f),
                circleColor("#FF5A2D"),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(3f),
            )
        )
    }

    // ── 주소·장소 검색 (계획에서 위치 찾기) ──────────────────────────
    private fun setUpSearch() {
        searchInput = findViewById(R.id.searchInput)
        searchClear = findViewById(R.id.searchClear)
        suggestionList = findViewById(R.id.searchSuggestions)
        suggestionList.adapter = suggestionAdapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return
                val q = s?.toString()?.trim().orEmpty()
                searchClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                scheduleSearch(q)
            }
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                pendingSearch?.let(searchHandler::removeCallbacks)
                runSearch(searchInput.text.toString().trim())
                true
            } else false
        }
        searchClear.setOnClickListener {
            searchInput.setText("")
            hideSuggestions()
        }
        suggestionList.setOnItemClickListener { _, _, position, _ ->
            moveToPlace(suggestionAdapter.getItem(position))
        }
    }

    /** 입력 디바운스(350ms) 후 검색 — 2자 미만이면 추천 숨김 */
    private fun scheduleSearch(query: String) {
        pendingSearch?.let(searchHandler::removeCallbacks)
        if (query.length < 2) { hideSuggestions(); return }
        val task = Runnable { runSearch(query) }
        pendingSearch = task
        searchHandler.postDelayed(task, 350L)
    }

    private fun runSearch(query: String) {
        if (query.length < 2) return
        geocoding.search(query) { result ->
            runOnUiThread {
                result.onSuccess { places ->
                    if (places.isEmpty()) {
                        hideSuggestions()
                        Toast.makeText(this, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
                    } else {
                        suggestionAdapter.submit(places)
                        suggestionList.visibility = View.VISIBLE
                    }
                }.onFailure {
                    Toast.makeText(this, "검색 실패: 네트워크를 확인하세요", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 선택한 장소로 카메라 이동 + 마커 표시, 키보드·추천 닫기 */
    private fun moveToPlace(place: GeoPlace) {
        searchSource?.setGeoJson(Point.fromLngLat(place.lon, place.lat))
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 16.0))
        suppressWatcher = true
        searchInput.setText(place.name)
        searchInput.setSelection(place.name.length)
        suppressWatcher = false
        hideSuggestions()
        hideKeyboard()
    }

    private fun hideSuggestions() {
        suggestionAdapter.submit(emptyList())
        suggestionList.visibility = View.GONE
    }

    private fun hideKeyboard() {
        searchInput.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    /** 검색 추천 목록 어댑터 (📍 대표 이름 + 전체 주소) */
    private inner class SuggestionAdapter : BaseAdapter() {
        private val items = ArrayList<GeoPlace>()
        fun submit(places: List<GeoPlace>) {
            items.clear(); items.addAll(places); notifyDataSetChanged()
        }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.row_suggestion, parent, false)
            val place = items[position]
            view.findViewById<TextView>(R.id.suggestionName).text = place.name
            view.findViewById<TextView>(R.id.suggestionAddress).text = place.displayName
            return view
        }
    }

    /** 스냅 켜기/끄기 — 켜짐: 도로 따라 라우팅, 꺼짐: 직선 연결 */
    private fun toggleSnap() {
        snapToRoad = !snapToRoad
        snapLabel.text = if (snapToRoad) "스냅 켜짐" else "스냅 꺼짐"
        snapIcon.alpha = if (snapToRoad) 1.0f else 0.4f
    }

    private fun confirmReset() {
        if (state.waypoints.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("코스 초기화")
            .setMessage("작성 중인 코스를 모두 지울까요?")
            .setPositiveButton("초기화") { _, _ ->
                state.reset()
                redraw()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 마지막 점에서 출발지(첫 점)로 잇는 구간을 추가해 코스를 닫는다 */
    private fun returnToStart() {
        if (requestInFlight) return
        if (state.waypoints.size < 2) {
            Toast.makeText(this, "점을 2개 이상 찍어주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val from = state.waypoints.last()
        val start = state.waypoints.first()
        addSegment(from, start)
    }

    /** 완성된 코스를 저장하고, 바로 따라 뛸지 묻는다 */
    private fun saveCourse() {
        val path = state.fullPath()
        if (path.size < 2) {
            Toast.makeText(this, "코스를 먼저 완성하세요 (점 2개 이상)", Toast.LENGTH_SHORT).show()
            return
        }
        PlannedRouteStore(java.io.File(filesDir, "data"))
            .save(PlannedRoute(path, state.totalDistanceMeters()))
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("코스 저장됨")
            .setMessage("${formatKm(state.totalDistanceMeters())} 코스를 저장했어요.\n따라 뛰거나 추천 코스로 공유할 수 있어요.")
            .setPositiveButton("🏃 따라 뛰기") { _, _ ->
                startActivity(Intent(this, TrackActivity::class.java).putExtra("follow", true))
            }
            .setNeutralButton("📣 추천에 공유") { _, _ -> shareCoursePrompt(path) }
            .setNegativeButton("닫기", null)
            .show()
    }

    /** 계획 코스를 추천(공개) 코스로 등록 — 출발지 보호 후 업로드 */
    private fun shareCoursePrompt(path: List<LatLngPoint>) {
        if (!ApiConfig.enabled) {
            Toast.makeText(this, "서버 연결 후 공유할 수 있어요", Toast.LENGTH_SHORT).show(); return
        }
        val km = String.format(Locale.US, "%.1f", state.totalDistanceMeters() / 1000.0)
        val input = EditText(this).apply { hint = "코스 이름 (예: 한강 야경 ${km}km)" }
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("📣 추천 코스로 공유")
            .setMessage("다른 러너들이 이 코스를 불러와 달릴 수 있어요. (출발·도착 주변은 보호돼요)")
            .setView(input)
            .setPositiveButton("공유") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "내 코스 ${km}km" }
                val safe = RoutePrivacy.trimEnds(path)
                val ewkt = "SRID=4326;LINESTRING(" + safe.joinToString(", ") { "${it.lon} ${it.lat}" } + ")"
                val body = org.json.JSONObject()
                    .put("name", name).put("distance_m", state.totalDistanceMeters())
                    .put("difficulty", 2).put("geom", ewkt).put("is_public", true)
                ApiClient(Session(this)).createRoute(body) { r ->
                    runOnUiThread {
                        r.onSuccess { Toast.makeText(this, "추천 코스로 공유했어요! 러닝 탭 추천에서 볼 수 있어요", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(this, "공유 실패: ${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
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
        if (from == null) {
            state.addWaypoint(tapped)
            redraw()
            return // 첫 점은 구간 없음
        }
        addSegment(from, tapped)
    }

    /** from→to 구간을 추가 (스냅 켜짐이면 라우팅, 꺼짐이면 직선) */
    private fun addSegment(from: LatLngPoint, to: LatLngPoint) {
        state.addWaypoint(to)
        redraw()
        if (!snapToRoad) {
            state.addLeg(RouteStats.straightLeg(from, to))
            redraw()
            return
        }
        requestInFlight = true
        routing.fetchRoute(from, to) { result ->
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
        updateStats(path)
    }

    private fun updateStats(path: List<LatLngPoint>) {
        val meters = state.totalDistanceMeters()
        statDistanceValue.text = formatKm(meters)
        val mins = RouteStats.estimatedMinutes(meters)
        statTimeValue.text = planGainM?.let { "${mins}분 · ▲${it}m" } ?: "${mins}분"
        val index = RegionRepo.get(this)
        val regions = if (index != null) RouteStats.distinctRegionCount(path, index) else 0
        statRegionValue.text = "${regions}개 동"
        scheduleElevation(path)
    }

    /** 경로 변경 후 0.8초 디바운스로 예상 상승고도 조회(best-effort) */
    private fun scheduleElevation(path: List<LatLngPoint>) {
        elevRunnable?.let { elevHandler.removeCallbacks(it) }
        if (path.size < 2) { planGainM = null; return }
        val snapshot = path.toList()
        val r = Runnable {
            elevation.gainForRoute(snapshot) { result ->
                result.onSuccess { gain ->
                    runOnUiThread {
                        // 경로가 그대로일 때만 반영(오래된 응답 무시)
                        if (state.fullPath().size == snapshot.size) {
                            planGainM = gain.toInt()
                            statTimeValue.text = "${RouteStats.estimatedMinutes(state.totalDistanceMeters())}분 · ▲${planGainM}m"
                        }
                    }
                }
            }
        }
        elevRunnable = r
        elevHandler.postDelayed(r, 800L)
    }

    private fun formatKm(meters: Double): String =
        String.format(Locale.US, "%.1f km", meters / 1000.0)

    // MapView 수명주기 포워딩
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy(); searchHandler.removeCallbacksAndMessages(null) }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
