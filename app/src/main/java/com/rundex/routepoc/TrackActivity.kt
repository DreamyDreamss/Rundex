package com.rundex.routepoc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    private var map: MapLibreMap? = null
    private var trackSource: GeoJsonSource? = null
    private var planSource: GeoJsonSource? = null
    private var meSource: GeoJsonSource? = null
    private lateinit var store: TrackStore

    // 러닝 화면 상태 뷰
    private lateinit var bigStartButton: TextView
    private lateinit var runStatsOverlay: View
    private lateinit var countdownOverlay: View
    private lateinit var countdownText: TextView
    private lateinit var runDistance: TextView
    private lateinit var runPace: TextView
    private lateinit var runKcal: TextView
    private lateinit var runTime: TextView
    private lateinit var runMapBar: View
    private var runMapMode = false
    private var counting = false
    private val recoRoutes = ArrayList<org.json.JSONObject>()

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
        RunSegment.setup(this, RunSegment.Tab.RECORD)
        store = TrackStore(File(filesDir, "tracks"))

        bigStartButton = findViewById(R.id.bigStartButton)
        runStatsOverlay = findViewById(R.id.runStatsOverlay)
        countdownOverlay = findViewById(R.id.countdownOverlay)
        countdownText = findViewById(R.id.countdownText)
        runDistance = findViewById(R.id.runDistance)
        runPace = findViewById(R.id.runPace)
        runKcal = findViewById(R.id.runKcal)
        runTime = findViewById(R.id.runTime)
        runMapBar = findViewById(R.id.runMapBar)

        bigStartButton.setOnClickListener { onStartPressed() }
        findViewById<View>(R.id.pauseButton).setOnClickListener { togglePause() }
        findViewById<View>(R.id.resumeButton).setOnClickListener { togglePause() }
        findViewById<View>(R.id.runMapPause).setOnClickListener { togglePause() }
        findViewById<View>(R.id.stopButton).setOnClickListener { onStopPressed() }
        findViewById<View>(R.id.runMapStop).setOnClickListener { onStopPressed() }
        findViewById<View>(R.id.mapToggleButton).setOnClickListener { setRunMapMode(true) }
        findViewById<View>(R.id.statsToggleButton).setOnClickListener { setRunMapMode(false) }
        findViewById<View>(R.id.loadRouteButton).setOnClickListener { loadSavedRoute() }
        findViewById<View>(R.id.historyButton).setOnClickListener {
            // 서버(원천) 기록으로 통일 — 로컬 이 기기 기록만 보이던 문제 해결
            if (ApiConfig.enabled && Session(this).userId != null)
                startActivity(Intent(this, RunHistoryActivity::class.java))
            else showHistory()
        }
        findViewById<TextView>(R.id.goalChip).setOnClickListener { showGoalPicker() }
        updateGoalChip()
        loadRecommendedCards()
        maybeOfferRecovery()

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
                val plan = plannedRoute
                if (plan != null) {
                    planSource?.setGeoJson(
                        LineString.fromLngLats(plan.points.map { Point.fromLngLat(it.lon, it.lat) })
                    )
                    m.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(plan.points.first().lat, plan.points.first().lon))
                        .zoom(15.0).build()
                } else {
                    centerOnMyLocation()   // 계획 경로 없으면 내 위치를 지도 중앙에
                }
            }
        }
        setState()
    }

    /** 대기 화면에서 내 현재 위치를 지도 중앙에 포커스 + 위치 점 표시 */
    private fun centerOnMyLocation() {
        if (!hasPermissions()) return
        runCatching {
            com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(this).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc == null || TrackRecorder.recording) return@addOnSuccessListener
                    meSource?.setGeoJson(Point.fromLngLat(loc.longitude, loc.latitude))
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.5)
                    )
                }
        }
    }

    /** 시작 버튼 → 권한 확인 후 3·2·1 카운트다운 → 기록 시작 */
    private fun onStartPressed() {
        if (TrackRecorder.recording || counting) return
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
        runCountdown { startRecording() }
    }

    private fun startRecording() {
        maybePromptBatteryExemption()
        TrackRecorder.start(System.currentTimeMillis())
        startForegroundService(Intent(this, TrackingService::class.java))
        runMapMode = false
        setState()
        refreshStats()
    }

    /** 정지 → 저장 + 도감 반영 + 공개여부 다이얼로그 */
    private fun onStopPressed() {
        if (!TrackRecorder.recording) return
        stopService(Intent(this, TrackingService::class.java))
        ActiveRunStore(File(filesDir, "data")).clear()
        val track = TrackRecorder.stop(System.currentTimeMillis())
        if (track.points.size >= 2) {
            store.save(track)
            applyRunToDex(track)
        } else {
            Toast.makeText(this, "좌표가 부족해 저장하지 않음", Toast.LENGTH_LONG).show()
        }
        setState()
    }

    /** 3·2·1 카운트다운 애니메이션 후 콜백 — 매 카운트 햅틱, 마지막엔 GO! */
    private fun runCountdown(onDone: () -> Unit) {
        counting = true
        countdownOverlay.visibility = View.VISIBLE
        val sub = findViewById<TextView>(R.id.countdownSub)
        sub.text = "잠시 후 시작해요"
        val h = Handler(Looper.getMainLooper())
        fun tick(n: Int) {
            if (n == 0) {
                // GO! 한 박자 보여주고 시작
                countdownText.text = "GO!"
                countdownText.setTextColor(getColor(R.color.primary))
                sub.text = "출발 🏃"
                countdownText.scaleX = 0.5f; countdownText.scaleY = 0.5f; countdownText.alpha = 0f
                countdownText.animate().scaleX(1.15f).scaleY(1.15f).alpha(1f).setDuration(260).start()
                vibrate(120)
                h.postDelayed({
                    counting = false
                    countdownOverlay.visibility = View.GONE
                    countdownText.text = "3"   // 다음 사용 대비 초기화
                    onDone()
                }, 650)
                return
            }
            countdownText.text = n.toString()
            countdownText.scaleX = 0.4f; countdownText.scaleY = 0.4f; countdownText.alpha = 0f
            countdownText.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(380).start()
            vibrate(40)
            h.postDelayed({ tick(n - 1) }, 800)
        }
        tick(3)
    }

    private fun togglePause() {
        val now = System.currentTimeMillis()
        if (TrackRecorder.paused) TrackRecorder.resume(now) else TrackRecorder.pause(now)
        setState()
    }

    /** 러닝/대기 상태에 따라 화면 전환. 일시정지 시 지도+스탯 페이지로 자동 전환. */
    private fun setState() {
        val running = TrackRecorder.recording
        val paused = TrackRecorder.paused
        val showMap = running && (runMapMode || paused)   // 일시정지=지도 페이지
        findViewById<View>(R.id.idleTop).visibility = if (running) View.GONE else View.VISIBLE
        findViewById<View>(R.id.idleControls).visibility = if (running) View.GONE else View.VISIBLE
        findViewById<View>(R.id.goalChip).visibility = if (running) View.GONE else View.VISIBLE
        runStatsOverlay.visibility = if (running && !showMap) View.VISIBLE else View.GONE
        runMapBar.visibility = if (showMap) View.VISIBLE else View.GONE

        // 주황(거리) 화면: 러닝 중 ⏸ 일시정지만
        findViewById<View>(R.id.pauseButton).visibility = if (running && !paused) View.VISIBLE else View.GONE
        findViewById<View>(R.id.mapToggleButton).visibility = if (running && !paused) View.VISIBLE else View.GONE
        findViewById<View>(R.id.stopButton).visibility = View.GONE
        findViewById<View>(R.id.resumeButton).visibility = View.GONE

        // 지도 패널 컨트롤: ⏸/▶ 토글, 📊(거리뷰)은 일시정지 아닐 때만
        findViewById<TextView>(R.id.runMapPause).text = if (paused) "▶" else "⏸"
        findViewById<View>(R.id.statsToggleButton).visibility = if (paused) View.GONE else View.VISIBLE
    }

    private fun setRunMapMode(mapMode: Boolean) {
        runMapMode = mapMode
        setState()
    }

    // ── 러닝 목표 ───────────────────────────────────────────────
    private fun goalPrefs() = getSharedPreferences("profile", MODE_PRIVATE)
    private fun goalType() = goalPrefs().getString("goal_type", "none") ?: "none"
    private fun goalValue() = goalPrefs().getDouble("goal_value", 0.0)

    private fun SharedPreferences.getDouble(k: String, d: Double) =
        Double.fromBits(getLong(k, d.toRawBits()))

    private fun updateGoalChip() {
        val chip = findViewById<TextView>(R.id.goalChip)
        chip.text = when (goalType()) {
            "dist" -> "🎯 목표 ${String.format(Locale.US, "%.1f", goalValue() / 1000.0)}km"
            "time" -> "🎯 목표 ${(goalValue() / 60000).toInt()}분"
            else -> "🎯 목표 설정"
        }
    }

    private fun showGoalPicker() {
        val opts = arrayOf("거리 3km", "거리 5km", "거리 10km", "시간 20분", "시간 30분", "시간 60분", "목표 없음")
        AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle("🎯 러닝 목표")
            .setItems(opts) { _, i ->
                val e = goalPrefs().edit()
                when (i) {
                    0 -> e.putString("goal_type", "dist").putLong("goal_value", 3000.0.toRawBits())
                    1 -> e.putString("goal_type", "dist").putLong("goal_value", 5000.0.toRawBits())
                    2 -> e.putString("goal_type", "dist").putLong("goal_value", 10000.0.toRawBits())
                    3 -> e.putString("goal_type", "time").putLong("goal_value", (20 * 60000).toDouble().toRawBits())
                    4 -> e.putString("goal_type", "time").putLong("goal_value", (30 * 60000).toDouble().toRawBits())
                    5 -> e.putString("goal_type", "time").putLong("goal_value", (60 * 60000).toDouble().toRawBits())
                    else -> e.putString("goal_type", "none")
                }
                e.apply(); updateGoalChip(); refreshStats()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 인런 화면 목표 진행도 갱신 */
    private fun refreshGoal(distM: Double, elapsedMs: Long) {
        val box = findViewById<View>(R.id.goalProgress)
        val type = goalType()
        if (type == "none" || !TrackRecorder.recording) { box.visibility = View.GONE; return }
        box.visibility = View.VISIBLE
        val target = goalValue()
        val cur = if (type == "dist") distM else elapsedMs.toDouble()
        val pct = (cur / target).coerceIn(0.0, 1.0)
        findViewById<android.widget.ProgressBar>(R.id.goalProgressBar).progress = (pct * 1000).toInt()
        val txt = findViewById<TextView>(R.id.goalProgressText)
        txt.text = if (pct >= 1.0) "🎉 목표 달성!"
        else if (type == "dist") "목표 ${String.format(Locale.US, "%.1f", target / 1000.0)}km · ${(pct * 100).toInt()}%"
        else "목표 ${(target / 60000).toInt()}분 · ${(pct * 100).toInt()}%"
    }

    /** 비정상 종료로 남은 진행 기록이 있으면 이어서 기록할지/저장할지 제안 */
    private fun maybeOfferRecovery() {
        if (TrackRecorder.recording) return
        val activeStore = ActiveRunStore(File(filesDir, "data"))
        val run = activeStore.load() ?: return
        if (run.points.size < 2) { activeStore.clear(); return }
        AlertDialog.Builder(this)
            .setTitle("기록이 복구되었습니다")
            .setMessage("이전에 종료되지 않은 러닝(${formatKm(run.distanceMeters)})이 있습니다.")
            .setPositiveButton("이어서 기록") { _, _ ->
                TrackRecorder.restore(run)
                startForegroundService(Intent(this, TrackingService::class.java))
                setState(); refreshStats()
            }
            .setNeutralButton("저장하고 종료") { _, _ ->
                val track = SavedTrack(
                    id = run.startedAtMs.toString(),
                    startedAtMs = run.startedAtMs,
                    durationMs = (run.points.last().timeMs - run.startedAtMs).coerceAtLeast(0),
                    distanceMeters = run.distanceMeters,
                    points = run.points,
                    elevationGainM = run.elevationGainM,
                )
                store.save(track)
                activeStore.clear()
                applyRunToDex(track)
            }
            .setNegativeButton("삭제") { _, _ -> activeStore.clear() }
            .setCancelable(false)
            .show()
    }

    /** 배터리 최적화 예외를 한 번만 안내 — 백그라운드 추적 끊김 방지(기획서 §8.4 최대 난관) */
    private fun maybePromptBatteryExemption() {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompt_shown", false)) return
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        prefs.edit().putBoolean("battery_prompt_shown", true).apply()
        AlertDialog.Builder(this)
            .setTitle("백그라운드 기록 안정화")
            .setMessage("화면을 끄거나 다른 앱을 써도 러닝이 끊기지 않으려면 배터리 최적화 예외를 허용해 주세요. (특히 삼성 기기 권장)")
            .setPositiveButton("설정 열기") { _, _ ->
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName")))
                }.onFailure {
                    startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    /** 러닝 결과를 도감·칭호에 반영하고 결과 다이얼로그 표시 */
    private fun applyRunToDex(track: SavedTrack) {
        val dataDir = File(filesDir, "data")
        val index = RegionRepo.get(this)

        // 테마 도감 — 경로가 명소 반경을 지났는지 판정해 먼저 수집 반영
        // (테마 완성 칭호를 같은 러닝에서 평가하기 위해 도감/칭호보다 앞에 둔다)
        val path = track.points.map { LatLngPoint(it.lat, it.lon) }
        val themeStore = ThemeStore(dataDir)
        val collections = ThemeRepo.all(this)
        val themeDeltas = themeStore.applyRun(path, collections)
        val completedThemes = themeStore.completedSlugs(collections)

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
                anySilver = dex.bestGradeReached(Grade.SILVER),
                anyGold = dex.bestGradeReached(Grade.GOLD),
                runStartHour = hour,
                completedThemeSlugs = completedThemes,
            )
            newTitles = TitleEngine.evaluate(ctx, TitleStore(dataDir), System.currentTimeMillis())
        }

        // 성취 항목 구조화 (새 동 / 승급 / 칭호 / 테마)
        val ach = ArrayList<String>()
        if (index == null) {
            ach.add("⚠ 도감 데이터를 불러올 수 없어 반영을 건너뜀")
        } else result?.let {
            if (it.newRegions.isNotEmpty())
                ach.add("🗺 새 동 ${it.newRegions.size}곳 발견 — ${it.newRegions.joinToString(", ") { r -> r.name }}")
            it.gradeUps.forEach { g -> ach.add("⬆ ${g.name} ${g.from.label} → ${g.to.label} 승급!") }
        }
        newTitles.forEach { ach.add("🏅 칭호 획득 — ${it.name}") }
        themeDeltas.forEach { d ->
            ach.add("🌟 ${d.title} +${d.newPlaces.size} (${d.newPlaces.joinToString(", ") { it.name }})")
            if (d.completed) ach.add("🏆 [${d.title}] 컬렉션 완성!")
        }

        // 공개 여부 + 글/태그 입력 → 서버 업로드 → 결과 축하 화면
        val view = layoutInflater.inflate(R.layout.dialog_publish, null)
        val captionInput = view.findViewById<EditText>(R.id.captionInput)
        val tagsInput = view.findViewById<EditText>(R.id.tagsInput)
        val dialog = android.app.Dialog(this, R.style.RundexDialog).apply {
            setContentView(view)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCancelable(false)
        }
        // 설정의 '새 러닝 기본 공개'에 따라 강조 버튼을 바꾼다
        val defPublic = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("default_public", false)
        val btnPriv = view.findViewById<TextView>(R.id.btnPrivate)
        val btnPub = view.findViewById<TextView>(R.id.btnPublish)
        if (!defPublic) {
            btnPriv.setBackgroundResource(R.drawable.btn_primary); btnPriv.setTextColor(android.graphics.Color.WHITE)
            btnPub.setBackgroundResource(R.drawable.btn_secondary); btnPub.setTextColor(getColor(R.color.textDark))
        }
        btnPriv.setOnClickListener {
            dialog.dismiss(); submitWithQueue(track, "private", "", emptyList(), ach)
        }
        btnPub.setOnClickListener {
            dialog.dismiss()
            submitWithQueue(track, "public",
                captionInput.text.toString().trim(), parseTags(tagsInput.text.toString()), ach)
        }
        dialog.show()
    }

    /** 러닝 완료 축하 화면 — 거리/시간/페이스/칼로리 + 성취 목록 */
    private fun showRunResult(track: SavedTrack, achievements: List<String>) {
        val view = layoutInflater.inflate(R.layout.dialog_result, null)
        view.findViewById<TextView>(R.id.resDistance).text = formatKm(track.distanceMeters)
        view.findViewById<TextView>(R.id.resTime).text = formatDuration(track.durationMs)
        view.findViewById<TextView>(R.id.resPace).text = formatPace(track.distanceMeters, track.durationMs)
        view.findViewById<TextView>(R.id.resKcal).text = "${RunStats.calorieKcal(track.distanceMeters).toInt()}"

        val box = view.findViewById<android.widget.LinearLayout>(R.id.resAchievements)
        val pad = (12 * resources.displayMetrics.density).toInt()
        if (achievements.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "이번 러닝은 도감 변동이 없었어요.\n새로운 동네로 달려보세요! 🏃"
                setTextColor(getColor(R.color.textGrey)); textSize = 13f; gravity = android.view.Gravity.CENTER
            })
        } else {
            achievements.forEach { line ->
                box.addView(TextView(this).apply {
                    text = line
                    setTextColor(getColor(R.color.textDark)); textSize = 14f
                    setBackgroundResource(R.drawable.card_inset)
                    setPadding(pad, pad, pad, pad)
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = pad / 2
                    layoutParams = lp
                })
            }
        }

        val dialog = android.app.Dialog(this, R.style.RundexDialog).apply {
            setContentView(view)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        view.findViewById<TextView>(R.id.resDexButton).setOnClickListener {
            dialog.dismiss(); startActivity(Intent(this, DexActivity::class.java))
        }
        view.findViewById<TextView>(R.id.resOkButton).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** "#한강 야간런, 5K" → ["한강","야간런","5K"] (# · 쉼표 · 공백 구분) */
    private fun parseTags(raw: String): List<String> =
        raw.split(Regex("[\\s,#]+")).map { it.trim() }.filter { it.isNotEmpty() }.take(8)

    private fun pendingStore() = PendingUploadStore(File(filesDir, "data"))

    /**
     * 러닝 업로드 — 큐에 적재 후 서버 전송. 성공 시 **서버가 권위 계산한 결과**(새 동·승급·칭호)로
     * 결과화면을 띄우고 큐에서 제거. 실패(오프라인/토큰만료) 시 로컬 추정 결과 + 자동 동기화 안내.
     */
    private fun submitWithQueue(
        track: SavedTrack, visibility: String, caption: String, tags: List<String>, localAch: List<String>,
    ) {
        if (!ApiConfig.enabled) { showRunResult(track, localAch); return }
        val coords = org.json.JSONArray()
        track.points.forEach { p -> coords.put(org.json.JSONArray().put(p.lon).put(p.lat)) }
        val payload = org.json.JSONObject()
            .put("startedAt", track.startedAtMs)
            .put("endedAt", track.startedAtMs + track.durationMs)
            .put("distanceM", track.distanceMeters)
            .put("durationMs", track.durationMs)
            .put("source", if (plannedRoute != null) "follow" else "free")
            .put("visibility", visibility)
            .put("caption", caption)
            .put("tags", org.json.JSONArray(tags))
            .put("coordinates", coords)
        val store = pendingStore()
        val localId = store.add(payload)
        ApiClient(Session(this)).submitRun(payload) { r ->
            runOnUiThread {
                r.onSuccess { o ->
                    store.remove(localId)
                    showRunResult(track, serverAch(o))
                }.onFailure {
                    val offline = localAch + "⏳ 오프라인 저장됨 — 연결되면 자동으로 올라가요"
                    showRunResult(track, offline)
                }
            }
        }
    }

    /** submit_run 서버 응답(권위) → 성취 라인 */
    private fun serverAch(o: org.json.JSONObject): List<String> {
        val ach = ArrayList<String>()
        o.optJSONArray("newRegions")?.takeIf { it.length() > 0 }?.let { nr ->
            val names = (0 until nr.length()).joinToString(", ") { nr.getJSONObject(it).optString("name") }
            ach.add("🗺 새 동 ${nr.length()}곳 발견 — $names")
        }
        o.optJSONArray("gradeUps")?.let { g ->
            for (i in 0 until g.length()) {
                val e = g.getJSONObject(i)
                ach.add("⬆ ${e.optString("name")} ${gradeKo(e.optString("from"))} → ${gradeKo(e.optString("to"))} 승급!")
            }
        }
        o.optJSONArray("newTitles")?.let { t ->
            for (i in 0 until t.length()) ach.add("🏅 칭호 획득 — ${t.getJSONObject(i).optString("name")}")
        }
        o.optJSONArray("themeDelta")?.let { th ->
            for (i in 0 until th.length()) ach.add("🌟 테마 +1 (${th.getJSONObject(i).optString("place")})")
        }
        return ach
    }

    private fun gradeKo(g: String) = when (g.lowercase()) {
        "card" -> "발견"; "bronze" -> "브론즈"; "silver" -> "실버"; "gold" -> "골드"; else -> g
    }

    /** 미업로드 러닝 재시도 — 앱 재개 시 호출 */
    private fun flushPending() {
        if (!ApiConfig.enabled || Session(this).userId == null) return
        val store = pendingStore()
        val items = store.all()
        if (items.isEmpty()) return
        val api = ApiClient(Session(this))
        items.forEach { (localId, payload) ->
            api.submitRun(payload) { r -> r.onSuccess { store.remove(localId) } }
        }
    }


    private fun hasPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (hasPermissions()) onStartPressed()
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
        findViewById<View>(R.id.offRouteBanner).visibility = if (nowOff) View.VISIBLE else View.GONE
    }

    private fun vibrate(ms: Long = 500L) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun refreshStats() {
        if (!TrackRecorder.recording) return
        val now = System.currentTimeMillis()
        val elapsed = TrackRecorder.elapsedMs(now)
        val dist = TrackRecorder.distanceMeters
        val currentPace = RunStats.currentPaceSecPerKm(TrackRecorder.points, now)
        val km = String.format(Locale.US, "%.2f", dist / 1000.0)
        val kcal = "${RunStats.calorieKcal(dist).toInt()}"
        val time = formatDuration(elapsed)
        val avgPace = formatPace(dist, elapsed)
        // 주황(거리) 화면
        runDistance.text = km
        runPace.text = currentPace?.let { RunStats.formatPaceSec(it) } ?: "-'--\""
        runKcal.text = kcal
        runTime.text = time
        // 지도 페이지 2×3 그리드
        findViewById<TextView>(R.id.mapKm).text = km
        findViewById<TextView>(R.id.mapPace).text = avgPace
        findViewById<TextView>(R.id.mapTime).text = time
        findViewById<TextView>(R.id.mapKcal).text = kcal
        findViewById<TextView>(R.id.mapElev).text = "${TrackRecorder.elevationGainM.toInt()} m"
        // 평균 속도 km/h (시간>0일 때만)
        val kmh = if (elapsed > 0) dist / 1000.0 / (elapsed / 3_600_000.0) else 0.0
        findViewById<TextView>(R.id.mapSpeed).text = String.format(Locale.US, "%.1f", kmh)
        refreshGoal(dist, elapsed)
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

    // ── 경로 불러오기 / 추천 코스 카드 ───────────────────────────
    /** 경로 불러오기 — 내 북마크 코스 목록(+ 최근 계획)에서 골라 따라 뛰기 설정 */
    private fun loadSavedRoute() {
        if (TrackRecorder.recording) return
        val session = Session(this)
        val uid = session.userId
        val planned = PlannedRouteStore(File(filesDir, "data")).load()

        if (!ApiConfig.enabled || uid == null) {
            if (planned != null && planned.points.size >= 2) {
                setPlannedRoute(planned.points.map { LatLngPoint(it.lat, it.lon) })
                Toast.makeText(this, "최근 계획 코스를 불러왔어요", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "북마크하거나 계획한 코스가 없어요", Toast.LENGTH_SHORT).show()
            return
        }

        ApiClient(session).listMyBookmarks(uid) { result ->
            runOnUiThread {
                val ids = ArrayList<String?>(); val labels = ArrayList<String>()
                if (planned != null && planned.points.size >= 2) {
                    ids.add(null)
                    labels.add("📍 최근 계획 코스 · ${String.format(Locale.US, "%.1f", planned.distanceMeters / 1000.0)}km")
                }
                result.getOrNull()?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i).optJSONObject("routes") ?: continue
                        ids.add(r.optString("id"))
                        val km = String.format(Locale.US, "%.1f", r.optDouble("distance_m") / 1000.0)
                        labels.add("🔖 ${r.optString("name")} · ${km}km")
                    }
                }
                if (labels.isEmpty()) {
                    Toast.makeText(this, "북마크한 코스가 없어요. 러닝→추천에서 저장하세요", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                AlertDialog.Builder(this, R.style.RundexDialog)
                    .setTitle("불러올 경로 선택")
                    .setItems(labels.toTypedArray()) { _, i ->
                        val rid = ids[i]
                        if (rid == null) {
                            setPlannedRoute(planned!!.points.map { LatLngPoint(it.lat, it.lon) })
                            Toast.makeText(this, "경로를 설정했어요. 시작을 누르세요!", Toast.LENGTH_SHORT).show()
                        } else loadRouteGeom(rid)
                    }
                    .setNegativeButton("닫기", null)
                    .show()
            }
        }
    }

    /** 서버 코스 id 의 경로를 불러와 따라 뛰기 설정 */
    private fun loadRouteGeom(routeId: String) {
        ApiClient(Session(this)).listRoutes("id=eq.$routeId&select=geom") { result ->
            result.onSuccess { arr ->
                val pts = parseGeomCoords(arr.optJSONObject(0)?.optJSONObject("geom"))
                runOnUiThread {
                    if (pts.size >= 2) {
                        setPlannedRoute(pts)
                        Toast.makeText(this, "경로를 설정했어요. 시작을 누르세요!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "경로를 불러올 수 없어요", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 지정 경로를 따라 뛰기 대상으로 설정 + 지도에 표시 + 카메라 이동 */
    private fun setPlannedRoute(points: List<LatLngPoint>) {
        if (points.size < 2) return
        plannedRoute = PlannedRoute(points, 0.0)
        planSource?.setGeoJson(
            LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })
        )
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(points.first().lat, points.first().lon), 15.0)
        )
    }

    /** 추천 코스(공개 routes)를 상단 스와이프 카드로 노출 */
    private fun loadRecommendedCards() {
        if (!ApiConfig.enabled) return
        ApiClient(Session(this)).listRoutes("is_public=eq.true&order=created_at.desc&limit=10") { result ->
            result.onSuccess { arr ->
                runOnUiThread {
                    recoRoutes.clear()
                    val container = findViewById<android.widget.LinearLayout>(R.id.recoCards)
                    val dots = findViewById<android.widget.LinearLayout>(R.id.recoDots)
                    container.removeAllViews(); dots.removeAllViews()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        recoRoutes.add(o)
                        val card = layoutInflater.inflate(R.layout.card_reco, container, false)
                        card.findViewById<TextView>(R.id.recoName).text = o.optString("name")
                        val km = String.format(Locale.US, "%.1f", o.optDouble("distance_m") / 1000.0)
                        card.findViewById<TextView>(R.id.recoSub).text =
                            "${km}km · 난이도 ${"★".repeat(o.optInt("difficulty", 1))}"
                        card.setOnClickListener { onRecoCardTap(o) }
                        container.addView(card)
                        val dot = TextView(this).apply {
                            text = "•"; textSize = 18f
                            setTextColor(getColor(if (i == 0) R.color.primary else R.color.divider))
                            setPadding(6, 0, 6, 0)
                        }
                        dots.addView(dot)
                    }
                }
            }
        }
    }

    /** 추천 카드 탭 → 상세 팝업(코스 정보 + 경로 미리보기 + 경로설정 버튼) */
    private fun onRecoCardTap(route: org.json.JSONObject) {
        val view = layoutInflater.inflate(R.layout.dialog_reco, null)
        val km = String.format(Locale.US, "%.1f", route.optDouble("distance_m") / 1000.0)
        view.findViewById<TextView>(R.id.recoDlgName).text = route.optString("name")
        view.findViewById<TextView>(R.id.recoDlgSub).text =
            "${km}km · 난이도 ${"★".repeat(route.optInt("difficulty", 1))}"
        val preview = view.findViewById<RoutePreviewView>(R.id.recoDlgRoute)

        val dialog = android.app.Dialog(this, R.style.RundexDialog).apply {
            setContentView(view)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // 경로 지오메트리 비동기 로드 → 미리보기 채우기 + '달리기' 활성
        var loadedPts: List<LatLngPoint> = emptyList()
        ApiClient(Session(this)).listRoutes("id=eq.${route.optString("id")}&select=geom") { result ->
            result.onSuccess { arr ->
                val pts = parseGeomCoords(arr.optJSONObject(0)?.optJSONObject("geom"))
                runOnUiThread {
                    loadedPts = pts
                    preview.setRoute(pts.map { doubleArrayOf(it.lon, it.lat) })
                }
            }
        }

        view.findViewById<TextView>(R.id.recoDlgRun).setOnClickListener {
            if (loadedPts.size >= 2) {
                setPlannedRoute(loadedPts)
                Toast.makeText(this, "‘${route.optString("name")}’ 경로를 설정했어요. 시작을 누르세요!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "경로를 불러오는 중이에요…", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<TextView>(R.id.recoDlgBookmark).setOnClickListener {
            val session = Session(this); val uid = session.userId
            if (uid != null) ApiClient(session).addBookmark(uid, route.optString("id")) { r ->
                runOnUiThread {
                    r.onSuccess { Toast.makeText(this, "북마크에 저장했어요", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(this, "이미 저장됐을 수 있어요", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        view.findViewById<TextView>(R.id.recoDlgClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun parseGeomCoords(geom: org.json.JSONObject?): List<LatLngPoint> {
        val coords = geom?.optJSONArray("coordinates") ?: return emptyList()
        return runCatching {
            (0 until coords.length()).map {
                val c = coords.getJSONArray(it)
                LatLngPoint(c.getDouble(1), c.getDouble(0))
            }
        }.getOrDefault(emptyList())
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

    override fun onResume() { super.onResume(); mapView.onResume(); flushPending() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
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
