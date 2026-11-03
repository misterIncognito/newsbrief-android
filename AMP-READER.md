# NewsBrief 0.4.9: publisher AMP articles

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

Both native article opening and background prefetch now discover the publisher's
`<link rel="amphtml" href="…">` in the original HTML. Relative links and HTML base URLs
are supported. Only valid HTTPS alternatives without credentials are accepted. Discovery
follows one alternative, skips already-AMP pages and self-links, and never guesses URL paths.
The convention is documented in the [official AMP discovery guide](https://amp.dev/documentation/guides-and-tutorials/optimize-and-measure/discovery).

AMP is parsed into the native reader, including `amp-img`, paragraph boundaries, links,
title, author and publication date. JSON-LD metadata is supported, including `@graph`,
multiple authors, and image objects/arrays. Article photos and structured article covers
take priority over generic social-sharing images; all valid image alternatives are retained. Invalid date metadata does not prevent a valid
structured date from being used. AMP ads, analytics, templates and navigation are removed.
Zerkalo's `.b-article` container is supported without including site navigation or related
article panels. Missing AMP metadata falls back to the original page and saved feed data.

The article retains its original publisher URL in Room, its browser action and cache identity;
AMP does not create a duplicate feed card. Telegram posts remain complete feed items.
Meduza, Zerkalo and Telegram-linked publishers keep forced Worker routing during discovery,
AMP loading and fallback, even if the advertised AMP page is on another host. There is no
direct fallback for these requests. Images continue using `/image` and the shared offline
cache. Other sources retain normal routing.

Missing, failed or empty AMP pages fall back to the original article. Protected and
Telegram-linked articles use Worker `/article` extraction for that fallback. Failed original
HTML discovery can also fall back to Worker extraction. If all loading fails, saved text is
retained. Cancellation propagates without starting a fallback request.

Previously saved full articles still open without network access. Explicit reader refresh
checks for AMP again. Pending prefetch uses the same new loading path and preserves longer
RSS/Atom text. The existing 12-article, 48-hour, two-concurrent-download prefetch limits apply.
Room schema 6 and existing image cache names are retained; no new migration is needed.

## Validation on 9 October 2026

`testDebugUnitTest assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
**120 offline tests pass**, zero failures/errors/skips. Sixteen new tests cover discovery,
unsafe/self-links, AMP markup and structured metadata, original metadata errors, empty
structured image objects, protected cross-host alternatives, failure/cancellation, and
Telegram AMP prefetch into Room with offline reopening and cover caching. Existing routing,
RSS/Atom parsing, database migrations, native feed interactions and storage regressions pass.

Opt-in live tests run the app's actual `ArticleReader`, `NewsHttp` and image cache against
the deployed Worker:

```sh
./gradlew :app:testDebugUnitTest -PverifyAmpReaders=true --tests '*AmpReaderLiveTest'
```

**3 of 3 live checks pass; the opt-in test command succeeds.**

| Check | Result |
| --- | --- |
| Provided Meduza article → advertised `/amp/news/…` through `/fetch` | PASS: native article plus article-specific cover through `/image`, then offline cache read |
| Published Zerkalo mirror → `/news/amp/economics/138172.html` | PASS: native article plus real cover through `/image`, then offline cache read |
| dev.by linked article without AMP | PASS: Worker `/article` fallback, cover through `/image`, then offline cache read |

An initial live run failed on Meduza's generic sharing image
`https://meduza.io/images/share-meduza.png`: `/image` returned HTTP 502 with body
`Upstream returned 502`. The article also advertises an article-specific cover in JSON-LD,
which works through the Worker. Image priority was corrected to prefer article images,
and the final live tests verify the image the reader actually selects with the same strict
image validation and offline-cache assertion. The initial failure log is retained at
validation/amp-live-049-initial-output.txt.
No direct connection was attempted. Canonical Zerkalo availability is not claimed by the
mirror test. The deployed Worker was not modified.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (version 0.4.9, code 13).
Offline report: `app/build/reports/tests/offline049/index.html`.
Live report: `app/build/reports/tests/ampLive049/index.html`.
Exact counts/hash: validation/amp-049-results.json.
Logs: offline build, live checks.

The phone was unavailable over USB. This version has not been installed or tested on the
phone; the Android test APK compiled successfully. Existing phone app data was not changed.
