package net.johnbiz.countyline.core

/**
 * Resolves a geographic coordinate to the US county that contains it.
 *
 * Implementations must be safe to call from multiple threads once constructed,
 * and [resolve] must be cheap enough to call on every location update.
 */
interface CountyResolver {
    /**
     * @return the county containing ([lat], [lng]), or `null` if the point is
     * outside every county in the dataset (open water, outside the US, or an
     * unmapped territory).
     */
    fun resolve(lat: Double, lng: Double): County?
}
