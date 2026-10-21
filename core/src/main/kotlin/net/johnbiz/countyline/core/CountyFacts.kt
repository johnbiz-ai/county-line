package net.johnbiz.countyline.core

/**
 * Bundled Census facts for one county. Every field may be unknown (`null`); callers hide
 * unknown values rather than showing zero.
 */
data class CountyFacts(
    /** Readable county type from the dataset, e.g. `"County"`, `"Parish"`, `"city"`. */
    val kind: String?,
    /** 2020 Census total population. */
    val population: Int?,
    /** Land area in square miles. */
    val landSqMi: Double?,
) {
    /** People per square mile, or `null` if either input is unknown or the area is zero. */
    val density: Double?
        get() {
            val people = population ?: return null
            val area = landSqMi ?: return null
            return if (area > 0.0) people / area else null
        }
}

/** Name with its type suffix, e.g. `"Orleans Parish"`; just [County.name] when [kind] is blank. */
fun County.fullName(kind: String?): String = if (kind.isNullOrBlank()) name else "$name $kind"
