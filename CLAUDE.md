# CLAUDE.md

This file provides guidance to Claude Code when working on this repository.

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

## Tech Stack

- Kotlin, Jetpack Compose for any UI (settings/status screen)
- `FusedLocationProviderClient` (Google Play Services location) for location updates
- WorkManager or a foreground `Service` for background tracking (decide based on required update frequency vs. Android
  background execution limits)
- Reverse geocoding: prefer an offline dataset (e.g. bundled county boundary polygons via a spatial index) to avoid
  per-update network/geocoding API costs and rate limits; fall back to `Geocoder`/a geocoding API only if no offline
  match
- Room (or DataStore) for persisting last-known county
- `NotificationCompat` / `NotificationManager` for the push notification

## Permissions

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (separate runtime request on Android 10+; must be requested *after* foreground location
  is granted, per Play policy)
- `POST_NOTIFICATIONS` (Android 13+)
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` if using a foreground service
- Handle "Allow only while using the app" gracefully — background tracking will not work in that mode; the app should
  detect this and inform the user

## Architecture Notes

- Keep location-polling frequency configurable; balance responsiveness against battery drain (e.g. adaptive interval,
  larger radius/interval when stationary)
- County boundary lookup should be a pluggable interface (`CountyResolver`) so the offline-dataset implementation can be
  swapped or tested independently of live location data
- Debounce/hysteresis on county changes — GPS jitter near a border should not cause repeated notifications for the same
  crossing
- All background work must survive process death and device reboot (`BOOT_COMPLETED` receiver to restart tracking if the
  user has enabled it)

## Commands

_(fill in once the project is scaffolded, e.g.)_

- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

## Code Style

- Kotlin official style guide, enforced via ktlint/detekt if configured
- Prefer coroutines/Flow over callbacks for location and DB updates
- ViewModels expose state via `StateFlow`; no business logic in Composables

## Testing Instructions

- Unit-test the `CountyResolver` boundary-matching logic with fixture coordinates near known county borders
- Unit-test the crossing-detection/debounce logic independent of real location data
- Manual test plan should include: crossing a border while app is killed, while device rebooted, and with "while using
  app" location permission only

## Known Issues / Open Questions

- Choice of offline county boundary dataset (e.g. US Census TIGER/Line shapefiles) and how to bundle/compress it in the
  APK
- Update frequency vs. battery tradeoff not yet tuned
- Behavior near county borders that also cross state lines