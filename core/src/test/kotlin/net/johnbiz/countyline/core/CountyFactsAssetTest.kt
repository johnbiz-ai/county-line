package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks against the exact facts asset the app bundles. A missing asset fails, not skips: the app ships it. */
class CountyFactsAssetTest {

    private val asset = File("../app/src/main/assets/county-facts.csv")

    private fun table(): CountyFactsTable {
        assertTrue("facts asset missing at $asset; run scripts/gen_county_facts.py", asset.exists())
        return asset.inputStream().use { CountyFactsTable.parse(it) }
    }

    @Test
    fun `has a row for every county-equivalent`() {
        assertEquals(3221, table().size)
    }

    @Test
    fun `Los Angeles has about ten million people`() {
        val population = table()["06037"]!!.population!!
        assertTrue("LA population $population", population in 9_900_000..10_100_000)
    }

    @Test
    fun `renamed and split counties get population through the crosswalk`() {
        val t = table()
        for (fips in listOf("46113", "02270", "02261")) {
            assertNotNull("population for $fips", t[fips]!!.population)
        }
    }

    @Test
    fun `all eight old Connecticut counties have population`() {
        val t = table()
        for (fips in listOf("09001", "09003", "09005", "09007", "09009", "09011", "09013", "09015")) {
            assertNotNull("population for $fips", t[fips]!!.population)
        }
    }

    @Test
    fun `only Bedford city lacks population`() {
        val t = table()
        assertEquals(setOf("51515"), t.fipsCodes.filter { t[it]!!.population == null }.toSet())
        assertNull(t["51515"]!!.population)
    }

    @Test
    fun `kinds are spelled out`() {
        val t = table()
        assertEquals("Parish", t["22071"]!!.kind)
        assertEquals("Municipio", t["72001"]!!.kind)
        assertEquals("Municipality", t["02020"]!!.kind)
        assertEquals("City and Borough", t["02110"]!!.kind)
        assertNull(t["11001"]!!.kind)
    }
}
