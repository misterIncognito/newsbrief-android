# NewsBrief Android build verification

Latest build: **0.4.11**, **150 passing tests** (147 offline + 3 live article/image
checks), zero failures/errors/skips. Debug APK and Android test APK built successfully.
Source collection and article extraction use separate plugin registries. The Telegram
service connector includes encrypted access-key storage, bearer renewal, channel discovery,
full posts and entity links. Service deployment is pending, so its base URL is configurable;
Telegram API verification uses deterministic contract fixtures.

Room 7→8 preserves existing data and persists plugin identity; migrations from versions 1–7
pass. Compose checks cover source-plugin choices, the unconfigured-service notice and
connection settings alongside existing Save/Share, return refresh and scroll behavior.
Live Meduza AMP, Zerkalo AMP and devby Readability checks pass, including `/image` downloads
and offline image-cache reopening. No phone is currently attached, so 0.4.11 is not installed.

One initial new UI test matched both a source label and the plugin selector; restricting
its assertion to clickable controls fixed the test. The focused and final full suites pass.
See [PLUGINS.md](PLUGINS.md), [validation/plugins-0411-results.json](validation/plugins-0411-results.json)
and [validation/build-0411-output.txt](validation/build-0411-output.txt).
Previous 0.4.10 changes: [SAVED-REFRESH.md](SAVED-REFRESH.md).
Previous live AMP checks: [AMP-READER.md](AMP-READER.md).
Earlier phone checks: [READER-REFRESH-FIX.md](READER-REFRESH-FIX.md).

The results below record the original 0.4.0 build.

Verified on Mac mini (Apple Silicon), 9 October 2026, Europe/Warsaw.
Original archive Git history retained; starting commit `575bc30`.

## Build and tests

`bash scripts/build-on-mac.sh` ran `:app:testDebugUnitTest :app:assembleDebug`.
**BUILD SUCCESSFUL**, 29 tests, 0 failures/errors, no skipped tests.

- Proxy routing: required hosts, external images, failure without direct fallback,
  redirects into required sources, and rejected Worker redirects.
- RSS/Atom/RDF/Telegram parsing: full encoded HTML and XHTML, images and DTD rejection.
- Article JSON: actual Worker field layout, paragraphs, title, date, author,
  multiple images; direct HTML parsing; saved short articles open without fetching.
- Real SQLite/Room migration tests under Robolectric: version 1 → 3 and 2 → 3,
  preserving sources, article dates/text/images and summaries. Room validates the
  migrated schema; saved reader content survives database close/reopen.

APK: `app/build/outputs/apk/debug/app-debug.apk` (11,446,614 bytes).
SHA-256: `da22bc9bd32f8f7c3ecbb2b5c9db877c406370145fb1c65ded6df3d0af6bed60`.
Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Build output: [validation/build-output.txt](validation/build-output.txt).

Tools: Temurin JDK 17.0.20.1, Android SDK platform 36, build tools 35.0.0,
Gradle Wrapper 8.13, AGP 8.13.2, Kotlin 2.3.20.
The local tools are outside the Git repository, in sibling `.toolchain`.
`local.properties` contains the local SDK path and is intentionally ignored.

Initial failures fixed: obsolete Kotlin `kotlinOptions.jvmTarget` DSL,
Compose BOM requiring API 37/AGP 9.1 (pinned to compatible 2025.08.01),
and incorrect `rememberSaveable` import. Final build has no compilation failures.
The build reported an unstripped graphics library on its initial full build;
this did not prevent packaging.

## Live Worker results

| Check | Actual result |
| --- | --- |
| `/health` | HTTP 200, `{"status":"ok"}` |
| `/fetch`, Zerkalo RSS | HTTP 200, RSS XML |
| `/fetch`, Meduza RSS | HTTP 200, RSS XML |
| `/article`, supplied Meduza article | HTTP 200, 1,311 text characters, 6 paragraphs |
| `/image`, Zerkalo logo | HTTP 200, PNG |
| `/article`, Zerkalo RSS article | HTTP 502, `Upstream returned 403` |

Observed article keys: `url`, `title`, `description`, `author`, `publishedAt`,
`imageUrl`, `imageProxyUrl`, `text`, `paragraphs`, `extraction`.
The parser preserves paragraph boundaries and the date. Original image URLs are
saved so requests can use the current configured Worker, without double encoding.
Results: [validation/worker-results.json](validation/worker-results.json).

## Remaining acceptance limitations

Zerkalo full-article extraction is blocked upstream. Its feeds and images pass;
the reader keeps available feed/offline text and reports extraction failures.
It never retries that source directly. Worker deployment was not changed.

No connected Android device or running emulator was found. APK installation,
Compose rendering, on-device refresh and airplane-mode checks have not been
performed. Tests exercise parsing/routing and real Room storage, but do not
substitute for these device checks. AI summaries require the user's API key;
no paid AI request was made.

Downloaded images persist in private application files; Room stores image URLs
and article metadata. Images not previously downloaded remain unavailable offline.
Saved history is retained, and the feed shows the latest 250 saved articles across
all dates. Summaries still use the last 24 hours.
