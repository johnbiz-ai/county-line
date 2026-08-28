# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions map to
`versionName` in `app/build.gradle.kts`. Play "What's new" text: `docs/store/whatsnew/`.

## [Unreleased]

_Nothing yet._

## [0.1.0] — 2026-08-28

First release. `versionCode 1`.

### Added
- Background county-crossing notifications via a `location` foreground service.
- Offline `CountyResolver`: all ~3,221 US county polygons, grid-indexed, ray-cast — no network.
- Crossing hysteresis (3 consecutive fixes) to absorb GPS jitter near borders.
- Last-known county + tracking state persisted; restarts after reboot.
- Compose status screen with staged location/notification permission flow.
- Google Play prominent-disclosure dialog before the background-location prompt.
- R8-minified, signed release App Bundle (`./gradlew bundleRelease`).

[Unreleased]: https://github.com/johnbiz-ai/county-line/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/johnbiz-ai/county-line/releases/tag/v0.1.0
