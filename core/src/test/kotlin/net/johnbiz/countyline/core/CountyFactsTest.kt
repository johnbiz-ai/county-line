package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CountyFactsTest {

    private fun parse(text: String) = CountyFactsTable.parse(text.byteInputStream())

    @Test
    fun `parses rows and treats empty fields as unknown`() {
        val table = parse(
            "fips,kind,population,land_sq_mi\n" +
                "20091,County,609863,473.375\n" +
                "51515,city,,6.88\n",
        )
        assertEquals(2, table.size)
        assertEquals(CountyFacts("County", 609863, 473.375), table["20091"])
        val bedford = table["51515"]!!
        assertEquals("city", bedford.kind)
        assertNull(bedford.population)
        assertEquals(6.88, bedford.landSqMi!!, 1e-9)
    }

    @Test
    fun `empty kind is null`() {
        assertNull(parse("11001,,689545,61.048\n")["11001"]!!.kind)
    }

    @Test
    fun `skips malformed rows without failing`() {
        val table = parse(
            "20091,County,609863,473.375\n" +
                "123,County,1,1\n" +          // FIPS not 5 digits
                "20093,County,abc,1\n" +      // non-numeric population
                "20095,County,1\n" +          // too few columns
                "20097,County,1,2,3\n" +      // too many columns
                "\n",
        )
        assertEquals(setOf("20091"), table.fipsCodes)
    }

    @Test
    fun `unknown FIPS returns null`() {
        assertNull(parse("20091,County,609863,473.375\n")["99999"])
    }

    @Test
    fun `density divides population by land area`() {
        assertEquals(1288.33, CountyFacts("County", 609863, 473.375).density!!, 0.01)
    }

    @Test
    fun `density is null when an input is missing or area is zero`() {
        assertNull(CountyFacts("city", null, 6.88).density)
        assertNull(CountyFacts("County", 100, null).density)
        assertNull(CountyFacts("County", 100, 0.0).density)
    }

    @Test
    fun `fullName appends the kind when present`() {
        val orleans = County("22071", "Orleans", "22", "Louisiana", "LA")
        assertEquals("Orleans Parish", orleans.fullName("Parish"))
        assertEquals("Orleans", orleans.fullName(null))
        assertEquals("Orleans", orleans.fullName(""))
    }

    @Test
    fun `count uses locale grouping`() {
        assertEquals("609,863", CountyFactsFormat.count(609863, Locale.US))
    }

    @Test
    fun `measure shows whole numbers from 10 up and one decimal below`() {
        assertEquals("1,288", CountyFactsFormat.measure(1288.33, Locale.US))
        assertEquals("0.4", CountyFactsFormat.measure(0.42, Locale.US))
        assertEquals("6.9", CountyFactsFormat.measure(6.88, Locale.US))
    }

    @Test
    fun `summary joins population and density`() {
        assertEquals(
            "pop. 609,863 · 1,288/sq mi",
            CountyFactsFormat.summary(CountyFacts("County", 609863, 473.375), Locale.US),
        )
    }

    @Test
    fun `summary omits missing facts and never prints null`() {
        assertEquals("pop. 1,234", CountyFactsFormat.summary(CountyFacts("County", 1234, null), Locale.US))
        assertNull(CountyFactsFormat.summary(CountyFacts("city", null, 6.88), Locale.US))
        assertNull(CountyFactsFormat.summary(CountyFacts(null, null, null), Locale.US))
    }
}
