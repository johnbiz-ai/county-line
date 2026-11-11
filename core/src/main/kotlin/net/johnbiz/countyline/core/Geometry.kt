package net.johnbiz.countyline.core

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * An axis-aligned bounding box in lng/lat degrees. Used as a cheap pre-filter
 * before the (relatively expensive) point-in-polygon test.
 */
internal class BoundingBox(
    val minLng: Double,
    val minLat: Double,
    val maxLng: Double,
    val maxLat: Double,
) {
    fun contains(lng: Double, lat: Double): Boolean =
        lng in minLng..maxLng && lat in minLat..maxLat

    companion object {
        /** Bounding box of a ring stored as `[lng0, lat0, lng1, lat1, ...]`. */
        fun ofRing(ring: DoubleArray): BoundingBox {
            var minLng = Double.POSITIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLng = Double.NEGATIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var i = 0
            while (i < ring.size) {
                val lng = ring[i]
                val lat = ring[i + 1]
                if (lng < minLng) minLng = lng
                if (lng > maxLng) maxLng = lng
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                i += 2
            }
            return BoundingBox(minLng, minLat, maxLng, maxLat)
        }
    }
}

/**
 * One polygon of a county boundary: a single outer ring plus zero or more hole
 * rings. A county with disjoint landmasses (islands) is represented as several
 * [CountyPolygon]s that share the same [county].
 *
 * Rings are flat `DoubleArray`s of `[lng, lat]` pairs. Containment uses the
 * even-odd (ray-casting) rule across *all* rings, so holes are handled
 * automatically: a point inside a hole crosses the outer ring once and the hole
 * ring once, netting to "outside".
 */
internal class CountyPolygon(
    val county: County,
    private val rings: List<DoubleArray>,
    private val bbox: BoundingBox,
) {
    val boundingBox: BoundingBox get() = bbox

    fun contains(lng: Double, lat: Double): Boolean {
        if (!bbox.contains(lng, lat)) return false
        var inside = false
        for (ring in rings) {
            if (rayCrossesRing(lng, lat, ring)) inside = !inside
        }
        return inside
    }

    /**
     * Distance in kilometers from the point to the nearest edge of any ring (holes included).
     * Uses an equirectangular projection centered on the point, which is accurate to well under
     * 1% at the few-kilometer distances snapping cares about.
     */
    fun edgeDistanceKm(lng: Double, lat: Double): Double {
        val kx = KM_PER_DEGREE * cos(Math.toRadians(lat))
        var best = Double.POSITIVE_INFINITY
        for (ring in rings) {
            val n = ring.size / 2
            for (i in 1 until n) {
                val d = segmentDistanceSq(
                    (ring[i * 2 - 2] - lng) * kx, (ring[i * 2 - 1] - lat) * KM_PER_DEGREE,
                    (ring[i * 2] - lng) * kx, (ring[i * 2 + 1] - lat) * KM_PER_DEGREE,
                )
                if (d < best) best = d
            }
        }
        return sqrt(best)
    }

    companion object {
        /** Mean length of one degree of latitude. */
        const val KM_PER_DEGREE: Double = 111.32

        /** Squared distance from the origin to segment (ax,ay)-(bx,by). */
        private fun segmentDistanceSq(ax: Double, ay: Double, bx: Double, by: Double): Double {
            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq == 0.0) 0.0 else (-(ax * dx + ay * dy) / lenSq).coerceIn(0.0, 1.0)
            val x = ax + t * dx
            val y = ay + t * dy
            return x * x + y * y
        }

        /** @param rings first entry is the outer ring; the rest are holes. */
        fun of(county: County, rings: List<DoubleArray>): CountyPolygon =
            CountyPolygon(county, rings, BoundingBox.ofRing(rings.first()))

        /**
         * Standard ray-casting: count how many times a ray from the point going
         * in +lng direction crosses this ring's edges; odd => inside.
         */
        private fun rayCrossesRing(lng: Double, lat: Double, c: DoubleArray): Boolean {
            var inside = false
            val n = c.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = c[i * 2]
                val yi = c[i * 2 + 1]
                val xj = c[j * 2]
                val yj = c[j * 2 + 1]
                if ((yi > lat) != (yj > lat) &&
                    lng < (xj - xi) * (lat - yi) / (yj - yi) + xi
                ) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}
