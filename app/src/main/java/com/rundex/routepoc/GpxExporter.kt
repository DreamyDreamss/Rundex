package com.rundex.routepoc

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** SavedTrack → GPX 1.1 문자열 */
object GpxExporter {
    fun toGpx(track: SavedTrack): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="rundex-poc" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("<trk><name>run-${track.id}</name><trkseg>").append('\n')
        track.points.forEach { p ->
            sb.append("""<trkpt lat="${p.lat}" lon="${p.lon}"><time>${iso.format(Date(p.timeMs))}</time></trkpt>""").append('\n')
        }
        sb.append("</trkseg></trk></gpx>")
        return sb.toString()
    }
}
