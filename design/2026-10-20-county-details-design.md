# County Details: Outline, Census Facts, and Things-to-Do Links

- **Date:** 2026-10-20
- **Status:** Approved design, pending implementation plan
- **Branch:** `feat/county-details`

## Goal

Help road-trippers discover the county they just entered. On each crossing, show the
county's outline and a few fun facts, and offer one-tap links to things to do there.

### Success criteria

- The crossing notification shows the county outline, population, and density, plus a
  "Things to do" action that opens Google Maps directly.
- Tapping the notification, or the current county on the status screen, opens a county
  detail screen with the outline, population, land area, density, and links to Maps,
  Wikipedia, and Wikivoyage.
- The app stays fully offline: no `INTERNET` permission, no API keys, no change to the
  privacy policy or Play data-safety answers.
- A failure anywhere in the enrichment never delays or suppresses the crossing alert.

### Decisions made during brainstorming

| Decision | Choice | Why |
|---|---|---|
| Surfaces | Notification and detail screen | Glanceable alert plus a place to dig in |
| Content source | Link-outs plus bundled Census facts | Keeps the "nothing leaves your phone" promise; works without signal |
| Counties reachable | Current county only | Smallest scope; history is a separate follow-up |
| Notification richness | Outline, facts, "Things to do" action | One tap for a passenger, no app navigation |
| Facts | Population, land area, density | All from Census data; county seat has no clean source |
| Population vintage | 2020 Decennial Census plus crosswalk | Official count; aligns with the 2010-vintage boundaries |
| Facts storage | Separate CSV asset (approach A) | Leaves the 3 MB GeoJSON and resolver hot path untouched |
| Navigation | New `CountyDetailActivity` | Trivial `PendingIntent` deep link; no navigation dependency |

### Out of scope

In-app fetched content (Wikipedia summaries, ranked attractions), crossing history,
county search/browse, county seat, sharing, in-app maps.

## Data

### Source and vintage

The bundled boundary dataset (`app/src/main/assets/counties.geojson`) is 2010 vintage. It
has the 8 old Connecticut counties and pre-2013 Alaska, South Dakota, and Virginia codes.
Current Census population estimates use newer codes, so this feature uses the
**2020 Decennial Census** (P.L. 94-171 redistricting data, total population `P1_001N`, which
also covers Puerto Rico), which still uses Connecticut's old counties, plus a hard-coded crosswalk:

| Boundary FIPS | Name in dataset | 2020 source |
|---|---|---|
| `46113` | Shannon County, SD | `46102` Oglala Lakota County |
| `02270` | Wade Hampton Census Area, AK | `02158` Kusilvak Census Area |
| `02261` | Valdez-Cordova Census Area, AK | sum of `02063` + `02066` |
| `51515` | Bedford city, VA | none (merged into `51019` in 2013); population unknown |

Land area comes from the GeoJSON's existing `CENSUSAREA` property (square miles, 2010).
It describes the same boundaries the app draws, so area and outline stay consistent.

### Generator

`scripts/gen_county_facts.py`, run by hand; its output is committed.

- **Inputs**: the 2020 P.L. 94-171 county population from the Census API (requires a free
  `CENSUS_API_KEY` in the developer's environment; the raw response is cached in git-ignored
  `data/`) and the bundled `counties.geojson`. The script header pins the exact URL and vintage.
- **Output:** `app/src/main/assets/county-facts.csv`, UTF-8, header row, one row per FIPS
  in the GeoJSON (~3,221 rows, ~80 KB):

  ```
  fips,kind,population,land_sq_mi
  20091,County,609863,473.375
  51515,city,,
  ```

  - `kind` is the GeoJSON `LSAD` value mapped to a readable form ("County", "Parish",
    "Borough", "city", and `Muno`/`CA`/`Cty&Bor`/`Muny` to "Municipio"/"Census Area"/
    "City and Borough"/"Municipality"); empty for DC and Carson City.
  - An empty field means unknown.
