package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    private val county = County("99999", "Test", "36", "New York", "NY")

    /** Unit square with corners (0,0)-(1,1), stored as [lng,lat] pairs, closed ring. */
    private fun square(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double) = doubleArrayOf(
        minLng, minLat,
        maxLng, minLat,
        maxLng, maxLat,
        minLng, maxLat,
        minLng, minLat,
    )

    @Test
    fun `point inside simple polygon`() {
        val poly = CountyPolygon.of(county, listOf(square(0.0, 0.0, 2.0, 2.0)))
        assertTrue(poly.contains(1.0, 1.0))
    }

    @Test
    fun `point outside simple polygon`() {
        val poly = CountyPolygon.of(county, listOf(square(0.0, 0.0, 2.0, 2.0)))
        assertFalse(poly.contains(3.0, 1.0))
        assertFalse(poly.contains(1.0, -0.5))
    }

    @Test
    fun `bounding box rejects far points before ray casting`() {
        val poly = CountyPolygon.of(county, listOf(square(0.0, 0.0, 1.0, 1.0)))
        assertFalse(poly.contains(100.0, 100.0))
    }

    @Test
    fun `hole ring excludes points inside it`() {
        val outer = square(0.0, 0.0, 10.0, 10.0)
        val hole = square(4.0, 4.0, 6.0, 6.0)
        val poly = CountyPolygon.of(county, listOf(outer, hole))

        assertTrue("between hole and outer edge is inside", poly.contains(2.0, 2.0))
        assertFalse("inside the hole is outside", poly.contains(5.0, 5.0))
    }

    @Test
    fun `bounding box of ring covers all vertices`() {
        val bb = BoundingBox.ofRing(square(-5.0, -3.0, 7.0, 11.0))
        assertTrue(bb.contains(-5.0, -3.0))
        assertTrue(bb.contains(7.0, 11.0))
        assertFalse(bb.contains(-5.1, 0.0))
    }

    @Test
    fun `edge distance is measured to the nearest segment in kilometers`() {
        val poly = CountyPolygon.of(county, listOf(square(0.0, 0.0, 1.0, 1.0)))
        // 0.01° of longitude at the equator is about 1.11 km.
        assertEquals(1.11, poly.edgeDistanceKm(lng = -0.01, lat = 0.5), 0.01)
        // Nearest point is the corner, not the extended edge line.
        assertEquals(1.57, poly.edgeDistanceKm(lng = -0.01, lat = -0.01), 0.01)
    }

    @Test
    fun `edge distance shrinks longitude with latitude`() {
        val poly = CountyPolygon.of(county, listOf(square(0.0, 59.0, 1.0, 61.0)))
        // At 60°N a degree of longitude is half as long as at the equator.
        assertEquals(0.556, poly.edgeDistanceKm(lng = -0.01, lat = 60.0), 0.01)
    }

    @Test
    fun `edge distance counts hole rings`() {
        val poly = CountyPolygon.of(county, listOf(square(0.0, 0.0, 10.0, 10.0), square(4.0, 4.0, 6.0, 6.0)))
        assertEquals(1.11, poly.edgeDistanceKm(lng = 4.01, lat = 5.0), 0.01)
    }
}
