# County Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On each county crossing, show the county outline, Census facts, and a one-tap "Things to do" link in the notification, and add a county detail screen with the outline, facts, and Maps/Wikipedia/Wikivoyage links.

**Architecture:** A hand-run Python script joins 2020 Census population to the bundled boundary FIPS codes and writes a small `county-facts.csv` asset. Pure `:core` code parses it, exposes county lookup and outlines from the already-loaded GeoJSON (`CountyCatalog`), and projects outlines into a box (`OutlineProjection`). `:app` renders outlines to bitmaps for the notification and to a Compose `Canvas` for a new `CountyDetailActivity`.

**Tech Stack:** Kotlin 2.4, AGP 9, Jetpack Compose Material 3, JUnit 4, Robolectric, kotlinx-coroutines-test, Python 3 stdlib (generator only).

**Spec:** `design/2026-10-20-county-details-design.md`

## Global Constraints

- Work on branch `feat/county-details` (already created). **Never stage, commit, stash, or push.** Every task ends with a checkpoint (`git status --short`) instead of a commit; the user manages git.
- Run Gradle through mise: `mise exec -- ./gradlew <tasks>` from the repo root.
- `:core` must stay Android-free (pure Kotlin/JVM).
- No `INTERNET` permission, no API keys in the app, no new Gradle dependencies (Material icons are **not** on the classpath; use vector drawables).
- `minSdk = 26`: do not use APIs newer than 26 without a version check (for example, use `URLEncoder.encode(String, "UTF-8")`, not the `Charset` overload).
- Population source: 2020 Census P.L. 94-171 redistricting data, field `P1_001N`, plus the crosswalk `46113←46102`, `02270←02158`, `02261←02063+02066`, `51515→none`.
- Land area source: the GeoJSON `CENSUSAREA` property (square miles).
- Facts asset: `app/src/main/assets/county-facts.csv`, header `fips,kind,population,land_sq_mi`, empty field means unknown.
- Notification bitmaps: large icon about 256 px square; big picture about 1024 x 512.
- Detail outline box: full width, 4:3.
- Footnote text, verbatim: "Population: 2020 U.S. Census. Boundaries and area: U.S. Census Bureau."
- American English everywhere; no em dashes in code comments, strings, or docs.
- Kotlin official style; match the comment density and idioms of neighboring code.

## Review Focus

1. **Partial facts** (Bedford city `51515` has area but no population; any county could lack a field): notification text and the detail screen omit the missing parts and never show "null", "0", or a dangling separator. Pinned in Task 2 (`summary` tests) and Task 6 (notification text test).
2. **Degenerate geometry** (a single-point or zero-span ring, a tiny independent city): `OutlineProjection.fit` returns finite coordinates inside the box; no NaN, no division by zero. Pinned in Task 4.
3. **Enrichment failure** (facts asset missing or corrupt, catalog throws): the crossing alert still posts, text-only. Pinned in Task 6 (`CrossingDetailsLoader` returns null on exception; `postCrossing` with null details).
4. **Cold start from a notification tap** (process dead; the 3 MB dataset takes a moment to load): the screen shows Loading, then the county; an unknown FIPS shows NotFound instead of crashing. Pinned in Task 7 (`CountyDetailViewModel` tests).
5. **Several crossings in one trip**: each county's content intent must carry its own FIPS, not the most recent one (PendingIntent extras collision). Pinned in Task 6 (two counties, two distinct intents).

## Clarifications to the spec (made while planning)

- The Census API now requires a free key, so the generator reads `CENSUS_API_KEY` from the environment (developer machine only; never shipped or committed). The source table is P.L. 94-171 (`dec/pl`) rather than DHC, since it covers Puerto Rico.
- The GeoJSON `LSAD` values are abbreviated (`Muno`, `CA`, `Cty&Bor`, `Muny`, and empty for DC and Carson City). The generator maps them to readable kinds (see Task 1).
- When `details` is null, the notification is text-only as before, but it **still** opens the detail screen and still has the "Things to do" action; neither depends on the enrichment.
- The facts summary line ("pop. 609,863 · 1,288/sq mi") and number formatting live in `:core` (`CountyFactsFormat`) so they are JVM-testable.

## File Structure

**Create**
- `scripts/gen_county_facts.py`: generator (stdlib only).
- `app/src/main/assets/county-facts.csv`: generated output, committed by the user.
- `core/src/main/kotlin/net/johnbiz/countyline/core/CountyFacts.kt`: `CountyFacts`, `County.fullName`.
- `core/src/main/kotlin/net/johnbiz/countyline/core/CountyFactsTable.kt`: CSV parser and lookup.
- `core/src/main/kotlin/net/johnbiz/countyline/core/CountyFactsFormat.kt`: number formatting and summary line.
- `core/src/main/kotlin/net/johnbiz/countyline/core/CountyCatalog.kt`: interface.
- `core/src/main/kotlin/net/johnbiz/countyline/core/OutlineProjection.kt`: projection.
- `core/src/test/kotlin/net/johnbiz/countyline/core/CountyFactsTest.kt`
- `core/src/test/kotlin/net/johnbiz/countyline/core/CountyFactsAssetTest.kt`
- `core/src/test/kotlin/net/johnbiz/countyline/core/OutlineProjectionTest.kt`
- `app/src/main/java/net/johnbiz/countyline/county/CountyLinks.kt`
- `app/src/main/java/net/johnbiz/countyline/county/CountyDetailSource.kt`
- `app/src/main/java/net/johnbiz/countyline/notify/OutlineBitmaps.kt`
- `app/src/main/java/net/johnbiz/countyline/notify/CrossingDetails.kt`
- `app/src/main/java/net/johnbiz/countyline/CountyDetailActivity.kt`
- `app/src/main/java/net/johnbiz/countyline/ui/CountyDetailViewModel.kt`
- `app/src/main/java/net/johnbiz/countyline/ui/CountyDetailScreen.kt`
- `app/src/main/res/drawable/ic_arrow_back.xml`, `app/src/main/res/drawable/ic_chevron_right.xml`
- `app/src/test/java/net/johnbiz/countyline/CountyLinksTest.kt`
- `app/src/test/java/net/johnbiz/countyline/CrossingNotificationTest.kt`
- `app/src/test/java/net/johnbiz/countyline/CountyDetailViewModelTest.kt`

**Modify**
- `.gitignore` (nothing to add: `data/` is already ignored; verify only)
- `core/src/main/kotlin/net/johnbiz/countyline/core/GeoJsonCountyResolver.kt`: implement `CountyCatalog`.
- `core/src/test/kotlin/net/johnbiz/countyline/core/GeoJsonCountyResolverTest.kt`: catalog tests.
- `app/src/main/java/net/johnbiz/countyline/county/CountyRepository.kt`: `facts()`, `catalog()`.
- `app/src/main/java/net/johnbiz/countyline/notify/Notifications.kt`: rich crossing notification.
- `app/src/main/java/net/johnbiz/countyline/location/CountyTrackingService.kt`: load details before posting.
- `app/src/main/java/net/johnbiz/countyline/ui/StatusScreen.kt`, `app/src/main/java/net/johnbiz/countyline/MainActivity.kt`: clickable county card.
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- `CLAUDE.md`, `README.md`

---

### Task 1: Facts generator and CSV asset

**Files:**
- Create: `scripts/gen_county_facts.py`
- Create (generated): `app/src/main/assets/county-facts.csv`

**Interfaces:**
- Consumes: `app/src/main/assets/counties.geojson` (features with `id`, `properties.LSAD`, `properties.CENSUSAREA`, `properties.NAME`).
- Produces: `county-facts.csv` with header `fips,kind,population,land_sq_mi`, rows sorted by FIPS, `kind` one of `County, Parish, Borough, city, Municipio, Census Area, City and Borough, Municipality` or empty.

- [ ] **Step 1: Write the generator**

