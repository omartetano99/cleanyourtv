# TV Home 3.0 — local build and TV test handoff

This change is prepared for hardware testing. It does not flash firmware, modify Cinemana's APK, or install anything on a TV as part of building it.

## What changed

- **Cinemana 4K History:** a local shelf records observed playback and titles visible on Cinemana's History screen. The empty shelf opens History for importing. Existing accessibility access is required to navigate titles; existing media access supplies progress. The helper recognizes the Arabic shelf heading `المشاهدات`, can scroll to an older title, and stops on ambiguous matches. A series opens the episode selector for the user to choose an episode. Public catalogue searches enrich covers/rating/genres only when an exact title is unique. Imported older pages never move above actual playback.
- **Stremio discovery:** Popular films, Popular series, New films and New series from Cinemeta, alongside Stremio's existing TV-provider shelves. Links are restricted to Stremio and open its details screen. These discovery rows are not an account library or personal watch-history sync.
- **All apps:** TV and sideloaded apps, alphabetically arranged for D-pad navigation. The system-app toggle also shows installed packages without a launch screen; selecting those opens app information. Hold OK on an app for its information page.
- **Inputs:** a dedicated `TvView` connects to the chosen HDMI/AV input ID. Connection failures and no-signal states have retry, vendor-app and Home actions. Sessions and audio focus are released when leaving. Vendor Live TV is launched normally instead of treating its tuner as an HDMI passthrough.
- **Home and settings:** the red gradient and Cinemana banners above the dock remain. The header adds Apps and a separate Home settings screen. Focus targets have a contrasting mint outline. Existing shelf views and adapters survive additions and reordering; overlapping catalogue refreshes are coalesced.
- **Ambient display:** an illustrated river, palms, skyline and sky respond to Baghdad time and current weather. The display includes a clock, weather, one fixture per league (rotating upcoming matches every five minutes), and an estimated drive to Harthya. Data sources load independently, cache responses and explicitly label stale/offline data.

## Configuration

Open **Home → Settings**. The app can preview the ambient screen, change Home's idle delay (off/2/5/10 minutes), toggle football and reduced motion, set origin/destination coordinates and label, open the route in Google Maps, import/clear local history, and open Android's media-access, accessibility and screensaver settings.

The public defaults use Baghdad weather and the **Harthya neighbourhood centre**, not a particular building. The origin defaults to empty. The owner's origin is stored only in a private configuration file outside this repository, ready to apply at TV testing. Enter another origin on each TV; do not publish personal coordinates in source, screenshots or PR descriptions.

Coordinates are sent to OSRM to calculate the route. Choosing **Open route in Google Maps** sends the configured locations to Google. No Google API key is needed. The on-screen estimate uses road routing **without live traffic**, not Google's live ETA. Open-Meteo and the public ESPN scoreboard require no account for this implementation; public endpoints can become unavailable. This is a personal launcher configuration, not a service availability guarantee.

## Idle and playback behavior

Automatic launch from Home requires all of the following: Home is resumed, its window has focus, the display is interactive, the delay is enabled, no restoration is pending, and the media guard reports no active playback. Dialogs and input reset/suspend the timer. External apps are not overlaid by the Home timer.

The system DreamService uses the same display, but Android must select it as the system screensaver. Both automatic surfaces refuse to run without media access and exit when a session is playing, buffering, connecting or seeking, or when audio is active. The manual preview is intentionally available without media access. Active HDMI keeps the screen awake. The helper does not request global key filtering; volume-key repeat handling remains with Android.

The landscape renders on a 15-second tick, not a continuous animation loop. Text drifts slightly to reduce static placement, and the screen fades in. Reduced motion disables drift, cloud movement and entry animation. Native bitmap thumbnail caches remain bounded, and workers are stopped with their owning screen.

## History limits

Cinemana's private database is not accessible to another ordinary app. This implementation does **not** claim a complete account-history import: it accumulates playback plus History pages the user visits (including pages traversed while finding a selected entry), up to 50 titles. Text-only entries are intentional when artwork is unavailable or matching is ambiguous. Titles are the accessible identifier; remakes with identical titles cannot be distinguished reliably without an app-provided ID. Movie resume and series selection must be verified with the installed Cinemana version.

No repackaging is currently needed. Re-signing Cinemana would prevent an ordinary in-place update with its original signature and could require removing the installed copy; the local helper avoids that migration.

## Build and local verification

Requirements: JDK 17, Android SDK 34, Gradle wrapper. First test setup downloads JUnit and Robolectric; later builds can use `--offline`.

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk \
  ./launcher/gradlew -p launcher testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain
```

Optional live checks use the actual app HTTP/cache client against all four Cinemeta catalogues, Baghdad weather, all three ESPN scoreboards and an OSRM route between public Baghdad locations:

```sh
TVHOME_LIVE_SMOKE=1 JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk \
  ./launcher/gradlew -p launcher testDebugUnitTest --rerun-tasks --console=plain
```

Unit tests cover malformed data, timezone conversion, sunrise/sunset, obsolete fixtures, route coordinate validation, active/transitional playback states, history ordering/bounds, ambiguous cover matching, shelf reuse, independent order/hiding, and header/layout bounds. Native Robolectric rendering writes previews under `launcher/app/build/reports/smart-home/`. **These are Android view renders with synthetic fixture data, not photographs or captures from the TV.**

Debug APK: `launcher/app/build/outputs/apk/debug/app-debug.apk`.
Android instrumentation APK: `launcher/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
The pre-existing device regression suite is compiled; it is not executed by host unit tests.

## Hardware validation after installation is authorized

1. Back up the installed launcher APK and its preferences. Install the update with `adb install -r`, preserving app data and existing helper access. Apply the owner's private origin through Home settings or private provisioning; do not include it in a shared handoff.
2. Confirm each connected HDMI port and AV switches to the selected picture, audio plays, unplugging produces an actionable no-signal screen, and Back returns Home. Verify the vendor-app fallback on this TV.
3. Open Cinemana History, return Home and inspect imported titles. Play a movie, return and confirm progress. Select an older movie and verify the exact title/position; verify series opens episode selection. Do not accept a different film as success.
4. Open each Stremio row and verify its owning app handles the detail link. Change order/hide one row, refresh and confirm focus/order persist.
5. Open sideloaded and system apps through All apps; hold OK for information. Check D-pad navigation through a long app grid and long history shelf.
6. Preview the ambient display, confirm real data and route configuration, then test timed idle from Home. Start playback and buffering in each video app and verify the display does not interrupt it. Select TV Home's system screensaver if desired and repeat with that entry point.
7. Test standby, full reboot, returning Home, and **physical volume-button hold**. Keep the previous APK and prefs available for rollback. No firmware changes are part of this test.

## Provider references

- [Android TvView](https://developer.android.com/reference/android/media/tv/TvView)
- [Stremio's tested detail-link format](https://github.com/Stremio/stremio-core/blob/development/src/unit_tests/deep_links/meta_item_deep_links.rs)
- [Cinemeta manifest](https://v3-cinemeta.strem.io/manifest.json)
- [Open-Meteo documentation](https://open-meteo.com/en/docs)
- [OSRM routing API](https://project-osrm.org/docs/v5.24.0/api/)
- [Google Maps URLs](https://developers.google.com/maps/documentation/urls/guide)

ESPN's public scoreboard is an undocumented endpoint and is isolated in `AmbientRepository` so it can be replaced if its contract changes. Cached fixtures show their retrieval time. Cinemana integration uses visible semantic IDs from the installed app and public metadata only; no account tokens are extracted.
