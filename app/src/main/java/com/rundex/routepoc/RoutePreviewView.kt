package com.rundex.routepoc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * 경로 미리보기 — 지도 타일 없이 폴리라인만 정규화해 그린다.
 * (피드 카드/리스트에서 MapView 비용·에뮬레이터 GL 문제 없이 안정적으로 렌더)
 */
class RoutePreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** [lon, lat] 점 목록 */
    private var pts: List<DoubleArray> = emptyList()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6A3D")
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E4E7EC"); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val density = resources.displayMetrics.density

    fun setRoute(points: List<DoubleArray>) {
        pts = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 지도풍 배경(연한 종이 + 격자) — 둥근 모서리 클립
        val radius = 14f * density
        val clip = android.graphics.Path().apply {
            addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawColor(Color.parseColor("#F2F4F7"))
        val step = 26f * density
        var gx = step
        while (gx < width) { canvas.drawLine(gx, 0f, gx, height.toFloat(), grid); gx += step }
        var gy = step
        while (gy < height) { canvas.drawLine(0f, gy, width.toFloat(), gy, grid); gy += step }

        if (pts.size < 2) { canvas.restore(); return }

        val pad = 24f
        val w = width - pad * 2
        val h = height - pad * 2
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        pts.forEach { p ->
            if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0]
            if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1]
        }
        val spanX = (maxX - minX).coerceAtLeast(1e-9)
        val spanY = (maxY - minY).coerceAtLeast(1e-9)
        // 가로세로 비율 유지(찌그러짐 방지)
        val scale = minOf(w / spanX, h / spanY)
        val offX = pad + (w - spanX * scale) / 2
        val offY = pad + (h - spanY * scale) / 2

        fun sx(lon: Double) = (offX + (lon - minX) * scale).toFloat()
        // 위도는 위로 갈수록 커지므로 y축 뒤집기
        fun sy(lat: Double) = (offY + (maxY - lat) * scale).toFloat()

        val path = Path()
        path.moveTo(sx(pts[0][0]), sy(pts[0][1]))
        for (i in 1 until pts.size) path.lineTo(sx(pts[i][0]), sy(pts[i][1]))
        canvas.drawPath(path, line)

        dot.color = Color.parseColor("#2ECC71")  // 출발 — 초록
        canvas.drawCircle(sx(pts.first()[0]), sy(pts.first()[1]), 9f, dot)
        dot.color = Color.parseColor("#E74C3C")  // 도착 — 빨강
        canvas.drawCircle(sx(pts.last()[0]), sy(pts.last()[1]), 9f, dot)
        canvas.restore()
    }
}
