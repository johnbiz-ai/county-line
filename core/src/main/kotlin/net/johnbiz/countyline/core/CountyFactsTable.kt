package net.johnbiz.countyline.core

import java.io.InputStream

/**
 * [CountyFacts] keyed by 5-digit FIPS, parsed from the bundled `county-facts.csv`
 * (`fips,kind,population,land_sq_mi`; an empty field means unknown).
 */
class CountyFactsTable private constructor(private val rows: Map<String, CountyFacts>) {

    val size: Int get() = rows.size

    val fipsCodes: Set<String> get() = rows.keys

    operator fun get(fips: String): CountyFacts? = rows[fips]

    companion object {
        private val FIPS = Regex("""\d{5}""")

        /** Parses the CSV. The header and malformed rows are skipped rather than failing the load. */
        fun parse(input: InputStream): CountyFactsTable {
            val rows = HashMap<String, CountyFacts>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val cols = line.split(',')
                    if (cols.size != 4 || !FIPS.matches(cols[0])) continue
                    val (fips, kind, popRaw, areaRaw) = cols
                    val population = popRaw.toIntOrNull()
                    val area = areaRaw.toDoubleOrNull()
                    if (popRaw.isNotEmpty() && population == null) continue
                    if (areaRaw.isNotEmpty() && area == null) continue
                    rows[fips] = CountyFacts(kind.ifEmpty { null }, population, area)
                }
            }
            return CountyFactsTable(rows)
        }
    }
}
