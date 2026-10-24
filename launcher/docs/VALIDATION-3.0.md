# Local verification — TV Home 3.0

- Build: version 3.0, version code 13; Android debug APK and device regression APK produced.
- Host tests: **20 passed, 0 failed, 0 skipped** with the optional public-service check enabled.
- Android lint: **0 errors, 55 warnings**. Warnings include existing platform/deprecation issues, English UI literals and infrequent Canvas shader allocations; they are not suppressed by a new baseline.
- Live HTTP contract check: all four Stremio catalogues (including HTTPS redirects), Open-Meteo, all three ESPN scoreboards and an OSRM route returned parseable data through the app's actual client.
- Native Android view renders: ambient, settings and Home layout previews reviewed. Synthetic data only; no TV screenshot or private location is included.
- APK SHA-256: `ad957f9e6318430d0b46add1c496e8a13b2ebf5dfade7533aeda7218388b6b56`.
- TV installation, HDMI tuning, remote hold/repeat, Cinemana navigation/resume, Stremio installed-app links, standby and DreamService behavior: **pending hardware testing**.

The test classes contain 4 Home navigation checks, 12 data/history checks, 3 native layout/render checks and 1 optional multi-provider live check. The existing on-device instrumentation suite compiles but was not run against the TV during this change.

The original Cinemana installation and account data have not been modified. The configured personal starting point stays outside the repository. This change is based on the earlier launcher stability branch and is prepared as a separate review PR.
