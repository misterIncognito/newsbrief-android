# NewsBrief 0.4.7: Telegram posts in the feed, proxied article links

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

Telegram posts are full feed items. Their saved text and photos appear directly in the
news list, with selectable text and clickable embedded link labels. They no longer use
a four-line excerpt or require opening a Telegram-post detail reader to follow a link.
Recovered links whose labels differ from an old saved body remain available as buttons.
Visible old posts recover their links by exact post ID while keeping cached text readable.
Failed recovery can retry when the post re-enters the feed.

Tapping an embedded link opens the publisher article directly in the native reader.
Back returns to the feed. A separate browser action opens the same linked publisher page
inside NewsBrief. Both carry a forced-proxy context from the Telegram post, including for
dev.by and publishers outside the required-host list. Following further links in that
reader/browser retains this context. No failed Worker request falls back to direct access.

Room schema 6 stores the proxy requirement on linked articles, so later reopening or
refreshing cannot lose the routing choice. Previously cached direct articles are promoted
to proxy-required when followed from Telegram without discarding their offline body.
Migration 5 → 6 adds one column with a default, preserving the schema-5 links and all
articles, sources, settings and summaries. Earlier supported migrations are also retained.
Normal RSS articles continue to use their existing source routing; Telegram channel
collection itself uses the public channel preview.

## Verified results

`testDebugUnitTest assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
94 tests, zero failures/errors/skips. Added Compose tests render real text under Robolectric:
full post content is not truncated; touching the inline «Подробнее» link invokes the
publisher URL action; the browser action targets the article rather than the Telegram post.
Routing tests cover forced proxy, cache persistence/promotion, offline reopening and
failed proxy requests. Room validates upgrades from all prior supported versions, with
saved link and proxy metadata checked after database close/reopen.

Actual dev.by link through the configured Worker:

- `/article`: HTTP 200, 2,838 text characters, 12 paragraphs.
- `/fetch` for embedded-browser HTML: HTTP 200, 66,963 bytes.

The phone is disconnected. Version 0.4.7 has not been installed or verified on the phone.
Earlier four phone gesture tests and preservation of 665 articles belong to the 0.4.6
reader/refresh build and are documented separately; they are not claimed as 0.4.7 runs.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Exact counts, APK hash and deployment status:
validation/telegram-feed-047-results.json.
Build output: validation/build-047-output.txt.
