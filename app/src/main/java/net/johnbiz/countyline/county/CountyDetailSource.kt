package net.johnbiz.countyline.county

import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CountyFacts
import kotlin.coroutines.cancellation.CancellationException

/** Everything the detail screen shows for one county. */
data class CountyDetail(
    val county: County,
    val facts: CountyFacts?,
    val outline: List<DoubleArray>?,
)

fun interface CountyDetailSource {
    /** @return the county's details, or `null` if [fips] is not in the dataset. */
    suspend fun load(fips: String): CountyDetail?
}

/** Detail source backed by the bundled dataset. Missing facts degrade to `null`, not an error. */
fun CountyRepository.detailSource() = CountyDetailSource { fips ->
    val catalog = catalog()
    val county = catalog.county(fips) ?: return@CountyDetailSource null
    val facts = try {
        facts()[fips]
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    CountyDetail(county, facts, catalog.outline(fips))
}
