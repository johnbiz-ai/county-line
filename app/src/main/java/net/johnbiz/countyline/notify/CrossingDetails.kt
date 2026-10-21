package net.johnbiz.countyline.notify

import android.graphics.Bitmap
import android.util.Log
import net.johnbiz.countyline.core.CountyCatalog
import net.johnbiz.countyline.core.CountyFacts
import net.johnbiz.countyline.core.CountyFactsTable
import kotlin.coroutines.cancellation.CancellationException

/** Optional enrichment for a crossing notification. Any field may be missing. */
class CrossingDetails(
    val facts: CountyFacts?,
    val outlineIcon: Bitmap?,
    val outlinePicture: Bitmap?,
)

/**
 * Builds [CrossingDetails] for a county. Best-effort: any failure is logged and yields `null`,
 * so the crossing alert still goes out as plain text.
 */
class CrossingDetailsLoader(
    private val facts: suspend () -> CountyFactsTable,
    private val catalog: suspend () -> CountyCatalog,
) {
    suspend fun load(fips: String): CrossingDetails? = try {
        val countyFacts = facts()[fips]
        val rings = catalog().outline(fips)
        val icon = rings?.let { OutlineBitmaps.render(it, OutlineBitmaps.ICON_PX, OutlineBitmaps.ICON_PX) }
        val picture = rings?.let {
            OutlineBitmaps.render(it, OutlineBitmaps.PICTURE_WIDTH_PX, OutlineBitmaps.PICTURE_HEIGHT_PX)
        }
        if (countyFacts == null && icon == null) null else CrossingDetails(countyFacts, icon, picture)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Crossing details unavailable for $fips; posting plain notification", e)
        null
    }

    private companion object {
        const val TAG = "CrossingDetails"
    }
}
