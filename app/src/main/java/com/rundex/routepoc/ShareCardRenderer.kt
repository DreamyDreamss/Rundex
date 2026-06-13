package com.rundex.routepoc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 러닝 기록을 인스타 스토리용 정사각 카드 이미지(Bitmap)로 렌더한다.
 * 지도 타일 없이 경로 폴리라인만 그려 서버·네트워크 없이 동작한다.
 */
object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1080

    fun render(track: SavedTrack, repTitle: String?, discovered: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 배경: 대각선 3단 그라데이션 (선셋 오렌지)
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(
                    Color.parseColor("#FF9A62"),
                    Color.parseColor("#FF5A2D"),
                    Color.parseColor("#D8340F"),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // 장식: 우상단 반투명 원 2개
        val deco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1AFFFFFF") }
        c.drawCircle(W - 80f, 60f, 220f, deco)
        c.drawCircle(W - 250f, 200f, 120f, deco)

        // 헤더: 브랜드 + 날짜
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        white.textSize = 58f; white.isFakeBoldText = true; white.letterSpacing = 0.12f
        c.drawText("RUNDEX", 64f, 112f, white)
        white.letterSpacing = 0f
        white.textSize = 34f; white.isFakeBoldText = false
        white.alpha = 220
        c.drawText(
            SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREA).format(Date(track.startedAtMs)),
            64f, 164f, white,
        )
        white.alpha = 255

        // 경로 폴리라인 (글로우 + 시작/끝 점) — 헤더와 수치 사이 중앙 영역에 정사각으로
        drawRoute(c, track.points, left = (W - 460f) / 2f, top = 200f, size = 460f)

        // 메인 수치: 거리 크게
        white.textSize = 168f; white.isFakeBoldText = true
        c.drawText(TrackActivity.formatKm(track.distanceMeters), 56f, 840f, white)

        // 보조 수치: 라벨 + 값 3열
        drawStat(c, 64f, "시간", TrackActivity.formatDuration(track.durationMs))
        drawStat(c, 420f, "평균 페이스", TrackActivity.formatPace(track.distanceMeters, track.durationMs))
        drawStat(c, 790f, "칼로리", "${RunStats.calorieKcal(track.distanceMeters).toInt()} kcal")

        // 하단: 칭호·발견 동 칩 배지 (수치 행 아래 별도 줄)
        var chipX = 64f
        repTitle?.let { chipX = drawChip(c, chipX, 1035f, "🏅 $it") + 20f }
        if (discovered > 0) drawChip(c, chipX, 1035f, "📒 새 동 ${discovered}곳")

        return bmp
    }

    /** 라벨(작게, 반투명) + 값(굵게) 세로 2단 */
    private fun drawStat(c: Canvas, x: Float, label: String, value: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        p.textSize = 30f; p.alpha = 200
        c.drawText(label, x, 892f, p)
        p.textSize = 46f; p.alpha = 255; p.isFakeBoldText = true
        c.drawText(value, x, 944f, p)
    }

    /** 반투명 흰 라운드 칩 + 텍스트. 다음 칩의 시작 x를 반환 */
    private fun drawChip(c: Canvas, x: Float, y: Float, text: String): Float {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 38f; isFakeBoldText = true
        }
        val w = tp.measureText(text)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFFFFF") }
        val rect = RectF(x, y - 52f, x + w + 48f, y + 22f)
        c.drawRoundRect(rect, 37f, 37f, bgPaint)
        c.drawText(text, x + 24f, y, tp)
        return rect.right
    }

    private fun drawRoute(c: Canvas, pts: List<TrackPoint>, left: Float, top: Float, size: Float) {
        if (pts.size < 2) return
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        pts.forEach {
            if (it.lat < minLat) minLat = it.lat; if (it.lat > maxLat) maxLat = it.lat
            if (it.lon < minLon) minLon = it.lon; if (it.lon > maxLon) maxLon = it.lon
        }
        val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
        val spanLon = (maxLon - minLon).coerceAtLeast(1e-6)
        val span = maxOf(spanLat, spanLon) // 정사각 유지(왜곡 방지)
        val pad = 40f
        val draw = size - pad * 2

        fun x(lon: Double) = left + pad + ((lon - minLon) / span * draw).toFloat()
        // 위도는 위로 갈수록 커지므로 y 반전
        fun y(lat: Double) = top + pad + draw - ((lat - minLat) / span * draw).toFloat()

        val path = Path()
        path.moveTo(x(pts[0].lon), y(pts[0].lat))
        for (i in 1 until pts.size) path.lineTo(x(pts[i].lon), y(pts[i].lat))

        // 글로우(넓고 반투명) → 본선 순서로 두 번 그리기
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF"); style = Paint.Style.STROKE
            strokeWidth = 30f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, glow)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE
            strokeWidth = 13f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, stroke)

        // 시작점(흰 테두리 + 초록) / 끝점(흰 채움)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.color = Color.WHITE
        c.drawCircle(x(pts[0].lon), y(pts[0].lat), 22f, dot)
        dot.color = Color.parseColor("#2ECC71")
        c.drawCircle(x(pts[0].lon), y(pts[0].lat), 14f, dot)
        dot.color = Color.WHITE
        c.drawCircle(x(pts.last().lon), y(pts.last().lat), 18f, dot)
    }
}
