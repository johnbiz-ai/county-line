# CLAUDE.md

This file provides guidance to Claude Code when working on this repository.

## Repository

- GitHub: https://github.com/johnbiz-ai/county-line (private)
- Merge policy: squash or rebase merges only — no merge commits; keep history linear
- Head branches auto-delete on merge
- Always do work on a feature branch and open a pull request — never commit or push directly to `main`. The user reviews, approves, and merges every PR.
- Use [Conventional Commits](https://www.conventionalcommits.org/) for commit subjects and PR
  titles: `type(scope): summary` (`feat`, `fix`, `build`, `chore`, `docs`, `refactor`, `test`,
  `ci`, `perf`). Scope is optional (e.g. `core`, `app`, `docker`). Breaking changes get a `!`
  before the colon or a `BREAKING CHANGE:` footer. Since PRs are squash-merged, the PR title is
  the commit that lands on `main` — it must follow this format.

## Project Overview

**County Crossing Notifier** — an Android app that tracks the user's location in the background and sends a push
notification whenever the user crosses from one US county into another.

Core loop:

1. Request location permissions (foreground + background).
2. Track location updates efficiently (avoid draining battery).
3. Resolve the current lat/lng to a county (reverse geocoding — county boundaries don't map cleanly to simple radius
   geofences).
4. Compare the resolved county to the last known county.
5. If it changed, fire a local push notification naming the new county (and optionally the state).
6. Persist last-known county across app restarts/reboots.

## Module Layout

- `:core` — pure Kotlin/JVM library, **no Android dependencies**. `CountyResolver` +
  `GeoJsonCountyResolver`, geometry (`Geometry.kt`), `CrossingDetector`, `County`, `UsStates`.
  Unit-tested on a plain JVM (`./gradlew :core:test`) — fast, no emulator.
- `:app` — the Android app. Foreground service, notifications, DataStore persistence, boot
  receiver, permission flow, Compose status screen. Depends on `:core`.
- `gradle/libs.versions.toml` — version catalog; all dependency/plugin versions live here.

## Tech Stack

- Kotlin 2.0, Jetpack Compose (Material 3) for the single status/settings screen
- `FusedLocationProviderClient` (Play Services location) via `kotlinx-coroutines-play-services`
- **Foreground service** (`CountyTrackingService`, `foregroundServiceType="location"`) for
  background tracking — chosen over WorkManager, whose 15-min floor and throttled background
  location delivery don't fit "notify promptly on crossing"
- Offline reverse geocoding only: bundled GeoJSON of all county polygons + coarse-grid spatial
  index. No `Geocoder` / network fallback.
- `kotlinx.serialization` for parsing the GeoJSON (works on JVM + Android; keeps `:core` pure)
- **DataStore (Preferences)** persists `trackingEnabled` and the full `CrossingState`
- `NotificationCompat` for both the ongoing service notification and crossing alerts
  (`Notifications.kt`)

## Dataset

- Source: `plotly/datasets` `geojson-counties-fips.json` (all ~3,221 county-equivalents,
  keyed by 5-digit FIPS, `Polygon` + `MultiPolygon`, some features have `null` geometry).
- Shipped **gzip-compressed** at `app/src/main/assets/counties.geojson.gz` (~884 KB);
  `androidResources { noCompress += "gz" }` stops AAPT double-compressing it.
- Raw files under `data/` are git-ignored. `:core` tests read the compressed asset via a
  relative path (`../app/src/main/assets/...`) for the real-data integration checks.
- `GeoJsonCountyResolver` loads it once (`CountyRepository`, `Dispatchers.IO`), builds a
  `Map<cellKey, List<CountyPolygon>>` grid (1° cells), then per query does bbox pre-filter +
  even-odd ray cast. `MultiPolygon` → one `CountyPolygon` per sub-polygon; holes handled by
  the even-odd rule across all rings.

## Permissions

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (separate runtime request on Android 10+; must be requested *after* foreground location
  is granted, per Play policy)
- `POST_NOTIFICATIONS` (Android 13+)
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` if using a foreground service
- Handle "Allow only while using the app" gracefully — background tracking will not work in that mode; the app should
  detect this and inform the user

## Architecture Notes

- `CountyResolver` is an interface in `:core`; `GeoJsonCountyResolver` is the only impl.
  `resolve(lat, lng): County?` — `null` means open water / outside the US / unmapped.
- `CrossingDetector` is a **pure reducer**: `update(CrossingState, County?) -> CrossingResult`.
  It holds no state; the caller (`CountyTrackingService`) owns persistence. Hysteresis: a new
  county must appear on `confirmations` (default 3) consecutive resolves before a crossing
  fires. Returning to the current county clears the pending candidate; `null` resolves are
  ignored without disturbing state.
- `CountyTrackingService`: `START_STICKY` foreground service, `BALANCED_POWER_ACCURACY`,
  `INTERVAL_MS` 2 min / `MIN_DISPLACEMENT_M` 250 m. Location handling is serialized with a
  `Mutex`; each fix → resolve → `CrossingDetector.update` → persist → maybe notify → refresh
  ongoing notification. Toggle via `CountyTrackingService.start/stop(context)`.
- `LocationPermissions.readiness()` collapses permission state into a `TrackingReadiness`
  enum (incl. `FOREGROUND_ONLY` for "while using the app"); `MainActivity.advancePermissionFlow()`
  requests exactly the next missing permission in Play-policy order.
- `BootReceiver` restarts the service on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` only if
  `trackingEnabled` **and** permissions still hold (`goAsync()` + short coroutine).
- Polling frequency is currently constant (see `CountyTrackingService` companion consts) —
  adaptive/stationary tuning is a TODO.

## Tooling

- Use [mise](https://mise.jdx.dev/) for tool management — JDK, Android SDK/command-line tools, Gradle, and any CLI
  dependencies are pinned in `mise.toml`, not installed globally or by hand
- Run project commands through `mise exec -- <cmd>` (or `mise run <task>`) so the pinned versions are used
- After changing `mise.toml`, run `mise install`

## Commands

Needs a JDK 17 (`mise install`) **and** an Android SDK with `platforms;android-35` +
`build-tools;35.0.0`. Point at it via `local.properties` (`sdk.dir=...`) or `$ANDROID_HOME`.

- Build debug APK: `./gradlew assembleDebug` (→ `app/build/outputs/apk/debug/app-debug.apk`)
- Install on device: `./gradlew installDebug` (or `mise run install`)
- All JVM unit tests: `./gradlew test`
- Resolver + detector tests only (no SDK emulator, fast): `./gradlew :core:test`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`
- `mise run build | test | lint | install` wrap the above.

## Code Style

- Kotlin official style guide (`kotlin.code.style=official`); ktlint/detekt not yet wired
- Prefer coroutines/Flow over callbacks for location and DB updates
- `:core` must stay Android-free so its tests run on a bare JVM
- `StatusViewModel` exposes a single `StateFlow<StatusUiState>`; Composables in
  `StatusScreen.kt` are pure (state in, lambdas out)

## Testing Instructions

- `:core` tests (`./gradlew :core:test`) cover:
  - `GeoJsonCountyResolverTest` — synthetic fixtures for holes / multipolygon / grid-spanning,
    plus real-dataset checks against border fixture coords (KC MO↔KS, DC↔Arlington,
    San Bernardino↔Riverside) and offshore → `null`. Regenerate expected FIPS with a scratch
    Python ray-cast script against `data/geojson-counties-fips.json` if fixtures change.
  - `CrossingDetectorTest` — hysteresis, border flapping, `null` handling; no real location data.
- `:app` tests are Robolectric (`PermissionAndNotificationTest`) — permission readiness +
  notification channels.
- Manual plan: cross a border with the app killed; after a reboot; and with "while using the
  app" permission only (status screen must warn + deep-link to settings).

## Known Issues / Open Questions

- Boundary precision is the source dataset's; near-border points may resolve to the neighbour
  until hysteresis clears. County lines that are also state lines work (tested).
- Antimeridian-crossing polygons (far-western Aleutians) not stitched.
- Update frequency vs. battery is a fixed default — no adaptive/stationary backoff yet.
- No app launcher PNG icons — adaptive vector icon only (fine for minSdk 26).
- `connectedAndroidTest` has only the scaffolded default; no real instrumented coverage yet.