```python
#!/usr/bin/env python3
"""Generate app/src/main/assets/county-facts.csv from the bundled boundaries and 2020 Census data.

Population: 2020 Census P.L. 94-171 redistricting data, total population (P1_001N),
  https://api.census.gov/data/2020/dec/pl
Land area: the boundary dataset's own CENSUSAREA property (square miles), so area matches
  the outlines the app draws.

The boundary dataset is 2010 vintage, so a few FIPS codes changed before 2020; CROSSWALK
maps them. Any boundary FIPS that is neither matched nor listed in NO_DATA fails the run.

Usage (from the repo root):
    CENSUS_API_KEY=... python3 scripts/gen_county_facts.py

Get a free key at https://api.census.gov/data/key_signup.html. The raw API response is
cached in data/ (git-ignored), so later runs need no key.
"""
import csv
import json
import os
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GEOJSON = ROOT / "app/src/main/assets/counties.geojson"
OUT = ROOT / "app/src/main/assets/county-facts.csv"
CACHE = ROOT / "data/census-2020-pl-county.json"
URL = "https://api.census.gov/data/2020/dec/pl?get=P1_001N&for=county:*&in=state:*&key={key}"

# GeoJSON LSAD code -> readable kind used in labels and link queries.
KINDS = {
    "County": "County",
    "Parish": "Parish",
    "Borough": "Borough",
    "city": "city",
    "Muno": "Municipio",
    "CA": "Census Area",
    "Cty&Bor": "City and Borough",
    "Muny": "Municipality",
    "": "",
}

# 2010 boundary FIPS -> 2020 FIPS whose populations sum to it.
CROSSWALK = {
    "46113": ["46102"],           # Shannon County, SD -> Oglala Lakota County (2015)
    "02270": ["02158"],           # Wade Hampton Census Area, AK -> Kusilvak (2015)
    "02261": ["02063", "02066"],  # Valdez-Cordova, AK -> Chugach + Copper River (2019)
}

# Boundary FIPS with no 2020 equivalent; population is written as unknown.
NO_DATA = {"51515"}  # Bedford city, VA, merged into Bedford County in 2013


def load_population():
    if not CACHE.exists():
        key = os.environ.get("CENSUS_API_KEY")
        if not key:
            sys.exit(f"CENSUS_API_KEY is not set and {CACHE.relative_to(ROOT)} does not exist")
        with urllib.request.urlopen(URL.format(key=key), timeout=120) as resp:
            body = resp.read()
        json.loads(body)  # fail before caching if the API returned an HTML error page
        CACHE.parent.mkdir(exist_ok=True)
        CACHE.write_bytes(body)
    rows = json.loads(CACHE.read_text(encoding="utf-8"))
    header = rows[0]
    i_pop, i_state, i_county = header.index("P1_001N"), header.index("state"), header.index("county")
    return {r[i_state] + r[i_county]: int(r[i_pop]) for r in rows[1:]}


def main():
    features = json.loads(GEOJSON.read_text(encoding="utf-8"))["features"]
    population = load_population()
    rows, errors = [], []

    for feature in sorted(features, key=lambda f: f["id"]):
        fips, props = feature["id"], feature["properties"]
        lsad = props.get("LSAD", "")
        if lsad not in KINDS:
            errors.append(f"{fips} {props['NAME']}: unknown LSAD {lsad!r}")
            continue

        if fips in NO_DATA:
            pop = None
        elif fips in CROSSWALK:
            sources = CROSSWALK[fips]
            missing = [s for s in sources if s not in population]
            if missing:
                errors.append(f"{fips} {props['NAME']}: crosswalk sources missing {missing}")
                continue
            pop = sum(population[s] for s in sources)
        elif fips in population:
            pop = population[fips]
        else:
            errors.append(f"{fips} {props['NAME']}: no 2020 population and not in CROSSWALK/NO_DATA")
            continue

        area = props.get("CENSUSAREA")
        rows.append([fips, KINDS[lsad], "" if pop is None else pop, "" if area is None else area])

    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(f"{len(errors)} problem(s); no output written")

    with OUT.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh, lineterminator="\n")
        writer.writerow(["fips", "kind", "population", "land_sq_mi"])
        writer.writerows(rows)
    print(f"wrote {len(rows)} rows to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Get the data (human step)**

The executor cannot obtain a Census key. Ask the user to run, in this session:

```
! CENSUS_API_KEY=<their key> python3 scripts/gen_county_facts.py
```

Expected: `wrote 3221 rows to app/src/main/assets/county-facts.csv`.

If the run fails and **every** error line is a `72xxx` FIPS (Puerto Rico), stop and report to the user: the `dec/pl` endpoint did not return Puerto Rico and the source needs a decision. Any other error list: fix the script only if the cause is a script bug; otherwise report the exact error output to the user.

- [ ] **Step 3: Sanity-check the output**

Run:
```bash
head -3 app/src/main/assets/county-facts.csv
grep -E '^(06037|20091|46113|02261|51515|11001|72001),' app/src/main/assets/county-facts.csv
wc -l app/src/main/assets/county-facts.csv
```
Expected: header `fips,kind,population,land_sq_mi`; `06037,County,100xxxxx,...` (about 10.0M); `51515,city,,...` (empty population); `11001,,...` (empty kind); `72001,Municipio,...`; 3222 lines (header plus 3221).

- [ ] **Step 4: Checkpoint**

Run `git status --short`. Expected new files: `scripts/gen_county_facts.py`, `app/src/main/assets/county-facts.csv`. `data/census-2020-pl-county.json` must **not** appear (git-ignored). Do not commit.

---

### Task 2: `CountyFacts`, `CountyFactsTable`, `CountyFactsFormat` in `:core`

**Files:**
- Create: `core/src/main/kotlin/net/johnbiz/countyline/core/CountyFacts.kt`
- Create: `core/src/main/kotlin/net/johnbiz/countyline/core/CountyFactsTable.kt`
- Create: `core/src/main/kotlin/net/johnbiz/countyline/core/CountyFactsFormat.kt`
- Test: `core/src/test/kotlin/net/johnbiz/countyline/core/CountyFactsTest.kt`
- Test: `core/src/test/kotlin/net/johnbiz/countyline/core/CountyFactsAssetTest.kt`

**Interfaces:**
- Consumes: `County` (existing), `county-facts.csv` from Task 1.
- Produces:
  - `data class CountyFacts(val kind: String?, val population: Int?, val landSqMi: Double?)` with `val density: Double?`
  - `fun County.fullName(kind: String?): String`
  - `class CountyFactsTable { val size: Int; val fipsCodes: Set<String>; operator fun get(fips: String): CountyFacts?; companion object { fun parse(input: InputStream): CountyFactsTable } }`
  - `object CountyFactsFormat { fun count(value: Int, locale: Locale): String; fun measure(value: Double, locale: Locale): String; fun summary(facts: CountyFacts, locale: Locale): String? }`

- [ ] **Step 1: Write the failing unit tests**

`core/src/test/kotlin/net/johnbiz/countyline/core/CountyFactsTest.kt`:

```kotlin
package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CountyFactsTest {

    private fun parse(text: String) = CountyFactsTable.parse(text.byteInputStream())

    @Test
    fun `parses rows and treats empty fields as unknown`() {
        val table = parse(
            "fips,kind,population,land_sq_mi\n" +
                "20091,County,609863,473.375\n" +
                "51515,city,,6.88\n",
        )
        assertEquals(2, table.size)
        assertEquals(CountyFacts("County", 609863, 473.375), table["20091"])
        val bedford = table["51515"]!!
        assertEquals("city", bedford.kind)
        assertNull(bedford.population)
        assertEquals(6.88, bedford.landSqMi!!, 1e-9)
    }

    @Test
    fun `empty kind is null`() {
        assertNull(parse("11001,,689545,61.048\n")["11001"]!!.kind)
    }

    @Test
    fun `skips malformed rows without failing`() {
        val table = parse(
            "20091,County,609863,473.375\n" +
                "123,County,1,1\n" +          // FIPS not 5 digits
                "20093,County,abc,1\n" +      // non-numeric population
                "20095,County,1\n" +          // too few columns
                "20097,County,1,2,3\n" +      // too many columns
                "\n",
        )
        assertEquals(setOf("20091"), table.fipsCodes)
    }

    @Test
    fun `unknown FIPS returns null`() {
        assertNull(parse("20091,County,609863,473.375\n")["99999"])
    }

    @Test
    fun `density divides population by land area`() {
        assertEquals(1288.33, CountyFacts("County", 609863, 473.375).density!!, 0.01)
    }

    @Test
    fun `density is null when an input is missing or area is zero`() {
        assertNull(CountyFacts("city", null, 6.88).density)
        assertNull(CountyFacts("County", 100, null).density)
        assertNull(CountyFacts("County", 100, 0.0).density)
    }

    @Test
    fun `fullName appends the kind when present`() {
        val orleans = County("22071", "Orleans", "22", "Louisiana", "LA")
        assertEquals("Orleans Parish", orleans.fullName("Parish"))
        assertEquals("Orleans", orleans.fullName(null))
        assertEquals("Orleans", orleans.fullName(""))
    }

    @Test
    fun `count uses locale grouping`() {
        assertEquals("609,863", CountyFactsFormat.count(609863, Locale.US))
    }

    @Test
    fun `measure shows whole numbers from 10 up and one decimal below`() {
        assertEquals("1,288", CountyFactsFormat.measure(1288.33, Locale.US))
        assertEquals("0.4", CountyFactsFormat.measure(0.42, Locale.US))
        assertEquals("6.9", CountyFactsFormat.measure(6.88, Locale.US))
    }

    @Test
    fun `summary joins population and density`() {
        assertEquals(
            "pop. 609,863 · 1,288/sq mi",
            CountyFactsFormat.summary(CountyFacts("County", 609863, 473.375), Locale.US),
        )
    }

    @Test
    fun `summary omits missing facts and never prints null`() {
        assertEquals("pop. 1,234", CountyFactsFormat.summary(CountyFacts("County", 1234, null), Locale.US))
        assertNull(CountyFactsFormat.summary(CountyFacts("city", null, 6.88), Locale.US))
        assertNull(CountyFactsFormat.summary(CountyFacts(null, null, null), Locale.US))
    }
}
```

`core/src/test/kotlin/net/johnbiz/countyline/core/CountyFactsAssetTest.kt`:

```kotlin
package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Checks against the exact facts asset the app bundles. */
class CountyFactsAssetTest {

