# NewsBrief 0.4.12 — feed sync, source selection and original pages

Feed synchronization records the start of every attempt, including failed and cancelled
attempts, in persistent preferences before making requests. Repeating the same source
configuration within three minutes returns the cached feed without making feed requests
or scheduling another article-prefetch batch. The repository owns the check, so it covers
app startup, feed/tab return, pull-to-refresh, the refresh button and WorkManager.
A manual attempt during the cooldown shows the remaining seconds; automatic checks stay
quiet. Concurrent requests are serialized. A backwards device-clock correction allows a
new attempt rather than suppressing refresh indefinitely.

Adding, enabling or changing sources (or changing proxy endpoints) changes the configuration
and permits an immediate sync, preserving the existing source-update behavior. The timer
starts at the beginning of an attempt, not when all sources finish. Article refresh and
background article/image prefetch remain separate from feed synchronization.

The source dropdown is replaced by horizontally scrollable multi-select chips. Selected
publishers combine their articles; tapping a selected chip removes it. “Все источники”
clears source selection. Topic categories still narrow the list, and “Сбросить” clears the
whole filter. RSS, public Telegram and Telegram-proxy variants of known publishers share
their publisher grouping. Filtering still happens in Room before the 250-article limit;
bookmarks and disabled-source history remain available offline.

The native Reader's “Открыть во встроенном браузере” button is now its first content
control, above the title, images and body, and remains usable when extraction fails.
The embedded browser has two explicit modes:

- “Через загрузчик” retains the existing NewsBrief transport, proxy rules and `/image` loading.
- “Оригинальная страница” uses native WebView transport, first-party cookies, scripts and
  DOM storage for ordinary HTTPS publishers, including The Warsaw. This is an explicit
  user choice, not an automatic fallback after a Worker failure. It can override the
  inherited Telegram-link routing for those ordinary publishers.

Meduza, Zerkalo and their supported mirrors still use the Worker in either mode. Navigating
to a required-proxy page locks subsequent requests to the Worker and disables scripts.
File/content access, mixed content, native JavaScript bridges and external-app navigation
remain disabled. Original-page mode keeps third-party cookies disabled. HTTP/load failures
show a message with retry/mode choices. Switching mode recreates the view at the current
page, releasing the previous WebView.

Verification: 154 offline JVM/Robolectric tests pass, including persistent cooldown after
failure/recreation, the exact three-minute boundary, concurrent attempts, source changes,
multi-publisher unions, actual Compose multi-selection and Reader button placement, and
WebView mode settings/interception/protected-source failure behavior. An initial Compose
test clicked a chip outside the horizontal viewport; it now scrolls the chip into view
before clicking, and the focused and final full suites pass.

Version 0.4.12 and its test APK were installed on the Motorola without clearing data. The
opt-in `OriginalPageLiveTest` passed against the existing Warsaw Telegram post 21641:
the real original publisher page rendered in NewsBrief, with JavaScript enabled, more than
500 text characters and the expected Warsaw heading. This does not promise that every
publisher will allow embedded browsers. Phone data-preservation counts and the rendered
page result are recorded in `validation/phone-0412-install.json`.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; Room remains schema 8.
The Telegram service URL is still configurable and its deployment is separate from this update.