- **Fails loudly** if any GeoJSON FIPS is neither matched (directly or via the crosswalk)
  nor on the explicit no-data list (`51515` only).

## `:core` changes

All pure Kotlin, no Android dependencies, tested on the JVM.

### `CountyFacts` and `CountyFactsTable`

```kotlin
data class CountyFacts(
    val kind: String?,          // LSAD, e.g. "County", "Parish"
    val population: Int?,
    val landSqMi: Double?,
) {
    val density: Double? // population / landSqMi; null if either is null or area is 0
}

class CountyFactsTable {
    fun get(fips: String): CountyFacts?
    companion object { fun parse(input: InputStream): CountyFactsTable }
}
```

`parse` skips malformed rows rather than throwing.

### `CountyCatalog`

A new interface, separate from `CountyResolver` so `resolve()`'s contract is unchanged:

```kotlin
interface CountyCatalog {
    fun county(fips: String): County?
    /** Every ring of every sub-polygon, as flat [lng, lat, ...] arrays; null if unknown. */
    fun outline(fips: String): List<DoubleArray>?
}
```

`GeoJsonCountyResolver` implements both. During the existing load it also builds a
`fips -> (County, rings)` map, reusing the same ring arrays already held by
`CountyPolygon`, so the extra memory is negligible.

### `OutlineProjection`

```kotlin
object OutlineProjection {
    /** Projects rings into a width x height box (Y down), centered, aspect-preserving. */
    fun fit(rings: List<DoubleArray>, width: Float, height: Float, padding: Float): List<FloatArray>
}
```

- Equirectangular, with longitude scaled by `cos(midLat)` so counties aren't stretched.
- **Antimeridian:** if the county's longitude span exceeds 180 degrees (Aleutians West,
  `02016`), positive longitudes are shifted by -360 before projecting. This fixes outline
  drawing only; the resolver's antimeridian limitation remains a known issue.

## `:app` changes

### `CountyRepository`

- `suspend fun facts(): CountyFactsTable`: lazy load of `county-facts.csv` on
  `Dispatchers.IO`, same mutex pattern as `resolver()`.
- `suspend fun catalog(): CountyCatalog`: returns the already-loaded resolver.

### `CountyLinks`

Pure helpers that build URLs from a `County` and its `kind`. All query values are
URL-encoded.

| Link | URL |
|---|---|
| Things to do | `https://www.google.com/maps/search/?api=1&query=things to do in {Name} {Kind}, {ST}` |
| Wikipedia | `https://en.wikipedia.org/wiki/Special:Search?search={Name} {Kind}, {State}` |
| Wikivoyage | `https://en.wikivoyage.org/wiki/Special:Search?search={Name} {Kind}, {State}` |

When `kind` is unknown it is omitted. `Special:Search` goes straight to the article on an
exact match, which avoids guessing article titles (disambiguation, independent cities).

### `OutlineBitmaps`

Renders `OutlineProjection` output into a `Bitmap` using an even-odd `Path` (so holes
render correctly), a semi-transparent fill, and a solid stroke.

- Large icon: square, about 256 px.
- Big picture: 2:1, about 1024 x 512 px.

### Crossing notification

`Notifications.postCrossing(context, from, to, details: CrossingDetails?)`, where
`CrossingDetails` holds the `CountyFacts` and both bitmaps. `CountyTrackingService` builds
it on `Dispatchers.Default` immediately before posting.

- **Collapsed:** outline as the large icon; title unchanged; text adds facts, e.g.
  "Welcome to Johnson County, KS · pop. 609,863 · 1,288/sq mi". Missing facts are omitted.
- **Expanded:** `BigPictureStyle` with the 2:1 outline and `bigLargeIcon(null)`.
- **Action "Things to do":** `PendingIntent` for `ACTION_VIEW` on the Maps URL (opens the
  Maps app if installed, else a browser).
