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
