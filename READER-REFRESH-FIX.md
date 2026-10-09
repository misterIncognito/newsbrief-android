# NewsBrief 0.4.6: Telegram article links, refresh gestures and embedded browser

## Changes

Telegram collection previously flattened anchor text and discarded link targets. The native
reader then returned cached Telegram text unconditionally, including on explicit refresh.
Posts now store safe HTTPS link labels/targets in Room. Existing posts hydrate links from the
exact Telegram post ID, never a neighbouring post. Offline post text remains available if
Telegram fails; refreshing feeds backfills links without replacing saved reader bodies.
The reader exposes publisher article links, and nested reader navigation returns to the post.

An embedded WebView is available from **«Открыть во встроенном браузере»**. From a Telegram
post this opens its publisher article when identified; from an article it reopens that article.
The browser stays inside NewsBrief, provides reload, browser history and **«К статье»**.
It renders public HTTPS pages with JavaScript disabled; login and form submission are outside
this reading flow. File/content access is disabled. Native reader text remains the offline mode.

All browser HTTP requests use the same redirect-aware transport as the reader. Protected
publishers and their embedded resources go through the Worker; image requests use `/image`.
Failures return an explicit error response, so WebView cannot fall back to a direct connection.
Redirected documents preserve MIME/charset and resolve relative links against their final URL.

Zerkalo Telegram publishes `zerkalo-*.site` mirror links, which now require the proxy just like
`zerkalo.io`. The observed mirror article and its HTML both returned HTTP 200 through the
Worker. The canonical `news.zerkalo.io` article still returned HTTP 502 (upstream 403).
No direct connection is used to work around that upstream block.

dev.by direct article HTML returned HTTP 200 with the app user agent. The reader extracts its
article body separately from header/footer content. All saved links can be opened in the native
reader; publisher links have a prominent full-article button.

The news list uses Material pull-to-refresh, including empty results. One- and two-finger
pulls at the top initiate refresh; pulling within a scrolled list continues scrolling.
Refresh uses the existing coalesced source refresh and retains feed filters.
The top bar now respects the system status-bar inset so navigation buttons are accessible.

## Validation

`testDebugUnitTest assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
87 offline tests, zero failures/errors/skips, including exact-post link hydration,
offline preservation, native reader back navigation, browser routing/MIME/redirects,
failed proxy requests without direct fallback, and Room migrations 1/2/3/4 → 5.
The 4 → 5 migration is validated by Room and checked after database close/reopen.

Four instrumentation tests passed on the connected Motorola: one-finger refresh,
two-finger refresh, empty-feed refresh, and no refresh while inside a scrolled list.
These ran before the browser addition, using the same refresh component.

The reader/refresh update was installed over the existing app. The phone database migrated
4 → 5, preserving all 665 original article links, every nonempty saved body and all source
settings. No data was cleared. The phone disconnected before installing the final
browser-enabled build. Its live native-reader/browser checks are built but still pending.

## Reproduce on a connected phone

Build using `bash scripts/build-on-mac.sh`. Also build `:app:assembleDebugAndroidTest`.
Install both debug APKs with `adb install -r` to preserve existing data. Run:

```sh
adb shell am instrument -w -r -e class io.newsbrief.app.FeedPullRefreshTest \
  io.newsbrief.app.test/androidx.test.runner.AndroidJUnitRunner
```

The opt-in `TelegramReaderLiveTest` uses the already saved dev.by post 16806 and Zerkalo
post 176330 on this phone. It opens each real publisher article in the native reader,
opens the embedded browser, verifies the page title and returns to the original post.
It requires internet and those existing posts; it skips absent posts without seeding or
clearing the user's data. Enable it with `-e verifyArticleReaders true` and select that class.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Exact counts, APK SHA-256, installation status and HTTP results:
[validation/phone-046-results.json](validation/phone-046-results.json).
Build log: [validation/build-046-output.txt](validation/build-046-output.txt).
Gesture results: [validation/gesture-046-output.txt](validation/gesture-046-output.txt).
