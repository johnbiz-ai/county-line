package net.johnbiz.countyline.core

/**
 * A single US county (or county-equivalent: parish, borough, independent city, municipio).
 */
data class County(
    /** 5-digit state+county FIPS code, e.g. `"36061"`. Stable identifier used for persistence. */
    val fips: String,
    /** County name without any type suffix, e.g. `"New York"`, `"Orleans"`. */
    val name: String,
    /** 2-digit state FIPS code, e.g. `"36"`. */
    val stateFips: String,
    /** Full state name, e.g. `"New York"`. Falls back to the FIPS code if unknown. */
    val stateName: String,
    /** USPS state abbreviation, e.g. `"NY"`. Falls back to the FIPS code if unknown. */
    val stateAbbr: String,
) {
    /** Short label for notifications and UI, e.g. `"New York, NY"`. */
    val displayName: String get() = "$name, $stateAbbr"
}
