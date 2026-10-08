# Source selection and refresh fix — 0.4.1

9 October 2026, Europe/Warsaw.

## Problem and behavior

Disabling sources did not change the feed: the Room query returned articles from
all saved sources. On the connected phone, Meduza and Polsat were disabled, but
80 of the 384 stored articles still belonged to those sources.

Feed queries now join enabled sources. Disabling/removing a source updates the
observed feed immediately; cached articles remain in Room. New summaries use
articles from enabled sources too. Re-enabling a source restores cached articles
and starts a refresh. Re-adding a deleted source reassociates matching cached
articles without replacing saved reader text.

Refresh requests arriving while another refresh is active used to be discarded.
They now queue another pass with the current source list. Cancellation propagates
through refresh instead of becoming a per-source error. Feed labels now describe
selected sources instead of claiming the retained history covers only 24 hours.
No schema migration or data reset is required.

## Verification

`bash scripts/build-on-mac.sh`: **BUILD SUCCESSFUL**. 33 tests, zero failures.
Four added regressions cover immediate filtering/data preservation, adding a
source during a blocked refresh, re-adding a deleted source and automatic fetching
when enabling a source. Existing parsing, routing and migration tests also pass.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `512d6ea8ea4b265a6ecaa45b5231788d78a5deef3c50e44e0c7ad3c5d33bd6a2`.
Build output: [validation/source-refresh-build.txt](validation/source-refresh-build.txt).

Installed with replacement (no uninstall/data clear) on the connected Motorola
Edge 70 Fusion; package version 0.4.1, versionCode 5. Launch succeeded and the
process stayed running. After update, all 384 saved articles and the existing
source settings remained. 304 articles belong to enabled sources; the feed limits
the display to the latest 250. UI verification was initially blocked by the phone
lock screen. Worker limitations documented in BUILD-RESULTS.md are unchanged.
