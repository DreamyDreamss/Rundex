package com.rundex.routepoc

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

/** 러닝 인사이트 — 주간 거리 막대그래프 + 최고기록 + 누적. 서버(my_insights) 원천. */
class StatsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        findViewById<TextView>(R.id.insBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (!ApiConfig.enabled || Session(this).userId == null) return
        ApiClient(Session(this)).getInsights { r ->
            runOnUiThread { r.onSuccess { bind(it) } }
        }
    }

    private fun bind(o: JSONObject) {
        // 주간 차트
        val weeks = o.optJSONArray("weeks")
        val data = ArrayList<Pair<String, Double>>()
        var sum = 0.0
        if (weeks != null) for (i in 0 until weeks.length()) {
            val w = weeks.getJSONObject(i)
            val km = w.optDouble("m") / 1000.0
            data.add(w.optString("label") to km)
            sum += km
        }
        findViewById<BarChartView>(R.id.insChart).setData(data)
        findViewById<TextView>(R.id.insWeekTotal).text =
            "8주 합계 ${String.format(Locale.US, "%.1f", sum)} km"

        val longestM = o.optDouble("longestM")
        val longestMs = o.optLong("longestMs")
        val bestPace = o.optDouble("bestPace")   // sec/km
        findViewById<TextView>(R.id.insLongest).text = String.format(Locale.US, "%.2f km", longestM / 1000.0)
        findViewById<TextView>(R.id.insLongestTime).text = TrackActivity.formatDuration(longestMs)
        findViewById<TextView>(R.id.insBestPace).text =
            if (bestPace > 0) String.format(Locale.US, "%d'%02d\"", (bestPace / 60).toInt(), (bestPace % 60).toInt()) else "-"
        findViewById<TextView>(R.id.insMonth).text = String.format(Locale.US, "%.1f km", o.optDouble("monthM") / 1000.0)
        findViewById<TextView>(R.id.insTotal).text = String.format(Locale.US, "%.1f km", o.optDouble("totalM") / 1000.0)
        findViewById<TextView>(R.id.insRuns).text = "${o.optInt("totalRuns")}회"
    }
}