- **Content intent:** opens `CountyDetailActivity` with the FIPS, built with
  `TaskStackBuilder` so Back returns to the status screen. Request codes are keyed by FIPS.
- **Fallback:** if loading facts, the outline, or rendering throws or returns null,
  `details` is null and a text-only notification is posted (no outline or facts). It still
  opens the detail screen and still has the "Things to do" action.

Unchanged: the ongoing service notification, `CROSSING_NOTIFICATION_ID` replace behavior,
notification channels.

### `CountyDetailActivity` and screen

- Manifest: `exported="false"`, `parentActivityName=".MainActivity"`.
- Reads `EXTRA_FIPS`; hosts `CountyDetailScreen`.
- `CountyDetailViewModel` exposes one `StateFlow<CountyDetailUiState>`:
  `Loading | Loaded(county, facts, outline) | NotFound`.
- `CountyDetailScreen` is pure (state in, lambdas out):
  - Top app bar with Up arrow; title like "Johnson County, KS".
  - Outline on a Compose `Canvas`, full width, 4:3 box, `MaterialTheme` colors (dark-mode safe).
  - Facts row: Population, Land area (sq mi), Density (/sq mi), locale-grouped; null facts hidden.
  - Buttons: **Things to do** (filled), **Wikipedia** and **Wikivoyage** (outlined).
  - Footnote: "Population: 2020 U.S. Census. Boundaries and area: U.S. Census Bureau."
  - `NotFound`: short message and a back button.
- Link launches catch `ActivityNotFoundException` and show a toast.

### Status screen

`CurrentCountyCard` becomes clickable (with a chevron) when the current county is known and
opens `CountyDetailActivity` for it. Not clickable while the county is unknown.

## Error handling summary

- Enrichment is best-effort: failures degrade to today's notification and to hidden fields
  on the detail screen.
- Facts parsing is tolerant at runtime; strictness is enforced by tests and the generator.
- Link-outs with no handler show a toast.

## Testing

### `:core` (JVM)

- `CountyFactsTable.parse`: round-trip, empty fields to null, malformed rows skipped,
  `density` computation and null cases.
- Real-asset checks: Los Angeles (`06037`) population about 10.0M; `46113` and `02270`
  have population via the crosswalk; all 8 Connecticut counties have population; every
  GeoJSON FIPS has a facts row, and only `51515` lacks population.
- `CountyCatalog`: `county(fips)` round-trips against the real asset; `outline` returns
  multiple rings for a `MultiPolygon` county and null for an unknown FIPS.
- `OutlineProjection`: output within bounds, aspect preserved, centered; Aleutians West
  (`02016`) has a shifted longitude span of about 21 degrees, not about 359.

### `:app` (Robolectric)

- `CountyLinks`: encoding of spaces, apostrophes ("Prince George's"), and non-ASCII
  ("Doña Ana"); `kind` omitted when null.
- `postCrossing` with details: large icon set, `BigPictureStyle`, "Things to do" action,
  content intent targets `CountyDetailActivity` with the right FIPS. With null details:
  text-only, still with the action and detail intent.
- `OutlineBitmaps`: expected dimensions.
- `CountyDetailViewModel`: `Loading -> Loaded` and `NotFound` with a fake catalog.

### Manual

- Real crossing: collapsed and expanded notification; action opens Maps.
- Cold start from a notification tap, then Back lands on the status screen.
- Dark mode on both surfaces.
- A county with holes or multiple parts renders correctly.

## Release impact

- No new permissions; privacy policy and Play data-safety answers unchanged.
- APK grows by about 80 KB raw (about 25 KB compressed); facts map uses well under 1 MB.
- Census data is public domain; the footnote is courtesy attribution.
- Docs to update: `CLAUDE.md` (Dataset, Architecture Notes, Known Issues), README feature
  list. Store "What's new" notes only on request.
- Delivered as one PR: `feat(app): county details with outline, Census facts, and things-to-do links`.
