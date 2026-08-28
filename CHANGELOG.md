# Changelog

All notable changes to County Line are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions map to
`versionName` in `app/build.gradle.kts`.

The Google Play "What's new" text for each release lives in
`docs/store/whatsnew/`.

## [Unreleased]

_Nothing yet._

## [0.1.0] — 2026-08-28

First release. `versionCode 1`.

### Added
- Background county-crossing notifications: a `location`-typed foreground service
  keeps a low-power `FusedLocationProviderClient` subscription and posts a
  notification naming the new county and state on a confirmed crossing.
- Offline `CountyResolver` — all ~3,221 US county polygons bundled as GeoJSON,
  indexed into a coarse 1°×1° grid, resolved with bounding-box pre-filter +
  ray-casting point-in-polygon. No network, no geocoding API.
- `CrossingDetector` hysteresis — a new county must be seen on 3 consecutive
  fixes before a crossing fires, so GPS jitter near a border doesn't spam.
- Last-known county and tracking state persisted via DataStore; a
  `BOOT_COMPLETED` receiver restarts tracking after a reboot.
- Compose status screen: current county, staged permission flow
  (foreground → background → notifications), and a "while using the app"
  dead-end that links to system settings.
- Google Play "prominent disclosure" dialog shown before the background-location
  prompt.
- Release build: R8-minified, resource-shrunk, signed App Bundle
  (`./gradlew bundleRelease`).

[Unreleased]: https://github.com/johnbiz-ai/county-line/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/johnbiz-ai/county-line/releases/tag/v0.1.0
