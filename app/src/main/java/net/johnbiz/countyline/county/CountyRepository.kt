package net.johnbiz.countyline.county

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.johnbiz.countyline.core.CountyCatalog
import net.johnbiz.countyline.core.CountyFactsTable
import net.johnbiz.countyline.core.CountyResolver
import net.johnbiz.countyline.core.GeoJsonCountyResolver

/**
 * Owns the (heavy, ~3 MB, load-once) [CountyResolver] built from the bundled
 * `counties.geojson` asset. First access parses the dataset off the main
 * thread; subsequent calls reuse the same instance.
 *
 * The asset is stored uncompressed on purpose: AAPT auto-gunzips (and renames)
 * any `*.gz` asset at build time, and the APK's own zip entry compresses this
 * file to ~1 MB regardless.
 */
class CountyRepository(private val context: Context) {

    private val resolverMutex = Mutex()
    private val factsMutex = Mutex()
    @Volatile private var resolver: GeoJsonCountyResolver? = null
    @Volatile private var facts: CountyFactsTable? = null

    suspend fun resolver(): CountyResolver = loadedResolver()

    /** County lookup and outlines; the same instance as [resolver]. */
    suspend fun catalog(): CountyCatalog = loadedResolver()

    /** Bundled Census facts (`county-facts.csv`), loaded once on first use. */
    suspend fun facts(): CountyFactsTable {
        facts?.let { return it }
        return factsMutex.withLock {
            facts ?: withContext(Dispatchers.IO) {
                context.assets.open(FACTS_ASSET).use { CountyFactsTable.parse(it) }
            }.also { facts = it }
        }
    }

    private suspend fun loadedResolver(): GeoJsonCountyResolver {
        resolver?.let { return it }
        return resolverMutex.withLock {
            resolver ?: withContext(Dispatchers.IO) {
                context.assets.open(ASSET_NAME).use { GeoJsonCountyResolver.fromGeoJson(it) }
            }.also { resolver = it }
        }
    }

    companion object {
        private const val ASSET_NAME = "counties.geojson"
        private const val FACTS_ASSET = "county-facts.csv"

        // Holds the application Context only (see get()), so this is not a leak.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: CountyRepository? = null

        fun get(context: Context): CountyRepository =
            instance ?: synchronized(this) {
                instance ?: CountyRepository(context.applicationContext).also { instance = it }
            }
    }
}
