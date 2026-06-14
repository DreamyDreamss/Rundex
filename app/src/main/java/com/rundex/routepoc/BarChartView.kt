package com.rundex.routepoc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 주간 거리 막대그래프 — (라벨, km) 목록을 받아 정규화해 그린다. 마지막 막대 강조. */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var data: List<Pair<String, Double>> = emptyList()
    private val density = resources.displayMetrics.density

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93"); textSize = 10f * density; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1E"); textSize = 10f * density
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    /** km 단위 값 */
    fun setData(values: List<Pair<String, Double>>) { data = values; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val n = data.size
        val labelH = 18f * density
        val valueH = 16f * density
        val chartTop = valueH
        val chartBottom = height - labelH
        val chartH = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxV = (data.maxOf { it.second }).coerceAtLeast(1.0)
        val slot = width.toFloat() / n
        val bw = slot * 0.5f
        val radius = 6f * density

        for (i in data.indices) {
            val (lab, v) = data[i]
            val cx = slot * i + slot / 2
            val h = (v / maxV * chartH).toFloat()
            val top = chartBottom - h
            val isLast = i == n - 1
            bar.color = if (isLast) Color.parseColor("#FF5A2D")
                else Color.parseColor("#FFD9C9")
            if (v > 0) {
                canvas.drawRoundRect(RectF(cx - bw / 2, top, cx + bw / 2, chartBottom), radius, radius, bar)
                if (isLast) canvas.drawText(String.format("%.1f", v), cx, top - 4f * density, valuePaint)
            } else {
                // 0인 주는 바닥에 옅은 점선 대신 작은 칸
                bar.color = Color.parseColor("#EEEFF2")
                canvas.drawRoundRect(RectF(cx - bw / 2, chartBottom - 3f * density, cx + bw / 2, chartBottom), radius, radius, bar)
            }
            canvas.drawText(lab, cx, height - 4f * density, label)
        }
    }
}
