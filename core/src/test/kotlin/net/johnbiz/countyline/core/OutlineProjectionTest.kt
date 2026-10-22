package net.johnbiz.countyline.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class OutlineProjectionTest {

    private class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val width get() = maxX - minX
        val height get() = maxY - minY
    }

    private fun bounds(rings: List<FloatArray>): Bounds {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (r in rings) {
            var i = 0
            while (i < r.size) {
                minX = minOf(minX, r[i]); maxX = maxOf(maxX, r[i])
                minY = minOf(minY, r[i + 1]); maxY = maxOf(maxY, r[i + 1])
                i += 2
            }
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    /** Closed axis-aligned rectangle ring. */
    private fun rect(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double) = doubleArrayOf(
        minLng, minLat, maxLng, minLat, maxLng, maxLat, minLng, maxLat, minLng, minLat,
    )

    @Test
    fun `wide shape fills the width and is centered vertically`() {
        val out = OutlineProjection.fit(listOf(rect(0.0, -0.5, 2.0, 0.5)), 100f, 100f, padding = 0f)
        val b = bounds(out)
        assertEquals(0f, b.minX, 0.05f)
        assertEquals(100f, b.maxX, 0.05f)
        assertEquals(25f, b.minY, 0.05f)
        assertEquals(75f, b.maxY, 0.05f)
    }

    @Test
    fun `longitude is scaled by latitude so shapes are not stretched`() {
        // 2 degrees of longitude at 60N is about as wide as 1 degree of latitude.
        val out = OutlineProjection.fit(listOf(rect(0.0, 59.5, 2.0, 60.5)), 100f, 100f, padding = 0f)
        val b = bounds(out)
        assertEquals(1f, b.width / b.height, 0.02f)
    }

    @Test
    fun `padding is respected`() {
        val b = bounds(OutlineProjection.fit(listOf(rect(0.0, 0.0, 1.0, 1.0)), 100f, 100f, padding = 10f))
        assertTrue(b.minX >= 9.99f && b.minY >= 9.99f && b.maxX <= 90.01f && b.maxY <= 90.01f)
    }

    @Test
    fun `north is up`() {
        // Triangle with its apex at the northernmost latitude.
        val ring = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.5, 1.0, 0.0, 0.0)
        val out = OutlineProjection.fit(listOf(ring), 100f, 100f, padding = 0f).single()
        val apexY = out[5]
        assertEquals(bounds(listOf(out)).minY, apexY, 0.001f)
    }

    @Test
    fun `every ring is projected with the same transform`() {
        val outer = rect(0.0, 0.0, 4.0, 4.0)
        val hole = rect(1.0, 1.0, 3.0, 3.0)
        val out = OutlineProjection.fit(listOf(outer, hole), 100f, 100f, padding = 0f)
        assertEquals(2, out.size)
        val o = bounds(listOf(out[0])); val h = bounds(listOf(out[1]))
        assertTrue(h.minX > o.minX && h.maxX < o.maxX && h.minY > o.minY && h.maxY < o.maxY)
    }

    @Test
    fun `shape crossing the antimeridian is unwrapped`() {
        // 179E to 179W is 2 degrees wide, not 358.
        val ring = doubleArrayOf(179.0, 50.0, -179.0, 50.0, -179.0, 51.0, 179.0, 51.0, 179.0, 50.0)
        val b = bounds(OutlineProjection.fit(listOf(ring), 100f, 100f, padding = 0f))
        assertTrue("height ${b.height} should be most of the box", b.height > 50f)
    }

    @Test
    fun `degenerate input stays finite and inside the box`() {
        val point = doubleArrayOf(-77.0, 38.0, -77.0, 38.0)
        val line = doubleArrayOf(-77.0, 38.0, -76.0, 38.0)
        for (ring in listOf(point, line)) {
            val out = OutlineProjection.fit(listOf(ring), 100f, 50f, padding = 4f).single()
            assertTrue(out.all { it.isFinite() && it >= 0f && it <= 100f })
        }
        assertEquals(emptyList<FloatArray>(), OutlineProjection.fit(emptyList(), 100f, 100f, 0f))
    }

    @Test
    fun `point is placed with the same transform as the rings`() {
        val rings = listOf(rect(0.0, -0.5, 2.0, 0.5))
        // Wide box fills the width (0..100) and sits at 25..75 vertically.
        assertArrayEquals(floatArrayOf(50f, 50f), OutlineProjection.point(rings, 1.0, 0.0, 100f, 100f, padding = 0f), 0.05f)
        assertArrayEquals(floatArrayOf(0f, 25f), OutlineProjection.point(rings, 0.0, 0.5, 100f, 100f, padding = 0f), 0.05f)
    }

    @Test
    fun `point respects padding`() {
        val rings = listOf(rect(0.0, 0.0, 1.0, 1.0))
        assertArrayEquals(floatArrayOf(90f, 90f), OutlineProjection.point(rings, 1.0, 0.0, 100f, 100f, padding = 10f), 0.05f)
    }

    @Test
    fun `point is unwrapped with a shape crossing the antimeridian`() {
        val ring = doubleArrayOf(179.0, 50.0, -179.0, 50.0, -179.0, 51.0, 179.0, 51.0, 179.0, 50.0)
        val ringX = bounds(OutlineProjection.fit(listOf(ring), 100f, 100f, padding = 0f))
        val east = OutlineProjection.point(listOf(ring), 179.5, 50.5, 100f, 100f, padding = 0f)!!
        val west = OutlineProjection.point(listOf(ring), -179.5, 50.5, 100f, 100f, padding = 0f)!!
        assertTrue(east[0] > ringX.minX && east[0] < west[0] && west[0] < ringX.maxX)
    }

    @Test
    fun `point without rings is null`() {
        assertNull(OutlineProjection.point(emptyList(), 0.0, 0.0, 100f, 100f, padding = 0f))
    }

    @Test
    fun `real Aleutians West outline is compact`() {
        val asset = File("../app/src/main/assets/counties.geojson")
        assumeTrue(asset.exists())
        val resolver = asset.inputStream().use { GeoJsonCountyResolver.fromGeoJson(it) }
        val b = bounds(OutlineProjection.fit(resolver.outline("02016")!!, 200f, 100f, padding = 0f))
        // Unwrapped it spans about 21 degrees of longitude; wrapped it would be a sliver.
        assertTrue("height ${b.height}", b.height > 20f)
    }
}
