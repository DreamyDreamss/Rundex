package com.rundex.routepoc

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File

/**
 * 화면이 꺼져도 위치를 계속 수신하는 location Foreground Service.
 * 받은 좌표를 TrackRecorder로 전달하고, 진행 상태를 디스크에 주기 저장(강제종료 복구용).
 * START_STICKY로 시스템이 죽여도 재시작되며, 재시작 시 스냅샷에서 기록을 복원한다.
 */
class TrackingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var activeStore: ActiveRunStore

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                TrackRecorder.updateRaw(loc.latitude, loc.longitude)
                val accepted = TrackRecorder.addPoint(
                    loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis(),
                    if (loc.hasAltitude()) loc.altitude else Double.NaN
                )
                if (accepted) activeStore.save(TrackRecorder.snapshot()) // 점마다 복구 스냅샷 갱신
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        activeStore = ActiveRunStore(File(filesDir, "data"))
    }

    @SuppressLint("MissingPermission") // 권한은 TrackActivity에서 확인 후 시작
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시스템이 죽인 뒤 재시작(intent == null)이면 스냅샷에서 복원
        if (intent == null && !TrackRecorder.recording) {
            activeStore.load()?.let { TrackRecorder.restore(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        return START_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "러닝 기록", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("러닝 기록 중")
            .setContentText("GPS 궤적을 기록하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIF_ID = 1
    }
}
