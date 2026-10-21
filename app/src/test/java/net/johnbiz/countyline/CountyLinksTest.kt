package net.johnbiz.countyline

import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.county.CountyLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class CountyLinksTest {

    private val johnson = County("20091", "Johnson", "20", "Kansas", "KS")

    @Test
    fun `things to do searches Google Maps with the full county name`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=things%20to%20do%20in%20Johnson%20County%2C%20KS",
            CountyLinks.thingsToDo(johnson, "County"),
        )
    }

    @Test
    fun `wikipedia and wikivoyage search by name and state`() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Special:Search?search=Johnson%20County%2C%20Kansas",
            CountyLinks.wikipedia(johnson, "County"),
        )
        assertEquals(
            "https://en.wikivoyage.org/wiki/Special:Search?search=Johnson%20County%2C%20Kansas",
            CountyLinks.wikivoyage(johnson, "County"),
        )
    }

    @Test
    fun `apostrophes and non-ASCII letters are encoded`() {
        val pg = County("24033", "Prince George's", "24", "Maryland", "MD")
        assertEquals(
            "https://en.wikipedia.org/wiki/Special:Search?search=Prince%20George%27s%20County%2C%20Maryland",
            CountyLinks.wikipedia(pg, "County"),
        )
        val donaAna = County("35013", "Doña Ana", "35", "New Mexico", "NM")
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=things%20to%20do%20in%20Do%C3%B1a%20Ana%20County%2C%20NM",
            CountyLinks.thingsToDo(donaAna, "County"),
        )
    }

    @Test
    fun `unknown kind is left out`() {
        val dc = County("11001", "District of Columbia", "11", "District of Columbia", "DC")
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=things%20to%20do%20in%20District%20of%20Columbia%2C%20DC",
            CountyLinks.thingsToDo(dc, null),
        )
    }
}
