# Releasing County Line to Google Play

This app requests **background location** and runs a **`location` foreground service**, so
it goes through Google's strictest review. Budget ~2–3 weeks from a fresh Play Console
account to production (new personal accounts must run a 14-day, 12-tester closed test first).

---

## 1. One-time setup

### 1.1 Upload keystore

Generate it once and **back it up somewhere safe** (a password manager + offline copy). If
you lose it you can reset the *upload* key via Play support, but only because Play App
Signing holds the real app-signing key — enrol in that (below).

```sh
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the repo root (git-ignored — see
`keystore.properties.example`):

```properties
storeFile=upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

`app/build.gradle.kts` reads this (or the `COUNTYLINE_KEYSTORE*` env vars for CI). With no
keystore configured, release builds fall back to the debug key so CI still works.

> A throwaway `upload-keystore.jks` (password `testtest`) may exist locally from testing —
> **delete it and generate a real one** before your first upload.

### 1.2 Play Console

1. Create a developer account ($25, identity verification — allow a few days).
2. Create the app: **County Line**, app (not game), free.
3. **Set up → App integrity → Play App Signing**: opt in (default for new apps). Play holds
   the signing key; you upload with the *upload* key.
4. Note the app's package name is fixed at `net.johnbiz.countyline` once uploaded.

### 1.3 Privacy policy

`docs/privacy-policy.md` is the source. Host it at a public URL and paste that URL into
Play Console → **Store presence → Store listing → Privacy policy**.

- Fill in the contact email placeholder first.
- Easiest hosting: enable **GitHub Pages** on this repo (Settings → Pages → source
  `main` / `/docs`), giving `https://johnbiz-ai.github.io/county-line/privacy-policy`.

---

## 2. Store listing

### Text

| Field | Value |
|---|---|
| **App name** | `County Line` |
| **Short description** (80 chars) | `Get a notification the moment you cross from one US county into the next.` |
| **Full description** | see below |
| **Category** | Maps & Navigation |
| **Tags** | location, travel, maps |
| **Contact email** | _your email_ |
| **Website** | optional (repo or Pages URL) |

**Full description:**

```
County Line quietly watches your location and sends a notification the moment you cross
from one US county into another — whether you're on a road trip, a train, or just driving
across town.

• Works in the background. Enable tracking once and County Line keeps watch even when the
  app is closed. A confirmed crossing pops a notification naming the new county and state.

• Fully offline. Every county boundary in the United States is bundled inside the app. Your
  location is looked up on your device and is never sent anywhere, saved off your device,
  or shared. No account, no ads, no analytics.

• Easy on your battery. County Line uses low-power location updates and only checks the
  map when you've actually moved.

• Steady, not noisy. GPS wobble near a border won't spam you — a crossing has to be
  confirmed over several readings before you're notified.

County Line needs "Allow all the time" location access to detect crossings while it's
closed; that is the app's whole purpose. If you grant only "While using the app", it tells
you background detection is off.
```

### Graphics (`docs/store/`)

| Asset | File | Spec |
|---|---|---|
| App icon | `icon-512.png` | 512×512, 32-bit PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| Phone screenshots | `screenshots/1-permission.png`, `2-active.png`, `3-disclosure.png` | 1080×2400 |

The SVG sources (`icon.svg`, `feature-graphic.svg`) are alongside the PNGs if you want to
tweak them (`rsvg-convert -w W -h H in.svg -o out.png`).

> The bundled screenshots are emulator captures — fine to launch with, but **retake them on
> a real device** during internal testing for the best listing.

---

## 3. Play Console declarations

### 3.1 App content → Permissions → Location

You'll be asked to justify `ACCESS_BACKGROUND_LOCATION`. Suggested answers:

- **Core feature that uses background location:** "County-crossing notifications — the app's
  only feature. It compares the device's current US county to the last known one and
  notifies the user on a change."
- **Why foreground / approximate location is not enough:** "The feature is to alert the user
  *while the phone is in a pocket or the app is closed*, so it cannot be foreground-only.
  Approximate location is too coarse to place the device on the correct side of a county
  line."
