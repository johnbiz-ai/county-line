package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class GeoJsonCountyResolverTest {

    // --- Synthetic dataset: deterministic geometry + spatial-index behaviour --------------------

    private fun synthetic(): GeoJsonCountyResolver =
        javaClass.getResourceAsStream("/synthetic-counties.geojson")!!
            .use { GeoJsonCountyResolver.fromGeoJson(it) }

    @Test
    fun `resolves points to the containing polygon`() {
        val r = synthetic()
        assertEquals("Alpha", r.resolve(lat = 0.5, lng = 0.5)?.name)
        assertEquals("Bravo", r.resolve(lat = 0.5, lng = 1.5)?.name)
    }

    @Test
    fun `adjacent counties either side of a shared border resolve differently`() {
        val r = synthetic()
        val west = r.resolve(lat = 0.5, lng = 0.25)
        val east = r.resolve(lat = 0.5, lng = 1.75)
        assertEquals("Alpha", west?.name)
        assertEquals("Bravo", east?.name)
        assertEquals("06", east?.stateFips)
    }

    @Test
    fun `skips features with null geometry`() {
        val r = synthetic()
        // Echo (51005) has null geometry and must not blow up loading or count.
        assertEquals(4, r.countyCount)
    }

    @Test
    fun `point in an interior hole resolves to no county`() {
        val r = synthetic()
        assertEquals("Charlie", r.resolve(lat = 11.0, lng = 11.0)?.name)
        assertEquals("Charlie", r.resolve(lat = 19.0, lng = 19.0)?.name)
        assertNull("centre of the hole", r.resolve(lat = 15.0, lng = 15.0))
    }

    @Test
    fun `multipolygon county matches either landmass but not the gap between`() {
        val r = synthetic()
        assertEquals("Delta", r.resolve(lat = 30.5, lng = 30.5)?.name)
        assertEquals("Delta", r.resolve(lat = 40.5, lng = 40.5)?.name)
        assertNull(r.resolve(lat = 35.0, lng = 35.0))
    }

    @Test
    fun `points outside every polygon resolve to null`() {
        val r = synthetic()
        assertNull(r.resolve(lat = 89.0, lng = 179.0))
        assertNull(r.resolve(lat = -5.0, lng = -5.0))
    }

    @Test
    fun `a point just outside a county snaps to it`() {
        val r = synthetic()
        // ~1.1 km west of Alpha, in a grid cell no polygon touches.
        assertEquals("Alpha", r.resolve(lat = 0.5, lng = -0.01)?.name)
        // ~1.1 km south of Charlie's outer edge.
        assertEquals("Charlie", r.resolve(lat = 9.99, lng = 15.0)?.name)
    }

    @Test
    fun `a point just inside a hole snaps to the surrounding county`() {
        assertEquals("Charlie", synthetic().resolve(lat = 15.0, lng = 13.01)?.name)
    }

    @Test
    fun `snapping picks the nearest county`() {
        val r = synthetic()
        // Just north of the Alpha/Bravo border, closer to Bravo's side.
        assertEquals("Bravo", r.resolve(lat = 1.01, lng = 1.02)?.name)
        assertEquals("Alpha", r.resolve(lat = 1.01, lng = 0.98)?.name)
    }

    @Test
    fun `points beyond the snap tolerance stay null`() {
        val r = synthetic()
        assertNull("~11 km west of Alpha", r.resolve(lat = 0.5, lng = -0.1))
    }

    @Test
    fun `a zero snap tolerance disables snapping`() {
        val r = javaClass.getResourceAsStream("/synthetic-counties.geojson")!!
            .use { GeoJsonCountyResolver.fromGeoJson(it, snapToleranceKm = 0.0) }
        assertNull(r.resolve(lat = 0.5, lng = -0.01))
        assertEquals("Alpha", r.resolve(lat = 0.5, lng = 0.5)?.name)
    }

    @Test
    fun `reads a gzip-compressed stream`() {
        val raw = javaClass.getResourceAsStream("/synthetic-counties.geojson")!!.use { it.readBytes() }
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(raw) }

        val r = java.io.ByteArrayInputStream(gz.toByteArray())
            .use { GeoJsonCountyResolver.fromGzippedGeoJson(it) }

        assertEquals("Alpha", r.resolve(lat = 0.5, lng = 0.5)?.name)
        assertEquals(4, r.countyCount)
    }

    @Test
    fun `large polygon spanning many grid cells is still found`() {
        // Charlie covers lat 10..20, lng 10..20 => ~100 index cells.
        val r = synthetic()
        assertNotNull(r.resolve(lat = 10.5, lng = 19.5))
        assertNotNull(r.resolve(lat = 19.5, lng = 10.5))
    }

    @Test
    fun `state names and abbreviations are filled from FIPS`() {
        val alpha = synthetic().resolve(lat = 0.5, lng = 0.5)!!
        assertEquals("New York", alpha.stateName)
        assertEquals("NY", alpha.stateAbbr)
        assertEquals("Alpha, NY", alpha.displayName)
    }

    @Test
    fun `catalog looks up counties by FIPS`() {
        val r = synthetic()
        assertEquals("Alpha", r.county("36001")?.name)
        assertEquals("TX", r.county("48003")?.stateAbbr)
        assertNull(r.county("99999"))
    }

    @Test
    fun `catalog omits features with null geometry`() {
        val r = synthetic()
        assertNull(r.county("51005"))
        assertNull(r.outline("51005"))
        assertEquals(setOf("36001", "06002", "48003", "12004"), r.fipsCodes)
    }

    @Test
    fun `outline returns every ring of every sub-polygon`() {
        val r = synthetic()
        assertEquals(1, r.outline("36001")!!.size)  // Alpha: one simple polygon
        assertEquals(2, r.outline("48003")!!.size)  // Charlie: outer ring + hole
        assertEquals(2, r.outline("12004")!!.size)  // Delta: two landmasses
        assertTrue(r.outline("12004")!!.all { it.size >= 8 && it.size % 2 == 0 })
        assertNull(r.outline("99999"))
    }

    // --- Real bundled dataset: fixture coordinates near known county borders --------------------

    /** The exact asset the app bundles, resolved relative to the `core/` module dir at test time. */
    private val bundledAsset = File("../app/src/main/assets/counties.geojson")

    private fun realResolver(): GeoJsonCountyResolver {
        assumeTrue("bundled dataset not present at $bundledAsset", bundledAsset.exists())
        return bundledAsset.inputStream().use { GeoJsonCountyResolver.fromGeoJson(it) }
    }

    @Test
    fun `real dataset loads the full set of counties`() {
        val r = realResolver()
        // 50 states + DC + PR ~= 3221 county-equivalents in this dataset.
        assertEquals(3221, r.countyCount)
    }

    @Test
    fun `real dataset resolves interior anchor points`() {
        val r = realResolver()
        assertResolves(r, 40.7831, -73.9712, "36061", "New York")     // Manhattan
        assertResolves(r, 34.0522, -118.2437, "06037", "Los Angeles") // Los Angeles
        assertResolves(r, 41.8781, -87.6298, "17031", "Cook")         // Chicago
        assertResolves(r, 29.9511, -90.0715, "22071", "Orleans")      // New Orleans (parish)
        assertResolves(r, 21.3069, -157.8583, "15003", "Honolulu")    // Hawaii
        assertResolves(r, 61.2181, -149.9003, "02020", "Anchorage")   // Alaska
        assertResolves(r, 25.7617, -80.1918, "12086", "Miami-Dade")   // Florida
    }

    @Test
    fun `real dataset distinguishes counties across nearby borders`() {
        val r = realResolver()
        // Kansas City straddles a state line: Missouri side vs Kansas side.
        assertResolves(r, 39.10, -94.59, "29095", "Jackson")
        assertResolves(r, 39.10, -94.62, "20209", "Wyandotte")
        // Washington, DC vs Arlington County, VA across the Potomac.
        assertResolves(r, 38.890, -77.050, "11001", "District of Columbia")
        assertResolves(r, 38.875, -77.080, "51013", "Arlington")
        // San Bernardino County vs Riverside County, CA.
        assertResolves(r, 34.07, -117.24, "06071", "San Bernardino")
        assertResolves(r, 33.95, -117.24, "06065", "Riverside")
    }

    @Test
    fun `real dataset snaps coastal points the simplified outline cuts off`() {
        val r = realResolver()
        // Citrus County, FL shoreline: ~60 m outside the dataset's coarse Gulf coast polygon.
        assertResolves(r, 28.909661400433784, -82.69162923967076, "12017", "Citrus")
    }

    @Test
    fun `real dataset returns null offshore and outside the US`() {
        val r = realResolver()
        assertNull(r.resolve(30.0, -140.0)) // Pacific
        assertNull(r.resolve(26.0, -90.0))  // Gulf of Mexico
        assertNull(r.resolve(43.65, -79.38)) // Toronto, Canada
    }

    @Test
    fun `real dataset catalog finds counties and outlines`() {
        val r = realResolver()
        assertEquals("Johnson", r.county("20091")?.name)
        assertEquals("KS", r.county("20091")?.stateAbbr)
        assertNotNull(r.outline("02016")) // Aleutians West
        assertEquals(3221, r.fipsCodes.size)
    }

    @Test
    fun `every county in the dataset has a facts row`() {
        val r = realResolver()
        val factsFile = File("../app/src/main/assets/county-facts.csv")
        assertTrue("facts asset missing at $factsFile", factsFile.exists())
        val facts = factsFile.inputStream().use { CountyFactsTable.parse(it) }
        val missing = r.fipsCodes.filter { facts[it] == null }
        assertEquals("FIPS without a facts row", emptyList<String>(), missing)
    }

    private fun assertResolves(
        r: CountyResolver,
        lat: Double,
        lng: Double,
        expectedFips: String,
        expectedName: String,
    ) {
        val county = r.resolve(lat, lng)
        assertNotNull("expected $expectedName at $lat,$lng", county)
        assertEquals("FIPS at $lat,$lng", expectedFips, county!!.fips)
        assertEquals("name at $lat,$lng", expectedName, county.name)
    }
}
