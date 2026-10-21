package net.johnbiz.countyline.core

/** Lookup of counties and their boundary geometry by 5-digit FIPS code. */
interface CountyCatalog {
    /** @return the county for [fips], or `null` if it is not in the dataset. */
    fun county(fips: String): County?

    /**
     * Every ring of every sub-polygon of the county, as flat `[lng, lat, lng, lat, ...]`
     * arrays (outer rings and holes alike), or `null` if unknown. The arrays are shared with
     * the resolver's index: callers must not modify them.
     */
    fun outline(fips: String): List<DoubleArray>?
}
