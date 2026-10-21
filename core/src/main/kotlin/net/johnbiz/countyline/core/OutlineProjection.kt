package net.johnbiz.countyline.core

import kotlin.math.cos
import kotlin.math.min

/**
 * Fits county rings into a pixel box for drawing: equirectangular, with longitude scaled by
 * the cosine of the middle latitude so shapes keep their real proportions, Y pointing down,
 * centered and aspect-preserving.
 *
 * A county whose longitudes span more than 180 degrees is assumed to cross the antimeridian
 * (Aleutians West), and its eastern-hemisphere longitudes are shifted by -360 first.
 */
object OutlineProjection {

    private const val MIN_SPAN = 1e-9

    fun fit(rings: List<DoubleArray>, width: Float, height: Float, padding: Float): List<FloatArray> {
        if (rings.none { it.size >= 2 }) return emptyList()

        var minLng = Double.POSITIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        forEachPoint(rings) { lng, lat ->
            minLng = min(minLng, lng); maxLng = maxOf(maxLng, lng)
            minLat = min(minLat, lat); maxLat = maxOf(maxLat, lat)
        }

        val unwrap = maxLng - minLng > 180.0
        if (unwrap) {
            minLng = Double.POSITIVE_INFINITY
            maxLng = Double.NEGATIVE_INFINITY
            forEachPoint(rings) { lng, _ ->
                val x = unwrapped(lng)
                minLng = min(minLng, x); maxLng = maxOf(maxLng, x)
            }
        }

        val kx = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLng - minLng) * kx).coerceAtLeast(MIN_SPAN)
        val spanY = (maxLat - minLat).coerceAtLeast(MIN_SPAN)
        val availW = (width - 2 * padding).coerceAtLeast(0f).toDouble()
        val availH = (height - 2 * padding).coerceAtLeast(0f).toDouble()
        val scale = min(availW / spanX, availH / spanY)
        val offX = padding + (availW - spanX * scale) / 2
        val offY = padding + (availH - spanY * scale) / 2

        return rings.map { ring ->
            FloatArray(ring.size).also { out ->
                var i = 0
                while (i + 1 < ring.size) {
                    val lng = if (unwrap) unwrapped(ring[i]) else ring[i]
                    out[i] = (offX + (lng - minLng) * kx * scale).toFloat()
                    out[i + 1] = (offY + (maxLat - ring[i + 1]) * scale).toFloat()
                    i += 2
                }
            }
        }
    }

    private fun unwrapped(lng: Double): Double = if (lng > 0) lng - 360.0 else lng

    private inline fun forEachPoint(rings: List<DoubleArray>, action: (lng: Double, lat: Double) -> Unit) {
        for (ring in rings) {
            var i = 0
            while (i + 1 < ring.size) {
                action(ring[i], ring[i + 1])
                i += 2
            }
        }
    }
}
