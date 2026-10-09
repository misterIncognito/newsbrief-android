# NewsBrief Android build verification

The `release` branch has now been promoted from `dev` and contains the verified
minimal shell. `main` and `v0.4.12` still identify the deployed full application.
This branch promotion does not change the app version, create a tag, push or deploy.

Current `dev` is a minimal shell: **2 passing tests**, debug APK built. Core feature
branches pass **9 / 18 / 41 / 66 / 151** tests in dependency order. The synced AI and
article-plugin branches pass **152 / 153**; their temporary combined merge passes
**154**. All final builds pass. Initial failures and their fixes are recorded in
[core extraction results](validation/core-feature-branches-results.json) and
[resolved failure excerpts](validation/core-feature-initial-failures.txt).
See [branch workflow](BRANCHES.md) for prerequisites and PR targets.

Current shell APK: `app/build/outputs/apk/debug/app-debug.apk`.
Verified combined APK: `app/build/outputs/apk/verified/newsbrief-all-features-debug.apk`.
Both are local build outputs and are not installed on the phone.

The extraction did not deploy an APK or change `main`, `release`, `v0.4.12` or on-device
data. The historical release/deployment results below describe the deployed app.
Earlier split records: [AI](validation/ai-branches-results.json) and
[article plugins](validation/article-plugin-branches-results.json).

Latest deployed release: **0.4.12**, **154 passing offline tests**, zero failures/errors/skips.
Debug APK and Android test APK built successfully. The update adds a persistent three-minute
cooldown between repeated feed-sync attempts (including failures), multi-select publisher
chips, the browser action above article content, and an explicit original-page WebView mode.
New/enabled sources still load immediately. Meduza/Zerkalo continue to use the Worker.

Version 0.4.12 was installed on the Motorola and launched successfully. The real Warsaw
original-page instrumented check passed (**1 device test**), rendering 28,554 characters
and the expected article heading inside NewsBrief. All **1,184 original articles** and
**21 source definitions** were retained; 1,190 articles were stored after refresh.
Room remains schema 8. A private local backup was taken before installation.

An initial new Compose test attempted to click an offscreen chip; it now scrolls the
horizontal source list before clicking. Focused and final full suites pass.
See [FEED-BROWSER-0412.md](FEED-BROWSER-0412.md),
[build/test results](validation/feed-browser-0412-results.json),
[phone verification](validation/phone-0412-install.json) and
[phone test output](validation/phone-0412-warsaw-test.txt).
Previous plugin changes: [PLUGINS.md](PLUGINS.md).
Previous live AMP checks: [AMP-READER.md](AMP-READER.md).

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