    private val asset = File("../app/src/main/assets/county-facts.csv")

    private fun table(): CountyFactsTable {
        assumeTrue("facts asset not present at $asset", asset.exists())
        return asset.inputStream().use { CountyFactsTable.parse(it) }
    }

    @Test
    fun `has a row for every county-equivalent`() {
        assertEquals(3221, table().size)
    }

    @Test
    fun `Los Angeles has about ten million people`() {
        val population = table()["06037"]!!.population!!
        assertTrue("LA population $population", population in 9_900_000..10_100_000)
    }

    @Test
    fun `renamed and split counties get population through the crosswalk`() {
        val t = table()
        for (fips in listOf("46113", "02270", "02261")) {
            assertNotNull("population for $fips", t[fips]!!.population)
        }
    }

    @Test
    fun `all eight old Connecticut counties have population`() {
        val t = table()
        for (fips in listOf("09001", "09003", "09005", "09007", "09009", "09011", "09013", "09015")) {
            assertNotNull("population for $fips", t[fips]!!.population)
        }
    }

    @Test
    fun `only Bedford city lacks population`() {
        val t = table()
        assertEquals(setOf("51515"), t.fipsCodes.filter { t[it]!!.population == null }.toSet())
        assertNull(t["51515"]!!.population)
    }

    @Test
    fun `kinds are spelled out`() {
        val t = table()
        assertEquals("Parish", t["22071"]!!.kind)
        assertEquals("Municipio", t["72001"]!!.kind)
        assertEquals("Municipality", t["02020"]!!.kind)
        assertEquals("City and Borough", t["02110"]!!.kind)
        assertNull(t["11001"]!!.kind)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mise exec -- ./gradlew :core:test --tests 'net.johnbiz.countyline.core.CountyFacts*'`
Expected: compilation failure, `Unresolved reference 'CountyFactsTable'` (and `CountyFacts`, `CountyFactsFormat`, `fullName`).

- [ ] **Step 3: Implement**

`core/src/main/kotlin/net/johnbiz/countyline/core/CountyFacts.kt`:

```kotlin
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
```

`core/src/main/kotlin/net/johnbiz/countyline/core/CountyFactsTable.kt`:

```kotlin
package net.johnbiz.countyline.core

import java.io.InputStream

/**
 * [CountyFacts] keyed by 5-digit FIPS, parsed from the bundled `county-facts.csv`
 * (`fips,kind,population,land_sq_mi`; an empty field means unknown).
 */
class CountyFactsTable private constructor(private val rows: Map<String, CountyFacts>) {

    val size: Int get() = rows.size

    val fipsCodes: Set<String> get() = rows.keys

    operator fun get(fips: String): CountyFacts? = rows[fips]

    companion object {
        private val FIPS = Regex("""\d{5}""")

        /** Parses the CSV. The header and malformed rows are skipped rather than failing the load. */
        fun parse(input: InputStream): CountyFactsTable {
            val rows = HashMap<String, CountyFacts>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val cols = line.split(',')
                    if (cols.size != 4 || !FIPS.matches(cols[0])) continue
                    val (fips, kind, popRaw, areaRaw) = cols
                    val population = popRaw.toIntOrNull()
                    val area = areaRaw.toDoubleOrNull()
                    if (popRaw.isNotEmpty() && population == null) continue
                    if (areaRaw.isNotEmpty() && area == null) continue
                    rows[fips] = CountyFacts(kind.ifEmpty { null }, population, area)
                }
            }
            return CountyFactsTable(rows)
        }
    }
}
```

`core/src/main/kotlin/net/johnbiz/countyline/core/CountyFactsFormat.kt`:

```kotlin
package net.johnbiz.countyline.core

import java.text.NumberFormat
import java.util.Locale

/** Display formatting for [CountyFacts]. */
object CountyFactsFormat {

    fun count(value: Int, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** Whole numbers from 10 up; one decimal below, so sparse areas don't read as "0". */
    fun measure(value: Double, locale: Locale): String {
        val digits = if (value < 10.0) 1 else 0
        return NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)
    }

