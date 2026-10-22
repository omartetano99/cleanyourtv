# Launcher 2.10: early boot, remote input and rendering

## Observed on Android 11 CAIXUN / m7332

- Vendor `com.cultraview.tvboot` crashed in `TvBoot.startHome` with `ActivityNotFoundException` for MAIN/HOME at startup.
- `cmd package resolve-activity --brief --query-flags 524288 -a android.intent.action.MAIN -c android.intent.category.HOME` returned `No activity found` with installed 2.9. Normal Home resolution succeeded. This isolates the missing Direct Boot registration.
- CinemanaResume was the only enabled accessibility service; `dumpsys input` reported `InputFilterEnabled: true`. The service requested global key filtering despite targeting Cinemana events. Removing that filter is the proposed remedy for held volume keys; physical remote confirmation is pending.
- A startup `gfxinfo` sample for 2.9 recorded 37 janky frames out of 84, including slow UI thread and bitmap uploads. This is diagnostic evidence, not a controlled before/after benchmark.

## Changes

- Make Home available in Direct Boot. While the user is locked, display a gradient startup window without reading credential-protected preferences or starting media/network loaders. Recreate after unlock, retaining existing settings and recovery records.
- Remove global accessibility key filtering entirely. Cinemana navigation observes focus/click events for cancellation, with a short grace period around automatic actions; Home still cancels recovery. Physical Back/D-pad cancellation and banner navigation need live acceptance checking.
- Load installed apps/artwork on a worker, reuse unchanged app assets, and avoid rebinding an unchanged dock. Shorten app focus animation and remove overshoot.
- Reuse shelf views when row order is unchanged and apply card diffs. Changed playback progress or catalogue entries no longer rebuild unrelated shelves; unchanged refreshes no longer request focus/scroll again.
- Remove obsolete queued thumbnail work when cards recycle, use two download workers, and prepare bitmaps off the main thread. Preserve the existing wide banner dimensions, quality, metadata line, row placement and red gradient.
- Cancel delayed wake callbacks on screen-off; recheck interactivity before delayed recovery. Reject empty/buffering/uncontrollable surviving sessions instead of consuming recovery on them. Periodic playback snapshots persist asynchronously; screen-off still commits the recovery marker.

## Validation (2026-10-23)

- `assembleDebug assembleDebugAndroidTest lintDebug --offline`: successful.
- `git diff --check`: clean.
- All 20 instrumentation regression checks passed on the physical TV using a temporary isolated application ID (`com.gathin.tvhome.verification`). Its HOME category was removed so it could not replace the selected launcher. Normal source and instrumentation were otherwise the same.
- Removed both temporary test packages afterward. Confirmed default Home and enabled accessibility services still point to installed 2.9.
- The new checks cover directBootAware registration, absence of key-filter capability/flag, rejection of unusable media sessions, retained card views when another shelf changes, and thumbnail request cancellation. Existing checks cover recovery state, row ordering/hiding, focus/navigation, banners/metadata, and feed parsing.
- The production APK is built but not installed. True pre-unlock rendering, repeated sleep/reboot cycles, physical held-volume behavior, Cinemana helper interaction and comparative performance still require post-install acceptance checks. Cinemana full power-cycle video resumption remains unproven.

## Acceptance after installation is authorized

1. Preserve the current APK and accessibility-service list; install 2.10 over 2.9 without clearing app data. Confirm Home remains selected and the helper rebinds. Never overwrite other accessibility services.
2. Verify direct-boot Home query now returns MainActivity, and InputFilterEnabled is false if no other service requires filtering.
3. Hold volume down/up on the physical remote from Home and from a video. Confirm repeated changes and release stops immediately.
4. Reboot from Home: see the startup window/Home, and no vendor missing-Home crash. Repeat standby/wake from Home and from SmartTube; verify the last video/position and Home cancellation. Allow the network time to reconnect after deep standby.
5. Open Cinemana banners, cancel navigation with Home/Back/D-pad, verify rating/genre/title alignment, and navigate rapidly across shelves. Capture comparative gfxinfo under identical warmed navigation sequences before claiming a speed improvement.

## Review scope

The PR targets `codex/launcher-2.9-baseline` in the owner's fork. That branch preserves the previously uncommitted custom launcher, allowing this PR to show only the stability changes. No device dumps, backups, APKs, signing keys or unrelated tools are included.

References: [Android Direct Boot](https://developer.android.com/privacy-and-security/direct-boot), [accessibility key filtering](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS).
