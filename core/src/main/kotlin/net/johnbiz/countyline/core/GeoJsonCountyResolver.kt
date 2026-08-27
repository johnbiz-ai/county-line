package net.johnbiz.countyline.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.floor

/**
 * [CountyResolver] backed by a bundled GeoJSON `FeatureCollection` of US county
 * boundary polygons (the `plotly/datasets` `geojson-counties-fips` file, keyed
 * by 5-digit FIPS code).
 *
 * ### Spatial index
 * Testing all ~3,200 county polygons on every location update is wasteful. On
 * construction we bucket every polygon into a coarse grid of [CELL_DEGREES]°×
 * [CELL_DEGREES]° lat/lng cells (a polygon is added to every cell its bounding
 * box overlaps). [resolve] then only bounding-box-tests and ray-casts the
 * handful of polygons registered in the one cell containing the query point.
 *
 * ### Known limitations
 * - Polygons that cross the ±180° antimeridian (parts of the Alaskan Aleutians)
 *   are not stitched; a query right on that line may miss.
 * - Boundary precision is whatever the source dataset provides; a point within
 *   a few hundred metres of a county line may resolve to the neighbour. The
 *   crossing-detection layer applies hysteresis to absorb this.
 */
class GeoJsonCountyResolver private constructor(
    private val grid: Map<Long, List<CountyPolygon>>,
    /** Number of distinct counties successfully loaded. */
    val countyCount: Int,
) : CountyResolver {

    override fun resolve(lat: Double, lng: Double): County? {
        val candidates = grid[cellKey(cell(lat), cell(lng))] ?: return null
        for (polygon in candidates) {
            if (polygon.contains(lng, lat)) return polygon.county
        }
        return null
    }

    companion object {
        /** Grid cell size in degrees. ~1° ≈ 111 km N/S; a reasonable bucket size for the US. */
        const val CELL_DEGREES: Int = 1

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /** Build a resolver from an uncompressed GeoJSON stream. */
        fun fromGeoJson(input: InputStream): GeoJsonCountyResolver {
            val text = input.bufferedReader().use { it.readText() }
            return fromFeatureCollection(json.decodeFromString<FeatureCollection>(text))
        }

        /** Build a resolver from a gzip-compressed GeoJSON stream (the bundled asset). */
        fun fromGzippedGeoJson(input: InputStream): GeoJsonCountyResolver =
            fromGeoJson(GZIPInputStream(input))

        private fun fromFeatureCollection(fc: FeatureCollection): GeoJsonCountyResolver {
            val grid = HashMap<Long, MutableList<CountyPolygon>>()
            val counties = HashSet<String>()

            for (feature in fc.features) {
                val geometry = feature.geometry ?: continue
                val props = feature.properties
                val fips = feature.id ?: (props.state + props.county)
                val county = County(
                    fips = fips,
                    name = props.name,
                    stateFips = props.state,
                    stateName = UsStates.name(props.state),
                    stateAbbr = UsStates.abbr(props.state),
                )

                for (rings in geometry.polygons()) {
                    if (rings.isEmpty()) continue
                    val polygon = CountyPolygon.of(county, rings)
                    index(grid, polygon)
                    counties += fips
                }
            }

            return GeoJsonCountyResolver(
                grid = grid.mapValues { (_, v) -> v.toList() },
                countyCount = counties.size,
            )
        }

        private fun index(grid: HashMap<Long, MutableList<CountyPolygon>>, polygon: CountyPolygon) {
            val bb = polygon.boundingBox
            for (latCell in cell(bb.minLat)..cell(bb.maxLat)) {
                for (lngCell in cell(bb.minLng)..cell(bb.maxLng)) {
                    grid.getOrPut(cellKey(latCell, lngCell)) { ArrayList() }.add(polygon)
                }
            }
        }

        private fun cell(degrees: Double): Int = floor(degrees / CELL_DEGREES).toInt()

        /** Pack a (lat, lng) cell pair into one key. Range covers the whole globe. */
        private fun cellKey(latCell: Int, lngCell: Int): Long =
            (latCell + 1_000).toLong() * 100_000L + (lngCell + 1_000).toLong()

        /** Convert one GeoJSON coordinate ring to a flat `[lng, lat, lng, lat, ...]` array. */
        private fun ring(ring: JsonArray): DoubleArray {
            val out = DoubleArray(ring.size * 2)
            for (i in ring.indices) {
                val position = ring[i].jsonArray
                out[i * 2] = position[0].jsonPrimitive.double
                out[i * 2 + 1] = position[1].jsonPrimitive.double
            }
            return out
        }

        /**
         * Normalize a geometry's `coordinates` to a list of polygons, where each
         * polygon is a list of rings (outer ring first).
         */
        private fun Geometry.polygons(): List<List<DoubleArray>> = when (type) {
            "Polygon" -> listOf(coordinates.jsonArray.map { ring(it.jsonArray) })
            "MultiPolygon" -> coordinates.jsonArray.map { poly ->
                poly.jsonArray.map { ring(it.jsonArray) }
            }
            else -> emptyList()
        }
    }

    @Serializable
    private class FeatureCollection(val features: List<Feature> = emptyList())

    @Serializable
    private class Feature(
        val properties: Properties,
        val geometry: Geometry? = null,
        val id: String? = null,
    )

    @Serializable
    private class Properties(
        @SerialName("STATE") val state: String,
        @SerialName("COUNTY") val county: String,
        @SerialName("NAME") val name: String,
    )

    @Serializable
    private class Geometry(
        val type: String,
        val coordinates: JsonElement,
    )
}
