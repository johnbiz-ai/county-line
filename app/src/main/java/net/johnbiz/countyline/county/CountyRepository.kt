package net.johnbiz.countyline.county

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val mutex = Mutex()
    @Volatile private var resolver: CountyResolver? = null

    suspend fun resolver(): CountyResolver {
        resolver?.let { return it }
        return mutex.withLock {
            resolver ?: load().also { resolver = it }
        }
    }

    private suspend fun load(): CountyResolver = withContext(Dispatchers.IO) {
        context.assets.open(ASSET_NAME).use { GeoJsonCountyResolver.fromGeoJson(it) }
    }

    companion object {
        private const val ASSET_NAME = "counties.geojson"

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