    /** One-line summary for notifications, e.g. `"pop. 609,863 · 1,288/sq mi"`; `null` if nothing is known. */
    fun summary(facts: CountyFacts, locale: Locale): String? {
        val parts = listOfNotNull(
            facts.population?.let { "pop. ${count(it, locale)}" },
            facts.density?.let { "${measure(it, locale)}/sq mi" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mise exec -- ./gradlew :core:test --tests 'net.johnbiz.countyline.core.CountyFacts*'`
Expected: all tests PASS (the asset tests run, not skipped, because Task 1 produced the CSV).

- [ ] **Step 5: Checkpoint**

Run `git status --short`; expect the five new `:core` files. Do not commit.

---

### Task 3: `CountyCatalog` on `GeoJsonCountyResolver`

**Files:**
- Create: `core/src/main/kotlin/net/johnbiz/countyline/core/CountyCatalog.kt`
- Modify: `core/src/main/kotlin/net/johnbiz/countyline/core/GeoJsonCountyResolver.kt` (constructor at lines 34-38, `fromFeatureCollection` at lines 67-95)
- Test: `core/src/test/kotlin/net/johnbiz/countyline/core/GeoJsonCountyResolverTest.kt`

**Interfaces:**
- Consumes: `CountyFactsTable` (Task 2) in one cross-check test.
- Produces:
  - `interface CountyCatalog { fun county(fips: String): County?; fun outline(fips: String): List<DoubleArray>? }`
  - `GeoJsonCountyResolver : CountyResolver, CountyCatalog` plus `val fipsCodes: Set<String>`

- [ ] **Step 1: Write the failing tests**

Add to `GeoJsonCountyResolverTest.kt` inside the class. Put the synthetic tests after `state names and abbreviations are filled from FIPS` and the real-data tests after `real dataset returns null offshore and outside the US`:

```kotlin
    @Test
    fun `catalog looks up counties by FIPS`() {
        val r = synthetic()
        assertEquals("Alpha", r.county("36001")?.name)
        assertEquals("TX", r.county("48003")?.stateAbbr)
        assertNull(r.county("99999"))
    }

    @Test
    fun `catalog omits features with null geometry`() {
        val r = synthetic()
        assertNull(r.county("51005"))
        assertNull(r.outline("51005"))
        assertEquals(setOf("36001", "06002", "48003", "12004"), r.fipsCodes)
    }

    @Test
    fun `outline returns every ring of every sub-polygon`() {
        val r = synthetic()
        assertEquals(1, r.outline("36001")!!.size)  // Alpha: one simple polygon
        assertEquals(2, r.outline("48003")!!.size)  // Charlie: outer ring + hole
        assertEquals(2, r.outline("12004")!!.size)  // Delta: two landmasses
        assertTrue(r.outline("12004")!!.all { it.size >= 8 && it.size % 2 == 0 })
        assertNull(r.outline("99999"))
    }
```

```kotlin
    @Test
    fun `real dataset catalog finds counties and outlines`() {
        val r = realResolver()
        assertEquals("Johnson", r.county("20091")?.name)
        assertEquals("KS", r.county("20091")?.stateAbbr)
        assertNotNull(r.outline("02016")) // Aleutians West
        assertEquals(3221, r.fipsCodes.size)
    }

    @Test
    fun `every county in the dataset has a facts row`() {
        val r = realResolver()
        val factsFile = File("../app/src/main/assets/county-facts.csv")
        assumeTrue("facts asset not present at $factsFile", factsFile.exists())
        val facts = factsFile.inputStream().use { CountyFactsTable.parse(it) }
        val missing = r.fipsCodes.filter { facts[it] == null }
        assertEquals("FIPS without a facts row", emptyList<String>(), missing)
    }
```

Add `import org.junit.Assert.assertTrue` to the imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mise exec -- ./gradlew :core:test --tests 'net.johnbiz.countyline.core.GeoJsonCountyResolverTest'`
Expected: compilation failure, `Unresolved reference 'county'` / `'outline'` / `'fipsCodes'`.

- [ ] **Step 3: Implement**

`core/src/main/kotlin/net/johnbiz/countyline/core/CountyCatalog.kt`:

```kotlin
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
```

In `GeoJsonCountyResolver.kt`, change the class header and add the catalog members:

```kotlin
class GeoJsonCountyResolver private constructor(
    private val grid: Map<Long, List<CountyPolygon>>,
    private val catalog: Map<String, CatalogEntry>,
) : CountyResolver, CountyCatalog {

    /** Number of distinct counties successfully loaded. */
    val countyCount: Int get() = catalog.size

    /** FIPS codes of every county with geometry. */
    val fipsCodes: Set<String> get() = catalog.keys

    override fun resolve(lat: Double, lng: Double): County? {
        val candidates = grid[cellKey(cell(lat), cell(lng))] ?: return null
        for (polygon in candidates) {
            if (polygon.contains(lng, lat)) return polygon.county
        }
        return null
    }

    override fun county(fips: String): County? = catalog[fips]?.county

    override fun outline(fips: String): List<DoubleArray>? = catalog[fips]?.rings

    private class CatalogEntry(val county: County, val rings: List<DoubleArray>)
```

Replace `fromFeatureCollection` with:

```kotlin
        private fun fromFeatureCollection(fc: FeatureCollection): GeoJsonCountyResolver {
            val grid = HashMap<Long, MutableList<CountyPolygon>>()
            val rings = HashMap<String, MutableList<DoubleArray>>()
            val counties = HashMap<String, County>()

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

                for (polygonRings in geometry.polygons()) {
                    if (polygonRings.isEmpty()) continue
                    index(grid, CountyPolygon.of(county, polygonRings))
                    counties[fips] = county
                    rings.getOrPut(fips) { ArrayList() } += polygonRings
                }
            }

            return GeoJsonCountyResolver(
                grid = grid.mapValues { (_, v) -> v.toList() },
                catalog = counties.mapValues { (fips, county) -> CatalogEntry(county, rings.getValue(fips)) },
            )
        }
```

`CatalogEntry` is a private nested class of the resolver; the companion can reference it because it is in the same class body.

- [ ] **Step 4: Run all `:core` tests**

Run: `mise exec -- ./gradlew :core:test`
Expected: all PASS, including the existing `countyCount` assertions (4 synthetic, 3221 real).

- [ ] **Step 5: Checkpoint**

`git status --short`. Do not commit.

---

### Task 4: `OutlineProjection`

**Files:**
- Create: `core/src/main/kotlin/net/johnbiz/countyline/core/OutlineProjection.kt`
- Test: `core/src/test/kotlin/net/johnbiz/countyline/core/OutlineProjectionTest.kt`

**Interfaces:**
- Consumes: `GeoJsonCountyResolver.outline` (Task 3) in the real-data test.
- Produces: `object OutlineProjection { fun fit(rings: List<DoubleArray>, width: Float, height: Float, padding: Float): List<FloatArray> }`. Output arrays are flat `[x, y, ...]` in pixels, Y down, one per input ring, same point count.

- [ ] **Step 1: Write the failing tests**

```kotlin
package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class OutlineProjectionTest {

    private class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val width get() = maxX - minX
        val height get() = maxY - minY
    }

    private fun bounds(rings: List<FloatArray>): Bounds {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (r in rings) {
            var i = 0
            while (i < r.size) {
                minX = minOf(minX, r[i]); maxX = maxOf(maxX, r[i])
                minY = minOf(minY, r[i + 1]); maxY = maxOf(maxY, r[i + 1])
                i += 2
            }
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    /** Closed axis-aligned rectangle ring. */
    private fun rect(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double) = doubleArrayOf(
        minLng, minLat, maxLng, minLat, maxLng, maxLat, minLng, maxLat, minLng, minLat,
    )

    @Test
    fun `wide shape fills the width and is centered vertically`() {
        val out = OutlineProjection.fit(listOf(rect(0.0, -0.5, 2.0, 0.5)), 100f, 100f, padding = 0f)
        val b = bounds(out)
        assertEquals(0f, b.minX, 0.05f)
        assertEquals(100f, b.maxX, 0.05f)
        assertEquals(25f, b.minY, 0.05f)
        assertEquals(75f, b.maxY, 0.05f)
    }

    @Test
    fun `longitude is scaled by latitude so shapes are not stretched`() {
        // 2 degrees of longitude at 60N is about as wide as 1 degree of latitude.
        val out = OutlineProjection.fit(listOf(rect(0.0, 59.5, 2.0, 60.5)), 100f, 100f, padding = 0f)
        val b = bounds(out)
        assertEquals(1f, b.width / b.height, 0.02f)
    }

    @Test
    fun `padding is respected`() {
        val b = bounds(OutlineProjection.fit(listOf(rect(0.0, 0.0, 1.0, 1.0)), 100f, 100f, padding = 10f))
        assertTrue(b.minX >= 9.99f && b.minY >= 9.99f && b.maxX <= 90.01f && b.maxY <= 90.01f)
    }

    @Test
    fun `north is up`() {
        // Triangle with its apex at the northernmost latitude.
        val ring = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.5, 1.0, 0.0, 0.0)
        val out = OutlineProjection.fit(listOf(ring), 100f, 100f, padding = 0f).single()
        val apexY = out[5]
        assertEquals(bounds(listOf(out)).minY, apexY, 0.001f)
    }

    @Test
    fun `every ring is projected with the same transform`() {
        val outer = rect(0.0, 0.0, 4.0, 4.0)
        val hole = rect(1.0, 1.0, 3.0, 3.0)
        val out = OutlineProjection.fit(listOf(outer, hole), 100f, 100f, padding = 0f)
        assertEquals(2, out.size)
        val o = bounds(listOf(out[0])); val h = bounds(listOf(out[1]))
        assertTrue(h.minX > o.minX && h.maxX < o.maxX && h.minY > o.minY && h.maxY < o.maxY)
    }

    @Test
    fun `shape crossing the antimeridian is unwrapped`() {
        // 179E to 179W is 2 degrees wide, not 358.
        val ring = doubleArrayOf(179.0, 50.0, -179.0, 50.0, -179.0, 51.0, 179.0, 51.0, 179.0, 50.0)
        val b = bounds(OutlineProjection.fit(listOf(ring), 100f, 100f, padding = 0f))
        assertTrue("height ${b.height} should be most of the box", b.height > 50f)
    }

    @Test
    fun `degenerate input stays finite and inside the box`() {
        val point = doubleArrayOf(-77.0, 38.0, -77.0, 38.0)
        val line = doubleArrayOf(-77.0, 38.0, -76.0, 38.0)
        for (ring in listOf(point, line)) {
            val out = OutlineProjection.fit(listOf(ring), 100f, 50f, padding = 4f).single()
            assertTrue(out.all { it.isFinite() && it >= 0f && it <= 100f })
        }
        assertEquals(emptyList<FloatArray>(), OutlineProjection.fit(emptyList(), 100f, 100f, 0f))
    }

    @Test
    fun `real Aleutians West outline is compact`() {
        val asset = File("../app/src/main/assets/counties.geojson")
        assumeTrue(asset.exists())
        val resolver = asset.inputStream().use { GeoJsonCountyResolver.fromGeoJson(it) }
        val b = bounds(OutlineProjection.fit(resolver.outline("02016")!!, 200f, 100f, padding = 0f))
        // Unwrapped it spans about 21 degrees of longitude; wrapped it would be a sliver.
        assertTrue("height ${b.height}", b.height > 20f)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mise exec -- ./gradlew :core:test --tests 'net.johnbiz.countyline.core.OutlineProjectionTest'`
Expected: compilation failure, `Unresolved reference 'OutlineProjection'`.

- [ ] **Step 3: Implement**

```kotlin
package net.johnbiz.countyline.core

import kotlin.math.cos
import kotlin.math.min

/**
 * Fits county rings into a pixel box for drawing: equirectangular, with longitude scaled by
 * the cosine of the middle latitude so shapes keep their real proportions, Y pointing down,
 * centered and aspect-preserving.
 *
 * A county whose longitudes span more than 180 degrees is assumed to cross the antimeridian
 * (Aleutians West), and its eastern-hemisphere longitudes are shifted by -360 first.
 */
object OutlineProjection {

    private const val MIN_SPAN = 1e-9

    fun fit(rings: List<DoubleArray>, width: Float, height: Float, padding: Float): List<FloatArray> {
        if (rings.none { it.size >= 2 }) return emptyList()

        var minLng = Double.POSITIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        forEachPoint(rings) { lng, lat ->
            minLng = min(minLng, lng); maxLng = maxOf(maxLng, lng)
            minLat = min(minLat, lat); maxLat = maxOf(maxLat, lat)
        }

        val unwrap = maxLng - minLng > 180.0
        if (unwrap) {
            minLng = Double.POSITIVE_INFINITY
            maxLng = Double.NEGATIVE_INFINITY
            forEachPoint(rings) { lng, _ ->
                val x = unwrapped(lng)
                minLng = min(minLng, x); maxLng = maxOf(maxLng, x)
            }
        }

        val kx = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLng - minLng) * kx).coerceAtLeast(MIN_SPAN)
        val spanY = (maxLat - minLat).coerceAtLeast(MIN_SPAN)
        val availW = (width - 2 * padding).coerceAtLeast(0f).toDouble()
        val availH = (height - 2 * padding).coerceAtLeast(0f).toDouble()
        val scale = min(availW / spanX, availH / spanY)
        val offX = padding + (availW - spanX * scale) / 2
        val offY = padding + (availH - spanY * scale) / 2

        return rings.map { ring ->
            FloatArray(ring.size).also { out ->
                var i = 0
                while (i + 1 < ring.size) {
                    val lng = if (unwrap) unwrapped(ring[i]) else ring[i]
                    out[i] = (offX + (lng - minLng) * kx * scale).toFloat()
                    out[i + 1] = (offY + (maxLat - ring[i + 1]) * scale).toFloat()
                    i += 2
                }
            }
        }
    }

    private fun unwrapped(lng: Double): Double = if (lng > 0) lng - 360.0 else lng

    private inline fun forEachPoint(rings: List<DoubleArray>, action: (lng: Double, lat: Double) -> Unit) {
        for (ring in rings) {
            var i = 0
            while (i + 1 < ring.size) {
                action(ring[i], ring[i + 1])
                i += 2
            }
        }
    }
}
```

Note on the degenerate case: with both spans clamped to `MIN_SPAN`, `spanX * scale` equals the limiting available dimension, so offsets stay within `[padding, size - padding]` and every output is finite.

- [ ] **Step 4: Run to verify pass**

Run: `mise exec -- ./gradlew :core:test`
Expected: all PASS.

- [ ] **Step 5: Checkpoint**

`git status --short`. Do not commit.

---

### Task 5: Repository access and `CountyLinks`

**Files:**
- Modify: `app/src/main/java/net/johnbiz/countyline/county/CountyRepository.kt`
- Create: `app/src/main/java/net/johnbiz/countyline/county/CountyLinks.kt`
- Test: `app/src/test/java/net/johnbiz/countyline/CountyLinksTest.kt`

**Interfaces:**
- Consumes: `CountyFactsTable.parse`, `CountyCatalog`, `GeoJsonCountyResolver`, `County.fullName` (Tasks 2-3).
- Produces:
  - `CountyRepository.facts(): CountyFactsTable` (suspend), `CountyRepository.catalog(): CountyCatalog` (suspend); `resolver()` unchanged in signature.
  - `object CountyLinks { fun thingsToDo(county: County, kind: String?): String; fun wikipedia(county: County, kind: String?): String; fun wikivoyage(county: County, kind: String?): String }`

- [ ] **Step 1: Write the failing test** (plain JUnit, no Robolectric)

```kotlin
package net.johnbiz.countyline

import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.county.CountyLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class CountyLinksTest {

    private val johnson = County("20091", "Johnson", "20", "Kansas", "KS")

    @Test
    fun `things to do searches Google Maps with the full county name`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=things%20to%20do%20in%20Johnson%20County%2C%20KS",
            CountyLinks.thingsToDo(johnson, "County"),
        )
    }

    @Test
    fun `wikipedia and wikivoyage search by name and state`() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Special:Search?search=Johnson%20County%2C%20Kansas",
            CountyLinks.wikipedia(johnson, "County"),
        )
        assertEquals(
            "https://en.wikivoyage.org/wiki/Special:Search?search=Johnson%20County%2C%20Kansas",
            CountyLinks.wikivoyage(johnson, "County"),
        )
    }

    @Test
    fun `apostrophes and non-ASCII letters are encoded`() {
        val pg = County("24033", "Prince George's", "24", "Maryland", "MD")
        assertEquals(
            "https://en.wikipedia.org/wiki/Special:Search?search=Prince%20George%27s%20County%2C%20Maryland",
            CountyLinks.wikipedia(pg, "County"),
        )
        val donaAna = County("35013", "Doña Ana", "35", "New Mexico", "NM")
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=things%20to%20do%20in%20Do%C3%B1a%20Ana%20County%2C%20NM",
            CountyLinks.thingsToDo(donaAna, "County"),
        )
    }

    @Test
    fun `unknown kind is left out`() {
        val dc = County("11001", "District of Columbia", "11", "District of Columbia", "DC")
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=things%20to%20do%20in%20District%20of%20Columbia%2C%20DC",
            CountyLinks.thingsToDo(dc, null),
        )
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests 'net.johnbiz.countyline.CountyLinksTest'`
Expected: compilation failure, `Unresolved reference 'CountyLinks'`.

- [ ] **Step 3: Implement `CountyLinks`**

```kotlin
package net.johnbiz.countyline.county

import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.fullName
import java.net.URLEncoder

/**
 * Outbound links for a county. All are plain web URLs opened with `ACTION_VIEW`, so the app
 * itself never touches the network. Wikipedia and Wikivoyage use `Special:Search`, which jumps
 * straight to the article on an exact title match and avoids guessing article titles.
 */
object CountyLinks {

    fun thingsToDo(county: County, kind: String?): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            encode("things to do in ${county.fullName(kind)}, ${county.stateAbbr}")

    fun wikipedia(county: County, kind: String?): String =
        "https://en.wikipedia.org/wiki/Special:Search?search=" + encode(searchTerm(county, kind))

    fun wikivoyage(county: County, kind: String?): String =
        "https://en.wikivoyage.org/wiki/Special:Search?search=" + encode(searchTerm(county, kind))

    private fun searchTerm(county: County, kind: String?) = "${county.fullName(kind)}, ${county.stateName}"

    // The String-charset overload works on every API level (the Charset overload needs API 33).
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
```

- [ ] **Step 4: Extend `CountyRepository`**

Replace the class body (keep the KDoc and companion as they are) with:

```kotlin
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
```

In the companion, add `private const val FACTS_ASSET = "county-facts.csv"` next to `ASSET_NAME`. Update imports: add `net.johnbiz.countyline.core.CountyCatalog` and `net.johnbiz.countyline.core.CountyFactsTable`; remove the now-unused private `load()` function.

- [ ] **Step 5: Run app unit tests**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest`
Expected: all PASS (new `CountyLinksTest` plus the existing `PermissionAndNotificationTest`).

- [ ] **Step 6: Checkpoint**

`git status --short`. Do not commit.

---

### Task 6: Rich crossing notification

**Files:**
- Create: `app/src/main/java/net/johnbiz/countyline/notify/OutlineBitmaps.kt`
- Create: `app/src/main/java/net/johnbiz/countyline/notify/CrossingDetails.kt`
- Create: `app/src/main/java/net/johnbiz/countyline/CountyDetailActivity.kt` (stub here; filled in by Task 7)
- Modify: `app/src/main/java/net/johnbiz/countyline/notify/Notifications.kt` (`postCrossing`, lines 60-82; new private helpers)
- Modify: `app/src/main/java/net/johnbiz/countyline/location/CountyTrackingService.kt` (`onCreate`, `handleLocation`)
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/net/johnbiz/countyline/CrossingNotificationTest.kt`

**Interfaces:**
- Consumes: `CountyRepository.facts()/catalog()`, `CountyLinks.thingsToDo`, `OutlineProjection.fit`, `CountyFactsFormat.summary`.
- Produces:
  - `object OutlineBitmaps { const val ICON_PX = 256; const val PICTURE_WIDTH_PX = 1024; const val PICTURE_HEIGHT_PX = 512; fun render(rings: List<DoubleArray>, width: Int, height: Int): Bitmap }`
  - `class CrossingDetails(val facts: CountyFacts?, val outlineIcon: Bitmap?, val outlinePicture: Bitmap?)`
  - `class CrossingDetailsLoader(facts: suspend () -> CountyFactsTable, catalog: suspend () -> CountyCatalog) { suspend fun load(fips: String): CrossingDetails? }`
  - `Notifications.postCrossing(context: Context, from: County?, to: County, details: CrossingDetails? = null)`
  - `CountyDetailActivity.EXTRA_FIPS: String`, `CountyDetailActivity.intent(context: Context, fips: String): Intent`

- [ ] **Step 1: Stub the detail Activity and register it** (so intents can target it)

`app/src/main/java/net/johnbiz/countyline/CountyDetailActivity.kt`:

```kotlin
package net.johnbiz.countyline

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity

/** Shows the outline, Census facts, and links for one county. Launched with [EXTRA_FIPS]. */
class CountyDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FIPS = "net.johnbiz.countyline.extra.FIPS"

        fun intent(context: Context, fips: String): Intent =
            Intent(context, CountyDetailActivity::class.java).putExtra(EXTRA_FIPS, fips)
    }
}
```

In `AndroidManifest.xml`, after the `MainActivity` element:

```xml
        <activity
            android:name=".CountyDetailActivity"
            android:exported="false"
            android:parentActivityName=".MainActivity"
            android:theme="@style/Theme.CountyLine" />
```

In `strings.xml`, before `</resources>`:

```xml
    <string name="action_things_to_do">Things to do</string>
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/net/johnbiz/countyline/CrossingNotificationTest.kt`:

```kotlin
package net.johnbiz.countyline

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CountyCatalog
import net.johnbiz.countyline.core.CountyFacts
import net.johnbiz.countyline.core.CountyFactsTable
import net.johnbiz.countyline.notify.CrossingDetails
import net.johnbiz.countyline.notify.CrossingDetailsLoader
import net.johnbiz.countyline.notify.Notifications
import net.johnbiz.countyline.notify.OutlineBitmaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CrossingNotificationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val johnson = County("20091", "Johnson", "20", "Kansas", "KS")
    private val la = County("06037", "Los Angeles", "06", "California", "CA")
    private val square = listOf(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 0.0))

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        Notifications.createChannels(context)
    }

    private fun posted(): Notification =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()

    private fun details(facts: CountyFacts?) = CrossingDetails(
        facts = facts,
        outlineIcon = OutlineBitmaps.render(square, OutlineBitmaps.ICON_PX, OutlineBitmaps.ICON_PX),
        outlinePicture = OutlineBitmaps.render(square, OutlineBitmaps.PICTURE_WIDTH_PX, OutlineBitmaps.PICTURE_HEIGHT_PX),
    )

    private fun contentIntentOf(n: Notification): Intent = shadowOf(n.contentIntent).savedIntents.last()

    @Test
    fun `rich notification has outline, facts, picture style, and things-to-do action`() {
        Notifications.postCrossing(context, from = null, to = johnson, details(CountyFacts("County", 609863, 473.375)))
        val n = posted()

        assertNotNull(n.getLargeIcon())
        assertEquals("android.app.Notification\$BigPictureStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().endsWith("pop. 609,863 · 1,288/sq mi"))

        val action = n.actions.single()
        assertEquals("Things to do", action.title.toString())
        val view = shadowOf(action.actionIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertTrue(view.dataString!!.contains("Johnson%20County%2C%20KS"))
    }

    @Test
    fun `tapping opens the detail screen for that county`() {
        Notifications.postCrossing(context, from = null, to = johnson, details = null)
        val intent = contentIntentOf(posted())
        assertEquals(CountyDetailActivity::class.java.name, intent.component!!.className)
        assertEquals("20091", intent.getStringExtra(CountyDetailActivity.EXTRA_FIPS))
    }

    @Test
    fun `each county keeps its own content intent`() {
        Notifications.postCrossing(context, from = null, to = johnson, details = null)
        val first = posted().contentIntent
        Notifications.postCrossing(context, from = johnson, to = la, details = null)
        val second = posted().contentIntent
        assertEquals("20091", shadowOf(first).savedIntents.last().getStringExtra(CountyDetailActivity.EXTRA_FIPS))
        assertEquals("06037", shadowOf(second).savedIntents.last().getStringExtra(CountyDetailActivity.EXTRA_FIPS))
    }

    @Test
    fun `missing population leaves no gaps in the text`() {
        Notifications.postCrossing(context, from = null, to = johnson, details(CountyFacts("city", null, 6.88)))
        val text = posted().extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertFalse(text, text.contains("null"))
        assertFalse(text, text.contains("pop."))
        assertFalse(text, text.trimEnd().endsWith("·"))
    }

    @Test
    fun `without details the notification is text-only but still useful`() {
        Notifications.postCrossing(context, from = null, to = johnson, details = null)
        val n = posted()
        assertNull(n.getLargeIcon())
        assertEquals("android.app.Notification\$BigTextStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(1, n.actions.size)
    }

    @Test
    fun `outline bitmaps have the requested sizes`() {
        val icon = OutlineBitmaps.render(square, OutlineBitmaps.ICON_PX, OutlineBitmaps.ICON_PX)
        val picture = OutlineBitmaps.render(square, OutlineBitmaps.PICTURE_WIDTH_PX, OutlineBitmaps.PICTURE_HEIGHT_PX)
        assertEquals(256 to 256, icon.width to icon.height)
        assertEquals(1024 to 512, picture.width to picture.height)
    }

    private class FakeCatalog(private val rings: List<DoubleArray>?) : CountyCatalog {
        override fun county(fips: String): County? = null
        override fun outline(fips: String): List<DoubleArray>? = rings
    }

    private fun factsTable(csv: String) = CountyFactsTable.parse(csv.byteInputStream())

    @Test
    fun `loader builds details from facts and outline`() = runTest {
        val loader = CrossingDetailsLoader(
            facts = { factsTable("20091,County,609863,473.375\n") },
            catalog = { FakeCatalog(square) },
        )
        val details = loader.load("20091")!!
        assertEquals(609863, details.facts!!.population)
        assertEquals(256, details.outlineIcon!!.width)
        assertEquals(1024, details.outlinePicture!!.width)
    }

    @Test
    fun `loader returns null instead of throwing when enrichment fails`() = runTest {
        val loader = CrossingDetailsLoader(
            facts = { throw java.io.FileNotFoundException("county-facts.csv") },
            catalog = { FakeCatalog(square) },
        )
        assertNull(loader.load("20091"))
    }

    @Test
    fun `loader returns null when nothing is known`() = runTest {
        val loader = CrossingDetailsLoader(facts = { factsTable("") }, catalog = { FakeCatalog(null) })
        assertNull(loader.load("20091"))
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests 'net.johnbiz.countyline.CrossingNotificationTest'`
Expected: compilation failure, `Unresolved reference 'CrossingDetails'` / `'OutlineBitmaps'` / `'CrossingDetailsLoader'`.

- [ ] **Step 4: Implement `OutlineBitmaps`**

```kotlin
package net.johnbiz.countyline.notify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import net.johnbiz.countyline.core.OutlineProjection
import kotlin.math.max
import kotlin.math.min

/** Renders a county outline into a transparent bitmap for notifications. */
object OutlineBitmaps {
    const val ICON_PX = 256
    const val PICTURE_WIDTH_PX = 1024
    const val PICTURE_HEIGHT_PX = 512

    // A mid green that reads on both light and dark notification shades.
    private const val STROKE_COLOR = 0xFF2E7D32.toInt()
    private const val FILL_COLOR = 0x592E7D32

    fun render(rings: List<DoubleArray>, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val shortSide = min(width, height).toFloat()
        val projected = OutlineProjection.fit(rings, width.toFloat(), height.toFloat(), padding = shortSide * 0.08f)

        // Even-odd fill so holes (a county surrounding an independent city) stay empty.
        val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (ring in projected) {
            if (ring.size < 4) continue
            path.moveTo(ring[0], ring[1])
            var i = 2
            while (i + 1 < ring.size) {
                path.lineTo(ring[i], ring[i + 1])
                i += 2
            }
            path.close()
        }

        val canvas = Canvas(bitmap)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = FILL_COLOR
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, shortSide / 96f)
            strokeJoin = Paint.Join.ROUND
            color = STROKE_COLOR
        })
        return bitmap
    }
}
```

- [ ] **Step 5: Implement `CrossingDetails` and its loader**

```kotlin
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
```

- [ ] **Step 6: Update `Notifications.postCrossing`**

Add imports: `android.app.PendingIntent`, `android.graphics.Bitmap`, `androidx.core.app.TaskStackBuilder`, `androidx.core.net.toUri`, `net.johnbiz.countyline.CountyDetailActivity`, `net.johnbiz.countyline.core.CountyFactsFormat`, `net.johnbiz.countyline.county.CountyLinks`, `java.util.Locale`.

Replace `postCrossing` with:

```kotlin
    /**
     * Posts the crossing alert. With [details], it shows the county outline and facts; without,
     * it falls back to plain text. Either way, tapping opens the county's detail screen and the
     * "Things to do" action opens Google Maps.
     */
    @SuppressLint("MissingPermission") // caller guarantees POST_NOTIFICATIONS on API 33+
    fun postCrossing(context: Context, from: County?, to: County, details: CrossingDetails? = null) {
        val base = if (from != null) {
            context.getString(R.string.crossing_text, from.displayName, to.name, to.stateName)
        } else {
            context.getString(R.string.crossing_text_no_prev, to.name, to.stateName)
        }
        val summary = details?.facts?.let { CountyFactsFormat.summary(it, Locale.getDefault()) }
        val text = if (summary != null) "$base · $summary" else base

        val builder = NotificationCompat.Builder(context, CHANNEL_CROSSINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.crossing_title, to.name))
            .setContentText(text)
            .setContentIntent(detailIntent(context, to.fips))
            .addAction(0, context.getString(R.string.action_things_to_do), thingsToDoIntent(context, to, details))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        details?.outlineIcon?.let { builder.setLargeIcon(it) }
        val picture = details?.outlinePicture
        if (picture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(text),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(CROSSING_NOTIFICATION_ID, builder.build())
        }
    }

    /** Back from the detail screen returns to the status screen. Request code keyed by FIPS so extras never collide. */
    private fun detailIntent(context: Context, fips: String): PendingIntent =
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(CountyDetailActivity.intent(context, fips))
            .getPendingIntent(fips.toInt(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!

    private fun thingsToDoIntent(context: Context, county: County, details: CrossingDetails?): PendingIntent =
        PendingIntent.getActivity(
            context,
            county.fips.toInt(),
            Intent(Intent.ACTION_VIEW, CountyLinks.thingsToDo(county, details?.facts?.kind).toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
```

Leave `serviceNotification` and the existing `contentIntent` (used by the service notification) unchanged.

- [ ] **Step 7: Wire the service**

In `CountyTrackingService.kt`: add `import net.johnbiz.countyline.notify.CrossingDetailsLoader`, a field `private lateinit var crossingDetails: CrossingDetailsLoader`, and at the end of `onCreate()`:

```kotlin
        crossingDetails = CrossingDetailsLoader(facts = counties::facts, catalog = counties::catalog)
```

In `handleLocation`, replace the `if (result.crossed) { ... }` block with:

```kotlin
        if (result.crossed) {
            val to = result.crossedInto!!
            // Already on Dispatchers.Default, so rendering the outline stays off the main thread.
            Notifications.postCrossing(this, from = result.crossedFrom, to = to, details = crossingDetails.load(to.fips))
        }
```

- [ ] **Step 8: Run tests to verify pass**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest`
Expected: all PASS, including the existing `crossing notification builds without a previous county`.

If `bigLargeIcon(null as Bitmap?)` is ambiguous or deprecated at compile time, keep the explicit `Bitmap?` cast (it selects the `Bitmap` overload); do not add a suppression for anything else.

- [ ] **Step 9: Checkpoint**

`git status --short`. Do not commit.

---

### Task 7: County detail screen and status-screen entry

**Files:**
- Create: `app/src/main/java/net/johnbiz/countyline/county/CountyDetailSource.kt`
- Create: `app/src/main/java/net/johnbiz/countyline/ui/CountyDetailViewModel.kt`
- Create: `app/src/main/java/net/johnbiz/countyline/ui/CountyDetailScreen.kt`
- Create: `app/src/main/res/drawable/ic_arrow_back.xml`, `app/src/main/res/drawable/ic_chevron_right.xml`
- Modify: `app/src/main/java/net/johnbiz/countyline/CountyDetailActivity.kt` (replace the Task 6 stub)
- Modify: `app/src/main/java/net/johnbiz/countyline/ui/StatusScreen.kt` (`StatusScreen` params, `CurrentCountyCard`, preview)
- Modify: `app/src/main/java/net/johnbiz/countyline/MainActivity.kt` (`setContent` block)
- Test: `app/src/test/java/net/johnbiz/countyline/CountyDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `CountyRepository.catalog()/facts()`, `CountyLinks`, `OutlineProjection`, `CountyFactsFormat`, `County.fullName`, `CountyDetailActivity.EXTRA_FIPS`/`intent` (Task 6).
- Produces:
  - `data class CountyDetail(val county: County, val facts: CountyFacts?, val outline: List<DoubleArray>?)`
  - `fun interface CountyDetailSource { suspend fun load(fips: String): CountyDetail? }`, `fun CountyRepository.detailSource(): CountyDetailSource`
  - `sealed interface CountyDetailUiState { data object Loading; data object NotFound; data class Loaded(val detail: CountyDetail) }`
  - `class CountyDetailViewModel(fips: String, source: CountyDetailSource) : ViewModel() { val uiState: StateFlow<CountyDetailUiState> }`
  - `@Composable fun CountyDetailScreen(state: CountyDetailUiState, onBack: () -> Unit, onOpenLink: (String) -> Unit)`
  - `StatusScreen(..., onOpenCountyDetails: (County) -> Unit)`

- [ ] **Step 1: Write the failing ViewModel test** (plain JUnit with a test Main dispatcher)

```kotlin
package net.johnbiz.countyline

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyDetailSource
import net.johnbiz.countyline.ui.CountyDetailUiState
import net.johnbiz.countyline.ui.CountyDetailViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountyDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val johnson = CountyDetail(County("20091", "Johnson", "20", "Kansas", "KS"), facts = null, outline = null)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows loading until the dataset is ready, then the county`() = runTest(dispatcher) {
        val gate = CompletableDeferred<CountyDetail?>()
        val vm = CountyDetailViewModel("20091", CountyDetailSource { gate.await() })
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loading, vm.uiState.value)

        gate.complete(johnson)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson), vm.uiState.value)
    }

