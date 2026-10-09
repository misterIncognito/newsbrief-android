# NewsBrief 0.4.8: background full-article prefetch

Full text included in RSS/Atom has always been retained on import. Previously, a linked
article whose feed supplied only a summary was downloaded on the first reader tap.
Now a separate durable WorkManager job starts after feed refresh or successful source
import. It downloads pending full articles before the user opens them, storing their
text, metadata and proxy requirement in Room and their lead image in the existing
`files/reader-images` cache. The visible reader uses the same image files.

Telegram posts remain complete native feed items. Prefetch follows the selected publisher
link, preserving the original post text. Linked publisher articles are cached separately
without duplicate feed cards, using forced Worker routing even for normally direct hosts.
Previously cached targets are promoted to proxy-required without replacing their bodies.
Normal RSS/Atom articles keep source routing, with strict proxy rules for protected hosts.
Worker failures never trigger a direct fallback.

Each job selects up to **12 pending articles**, taking turns between enabled sources,
from the newest 600 feed items within the last **48 hours**. At most two targets download
at once. Completed targets are skipped so later refreshes progress through remaining work.
Jobs require a connected network and battery above the system low-battery threshold.
Manual and periodic refresh use the same scheduling path. Unique appended jobs preserve
refresh requests made while prefetch is already running. Source-list refresh does not
wait for article downloads. This is bounded recent-article prefetch, not downloading an
entire publication archive. Gallery images beyond the cover still load when viewed.

A failed article/image is counted without blocking other sources or discarding existing
content. Feed text longer than the extracted website text is preserved. Feed author/image
metadata survives missing website metadata. Cached articles open without another request;
cached images can be read by a new cache instance while offline. Invalid image responses
are rejected before writing. Existing cache names and database schema 6 are retained.

## Validation

`testDebugUnitTest assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
104 tests, zero failures/errors/skips. Ten new tests cover:

- Atom summary import schedules background work without fetching the article inline;
  prefetch then makes its full text and cover available before the first tap.
- Longer Atom content and feed metadata survive shorter extraction.
- Telegram publisher links use the proxy, preserving post text and avoiding duplicate cards.
- Worker failure cannot escape to direct transport and does not block other sources.
- Existing direct cache gains the Telegram proxy requirement without another download.
- Bounded batches skip completed articles and share slots across sources.
- Disabled sources and older history are excluded.
- A prefetched image is read offline from another cache instance; HTML errors are not cached.

The Compose feed interaction, routing, parser and migration regression tests also pass.
Image validation uses Robolectric native graphics rather than its permissive legacy decoder.
The phone remains disconnected. This APK has not been installed, and actual Android
background scheduling on the phone is still pending verification. Earlier phone results
are documented separately and are not reported as runs of 0.4.8.

During this session `Entities.kt` repeatedly reverted to its older schema-4 content outside
our edits, causing intermediate compilation failures. The tested schema-6 file is committed
in Git and restored for delivery; the user has been asked to check another editor or sync tool.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Exact results/hash: [validation/prefetch-048-results.json](validation/prefetch-048-results.json).
Build log: [validation/build-048-output.txt](validation/build-048-output.txt).