- **Prominent disclosure:** the app shows an in-app disclosure dialog
  (`BackgroundLocationDisclosureDialog`) stating what is collected, that access happens in
  the background, and why — *before* the OS permission prompt. Screenshot:
  `docs/store/screenshots/3-disclosure.png`.
- **Demo video:** record a screen capture showing: open app → tap "Grant location" → grant
  foreground → tap "Grant background location" → the disclosure dialog → "Continue" →
  choose "Allow all the time" → toggle tracking on → the "Active" state. Upload to YouTube
  (unlisted) and paste the link.

### 3.2 App content → Foreground service permissions

Declare **`FOREGROUND_SERVICE_LOCATION`**:

- **Purpose:** "Continuously check the device's county in the background and post a
  notification when it changes."
- **User-facing:** yes — an ongoing "Tracking your county" notification is shown the whole
  time the service runs (`Notifications.serviceNotification`).
- Same demo video as above can be reused.

### 3.3 Data safety

| Question | Answer |
|---|---|
| Does your app collect or share user data? | **Collects** (Location → Approximate & Precise location). **Does not share.** |
| Is data processed ephemerally? | Location is processed on-device; last-known county is stored on-device. Not "ephemeral" per Google's definition, but not transmitted. |
| Is data transferred off the device? | **No.** |
| Is data encrypted in transit? | N/A — no transmission. |
| Can users request deletion? | Yes — uninstalling removes all stored data. |
| Purposes | "App functionality" only. |

### 3.4 Content rating

Complete the IARC questionnaire — this app has no objectionable content; expect **Everyone**.

### 3.5 Target audience

Not directed at children. Target age 18+ (or 13+); it collects location, so keep it out of
the "designed for families" program.

---

## 4. Build & upload

1. **Bump `versionCode`** in `app/build.gradle.kts` (must strictly increase on every upload).
   Update `versionName` for user-facing releases.
2. Build the App Bundle:
   ```sh
   ./gradlew clean test lint bundleRelease
   # -> app/build/outputs/bundle/release/app-release.aab
   ```
3. Sanity-check the AAB signature:
   ```sh
   jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | head
   ```
4. Upload the `.aab` to a track (Internal testing first).
5. In the release's **Release notes** field, paste `docs/store/whatsnew/en-US.txt`
   (≤500 chars/language). Update that file + `CHANGELOG.md` for every user-facing release.

CI alternative: set `COUNTYLINE_KEYSTORE`, `COUNTYLINE_KEYSTORE_PASSWORD`,
`COUNTYLINE_KEY_ALIAS`, `COUNTYLINE_KEY_PASSWORD` and run `./gradlew bundleRelease`.

---

## 5. Testing track progression

1. **Internal testing** — add your own devices/emails; installs in minutes. Verify the
   permission flow, disclosure dialog, a real county crossing, reboot persistence.
2. **Closed testing** — new personal accounts *must* run this with **≥12 testers opted in
   for ≥14 continuous days** before production access is granted. Recruit early.
3. **Open testing** (optional) — public beta.
4. **Production** — apply for production access (new accounts), then promote a release.

Watch the **Pre-launch report** (Play runs the app on real devices) for crashes/ANRs and
the **Android vitals** dashboard after launch.

---

## 6. Pre-submission checklist

- [ ] Real `upload-keystore.jks` generated and backed up; test keystore deleted
- [ ] `keystore.properties` filled in (and still git-ignored)
- [ ] `versionCode` bumped
- [ ] `./gradlew clean test lint bundleRelease` green
- [ ] Privacy policy hosted; URL + contact email filled in
- [ ] Prominent disclosure verified on device before the OS prompt
- [ ] Demo video recorded and uploaded
- [ ] Permissions declaration + foreground-service declaration submitted
- [ ] Data safety form completed (nothing transmitted)
- [ ] Content rating questionnaire done
- [ ] Store listing text + icon + feature graphic + ≥2 screenshots uploaded
- [ ] Release notes pasted from `docs/store/whatsnew/en-US.txt`; `CHANGELOG.md` updated
- [ ] `targetSdk` meets Play's current minimum for new apps (currently 35 — already set)
- [ ] Internal testing build installs and the full flow works