    @Test
    fun `unknown FIPS is not found`() = runTest(dispatcher) {
        val vm = CountyDetailViewModel("", CountyDetailSource { null })
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `passes the FIPS through to the source`() = runTest(dispatcher) {
        var requested: String? = null
        CountyDetailViewModel("06037", CountyDetailSource { requested = it; null })
        advanceUntilIdle()
        assertEquals("06037", requested)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests 'net.johnbiz.countyline.CountyDetailViewModelTest'`
Expected: compilation failure, `Unresolved reference 'CountyDetail'` / `'CountyDetailViewModel'`.

- [ ] **Step 3: Implement the source and ViewModel**

`app/src/main/java/net/johnbiz/countyline/county/CountyDetailSource.kt`:

```kotlin
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
```

`app/src/main/java/net/johnbiz/countyline/ui/CountyDetailViewModel.kt`:

```kotlin
package net.johnbiz.countyline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyDetailSource

sealed interface CountyDetailUiState {
    data object Loading : CountyDetailUiState
    data object NotFound : CountyDetailUiState
    data class Loaded(val detail: CountyDetail) : CountyDetailUiState
}

class CountyDetailViewModel(fips: String, source: CountyDetailSource) : ViewModel() {

    private val state = MutableStateFlow<CountyDetailUiState>(CountyDetailUiState.Loading)
    val uiState: StateFlow<CountyDetailUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = source.load(fips)?.let(CountyDetailUiState::Loaded) ?: CountyDetailUiState.NotFound
        }
    }
}
```

Note: `CountyDetail` holds `List<DoubleArray>`, so `Loaded` equality compares the list by reference per array. The test passes the same instance, so `assertEquals` holds.

- [ ] **Step 4: Run to verify pass**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests 'net.johnbiz.countyline.CountyDetailViewModelTest'`
Expected: PASS.

- [ ] **Step 5: Add the vector drawables**

`app/src/main/res/drawable/ic_arrow_back.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z" />
</vector>
```

`app/src/main/res/drawable/ic_chevron_right.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z" />
</vector>
```

- [ ] **Step 6: Implement the screen**

`app/src/main/java/net/johnbiz/countyline/ui/CountyDetailScreen.kt`:

```kotlin
package net.johnbiz.countyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.johnbiz.countyline.R
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CountyFacts
import net.johnbiz.countyline.core.CountyFactsFormat
import net.johnbiz.countyline.core.OutlineProjection
import net.johnbiz.countyline.core.fullName
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyLinks
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountyDetailScreen(
    state: CountyDetailUiState,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val title = (state as? CountyDetailUiState.Loaded)?.detail?.let {
        "${it.county.fullName(it.facts?.kind)}, ${it.county.stateAbbr}"
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding)
        when (state) {
            CountyDetailUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            CountyDetailUiState.NotFound -> Column(
                modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("We couldn't find details for this county.", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
            is CountyDetailUiState.Loaded -> CountyDetailContent(state.detail, onOpenLink, modifier)
        }
    }
}

@Composable
private fun CountyDetailContent(detail: CountyDetail, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val county = detail.county
    val kind = detail.facts?.kind
    Column(
        modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        detail.outline?.let { CountyOutline(it, Modifier.fillMaxWidth().aspectRatio(4f / 3f)) }
        detail.facts?.let { FactsRow(it) }
        Button(onClick = { onOpenLink(CountyLinks.thingsToDo(county, kind)) }, Modifier.fillMaxWidth()) {
            Text("Things to do")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onOpenLink(CountyLinks.wikipedia(county, kind)) }, Modifier.weight(1f)) {
                Text("Wikipedia")
            }
            OutlinedButton(onClick = { onOpenLink(CountyLinks.wikivoyage(county, kind)) }, Modifier.weight(1f)) {
                Text("Wikivoyage")
            }
        }
        Text(
            "Population: 2020 U.S. Census. Boundaries and area: U.S. Census Bureau.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FactsRow(facts: CountyFacts) {
    val locale = Locale.getDefault()
    val items = listOfNotNull(
        facts.population?.let { "Population" to CountyFactsFormat.count(it, locale) },
        facts.landSqMi?.let { "Land area" to "${CountyFactsFormat.measure(it, locale)} sq mi" },
        facts.density?.let { "Density" to "${CountyFactsFormat.measure(it, locale)}/sq mi" },
    )
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for ((label, value) in items) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Draws a county outline in theme colors; even-odd fill keeps holes empty. */
@Composable
private fun CountyOutline(rings: List<DoubleArray>, modifier: Modifier = Modifier) {
    val stroke = MaterialTheme.colorScheme.primary
    val fill = stroke.copy(alpha = 0.25f)
    Canvas(modifier) {
        val projected = OutlineProjection.fit(rings, size.width, size.height, padding = 16.dp.toPx())
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        for (ring in projected) {
            if (ring.size < 4) continue
            path.moveTo(ring[0], ring[1])
            var i = 2
            while (i + 1 < ring.size) {
                path.lineTo(ring[i], ring[i + 1])
                i += 2
            }
            path.close()
        }
        drawPath(path, fill)
        drawPath(path, stroke, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Preview(showBackground = true)
@Composable
private fun CountyDetailScreenPreview() {
    CountyLineTheme {
        CountyDetailScreen(
            state = CountyDetailUiState.Loaded(
                CountyDetail(
                    county = County("20091", "Johnson", "20", "Kansas", "KS"),
                    facts = CountyFacts("County", 609863, 473.375),
                    outline = listOf(doubleArrayOf(-95.06, 38.74, -94.61, 38.74, -94.61, 39.04, -95.06, 39.04, -95.06, 38.74)),
                ),
            ),
            onBack = {},
            onOpenLink = {},
        )
    }
}
```

- [ ] **Step 7: Fill in the Activity**

Replace `CountyDetailActivity.kt` with:

```kotlin
package net.johnbiz.countyline

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.johnbiz.countyline.county.CountyRepository
import net.johnbiz.countyline.county.detailSource
import net.johnbiz.countyline.ui.CountyDetailScreen
import net.johnbiz.countyline.ui.CountyDetailViewModel
import net.johnbiz.countyline.ui.CountyLineTheme

/** Shows the outline, Census facts, and links for one county. Launched with [EXTRA_FIPS]. */
class CountyDetailActivity : ComponentActivity() {

    private val viewModel: CountyDetailViewModel by viewModels {
        viewModelFactory {
            initializer {
                CountyDetailViewModel(
                    fips = intent.getStringExtra(EXTRA_FIPS).orEmpty(),
                    source = CountyRepository.get(applicationContext).detailSource(),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountyLineTheme {
                val state by viewModel.uiState.collectAsState()
                CountyDetailScreen(state = state, onBack = ::finish, onOpenLink = ::openLink)
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_FIPS = "net.johnbiz.countyline.extra.FIPS"

        fun intent(context: Context, fips: String): Intent =
            Intent(context, CountyDetailActivity::class.java).putExtra(EXTRA_FIPS, fips)
    }
}
```

- [ ] **Step 8: Make the status-screen county clickable**

In `StatusScreen.kt`:

1. Add the parameter `onOpenCountyDetails: (County) -> Unit,` to `StatusScreen` after `onToggleTracking`, and change the call to `CurrentCountyCard(state.currentCounty, onOpenCountyDetails)`.
2. Add imports `androidx.compose.material3.Icon`, `androidx.compose.ui.res.painterResource`, `net.johnbiz.countyline.R`.
3. Replace `CurrentCountyCard` with:

```kotlin
@Composable
private fun CurrentCountyCard(county: County?, onOpenDetails: (County) -> Unit) {
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Current county", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = county?.let { "${it.name}\n${it.stateName}" } ?: "Unknown — waiting for a fix",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (county != null) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "View county details")
            }
        }
    }
    if (county != null) {
        Card(onClick = { onOpenDetails(county) }, modifier = Modifier.fillMaxWidth()) { body() }
    } else {
        Card(Modifier.fillMaxWidth()) { body() }
    }
}
```

(The existing "Unknown — waiting for a fix" string is user-visible copy that predates this work; leave it as is.)

4. In `StatusScreenPreview`, add `onOpenCountyDetails = {},`.

In `MainActivity.kt`, add to the `StatusScreen(...)` call:

```kotlin
                    onOpenCountyDetails = { startActivity(CountyDetailActivity.intent(this, it.fips)) },
```

- [ ] **Step 9: Build, test, lint**

Run: `mise exec -- ./gradlew test lint assembleDebug`
Expected: BUILD SUCCESSFUL; no new lint errors. If lint reports new warnings in files this plan touched, fix them; report any you cannot fix.

- [ ] **Step 10: Checkpoint**

`git status --short`. Do not commit.

---

### Task 8: Documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: names from Tasks 1-7.
- Produces: none.

- [ ] **Step 1: Update `CLAUDE.md`**

1. **Module Layout**, `:core` bullet: after `CrossingDetector`, add `CountyFacts`/`CountyFactsTable`/`CountyFactsFormat` (bundled Census facts), `CountyCatalog` (FIPS lookup + outlines, implemented by `GeoJsonCountyResolver`), `OutlineProjection`.
2. **Module Layout**, `:app` bullet: add "county detail screen (`CountyDetailActivity`)".
3. **Dataset**: append this bullet list:

```markdown
- **Facts**: `app/src/main/assets/county-facts.csv` (`fips,kind,population,land_sq_mi`, empty =
  unknown) is generated by `scripts/gen_county_facts.py` and committed. Population is 2020 Census
  P.L. 94-171 `P1_001N`; land area is the GeoJSON's own `CENSUSAREA`. The script needs
  `CENSUS_API_KEY` (free; developer machine only, never shipped) unless `data/census-2020-pl-county.json`
  is cached. It maps 2010-vintage FIPS via a crosswalk (`46113`, `02270`, `02261`) and fails on any
  unmatched FIPS; only `51515` (Bedford city) has no population. Regenerate it whenever
  `counties.geojson` changes.
```

4. **Architecture Notes**: append:

```markdown
- Crossing notifications are enriched best-effort: `CrossingDetailsLoader` (facts + outline
  bitmaps) returns `null` on any failure and `postCrossing` falls back to plain text. The content
  intent opens `CountyDetailActivity` (FIPS extra, `TaskStackBuilder` back stack) and the
  "Things to do" action opens Google Maps. All links are `ACTION_VIEW` URLs; the app has no
  `INTERNET` permission, so keep it that way.
```

5. **Known Issues**: change the antimeridian bullet to: "Antimeridian-crossing polygons (far-western Aleutians) are not stitched for resolving; outline drawing unwraps them (`OutlineProjection`)."
6. **Testing Instructions**: add bullets for `CountyFactsTest`/`CountyFactsAssetTest`/`OutlineProjectionTest` (`:core`) and `CountyLinksTest`/`CrossingNotificationTest`/`CountyDetailViewModelTest` (`:app`). Add to the manual plan: "check the expanded crossing notification and the detail screen in light and dark mode."

- [ ] **Step 2: Update `README.md`**

Under `## How it works`, add a bullet in the existing style:

```markdown
- Each crossing alert shows the new county's outline, 2020 Census population and density, and a
  **Things to do** button that opens Google Maps. Tap the alert (or the current county in the app)
  for a details page with Wikipedia and Wikivoyage links. Everything is bundled; nothing is fetched.
```

Under `## Tests`, list the new test classes the same way existing ones are listed.

- [ ] **Step 3: Final verification**

Run: `mise exec -- ./gradlew test lint assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual checks (human, on a device)**

Report these to the user as the remaining manual plan; do not claim them done:
- Real crossing: collapsed and expanded notification; "Things to do" opens Maps.
- Force-stop the app, tap an old crossing notification: Loading, then the county; Back lands on the status screen.
- Dark mode on both surfaces.
- A county with a hole or several parts (for example, Albemarle County, VA, around Charlottesville) renders correctly.

- [ ] **Step 5: Checkpoint**

`git status --short`. Do not commit. The user stages, commits, and opens the PR (suggested title: `feat(app): county details with outline, Census facts, and things-to-do links`).
