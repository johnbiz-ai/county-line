# County Line

An Android app that tracks your location in the background and sends a push notification
whenever you cross from one US county into another.

## How it works

1. You grant location permission, then **background** location ("Allow all the time"), then
   notification permission (Android 13+).
2. A `location`-typed **foreground service** keeps a low-power location subscription alive
   (`FusedLocationProviderClient`, balanced-power priority, ~2 min interval, 250 m minimum
   displacement).
3. Each fix is resolved to a county entirely **offline** by `CountyResolver`: a bundled
   GeoJSON of all ~3,221 county polygons, indexed into a coarse 1°×1° grid, then
   bounding-box pre-filter + ray-casting point-in-polygon on the few surviving candidates.
4. A `CrossingDetector` applies hysteresis — a new county must be seen on 3 consecutive
   fixes before it counts — so GPS jitter along a border doesn't spam you.
5. On a confirmed crossing you get a notification ("Welcome to Marin County — you crossed
   from San Francisco into Marin, CA"). The last-known county is persisted (DataStore) and
   survives process death and reboot.
6. A `BOOT_COMPLETED` receiver restarts tracking after a reboot if you had it enabled.

If you only grant "While using the app", the status screen tells you background tracking
won't work and links you to system settings.

## Module layout

| Module  | Contents |
|---------|----------|
| `:core` | Pure Kotlin/JVM. `CountyResolver` + `GeoJsonCountyResolver`, geometry, `CrossingDetector`, `County`, `UsStates`. No Android dependencies — unit-tested on a plain JVM. |
| `:app`  | Android app. Foreground service, notifications, DataStore persistence, boot receiver, permission flow, Compose status screen. |

The county dataset (`plotly/datasets` `geojson-counties-fips.json`) ships as
`app/src/main/assets/counties.geojson` (~3 MB uncompressed; the APK's own zip entry
compresses it to ~1 MB). The raw source files under `data/` are git-ignored.

## Building & installing

### Prerequisites

- JDK 17 — `mise install` provisions Temurin 17 (see `mise.toml`).
- **Android SDK** (not managed by mise). Install via Android Studio, or the command-line
  tools:
  ```sh
  # one-time SDK setup
  export ANDROID_HOME="$HOME/Android/Sdk"
  sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
  ```
  Then either export `ANDROID_HOME`, or create `local.properties` with:
  ```
  sdk.dir=/absolute/path/to/Android/Sdk
  ```

### Docker (no local SDK needed)

A build image with JDK 17 + Android SDK 35 is defined in `docker/Dockerfile`. It
carries only the toolchain — the project is bind-mounted, so source edits never
need an image rebuild.

```sh
./docker/build.sh                      # -> ./gradlew assembleDebug (as your host user)
./docker/build.sh :core:test
./docker/build.sh assembleDebug test lint
```

`build.sh` runs the container as your UID/GID, so `app/build/…/app-debug.apk` and
other outputs land on the host owned by you. Downloaded Gradle dependencies persist
in the `county-line_gradle-cache` volume. Plain `docker compose` works too:

```sh
docker compose build
DOCKER_UID=$(id -u) DOCKER_GID=$(id -g) docker compose run --rm android ./gradlew test
```

`connectedAndroidTest` / `installDebug` still need a real device or emulator and
are not run from the container.

### Install on a device

1. Enable **Developer options → USB debugging** on the phone and plug it in
   (`adb devices` should list it).
2. Build and install the debug APK:
   ```sh
   ./gradlew installDebug
   # or: mise run install
   ```
   To just produce the APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`,
   then `adb install -r app-debug.apk`.
3. Launch **County Line**. Grant, in order: location → "Allow all the time" → notifications.
4. Toggle **Background tracking** on. A persistent "Tracking your county" notification
   appears; you can now close the app.

### Emulator

county crossings need location changes: in the emulator's **Extended controls → Location**,
set a point in one county, then a point across a county line a minute later (or play a GPX
route). Cold-start behaviour can also be checked with `adb shell am kill net.johnbiz.countyline`.

## Commands

| Task | Command |
|------|---------|
| Debug APK | `./gradlew assembleDebug` |
| Install on device | `./gradlew installDebug` |
| All JVM unit tests | `./gradlew test` |
| `CountyResolver`/detector tests only | `./gradlew :core:test` |
| Instrumented tests | `./gradlew connectedAndroidTest` |
| Lint | `./gradlew lint` |
| Release App Bundle | `./gradlew bundleRelease` |
| Any task in Docker | `./docker/build.sh <tasks…>` |

(`mise run build` / `test` / `lint` / `install` wrap these.)

## Releasing

`bundleRelease` produces a signed `.aab` for Google Play. Signing comes from a git-ignored
`keystore.properties` (see `keystore.properties.example`) or `COUNTYLINE_KEYSTORE*` env
vars; with neither, it falls back to the debug key. The full Play Store process — keystore,
Play Console declarations for background location, store listing, screenshots — is in
[RELEASING.md](RELEASING.md). Store assets live in `docs/store/`; the privacy policy
(`docs/privacy-policy.html`) is served via GitHub Pages from `/docs`.

## Tests

- `GeoJsonCountyResolverTest` — synthetic polygons for deterministic geometry/index
  behaviour (holes, multipolygons, grid spanning), plus the real bundled dataset checked
  against fixture coordinates near known borders (Kansas City MO/KS, DC/Arlington,
  San Bernardino/Riverside) and offshore points that must resolve to `null`.
- `GeometryTest` — ray casting and bounding-box edge cases.
- `CrossingDetectorTest` — hysteresis, border flapping, `null` handling, back-to-back
  crossings, all independent of real location data.
- `PermissionAndNotificationTest` (Robolectric) — permission-readiness state machine and
  notification-channel setup.

## Known limitations

- Boundary precision is the source dataset's; a point within a few hundred metres of a
  county line may resolve to the neighbour until the hysteresis threshold is met.
- Polygons crossing the ±180° antimeridian (far-western Aleutians) aren't stitched.
- Update frequency vs. battery is a fixed default, not yet user-tunable.
