# NewsBrief 0.4.4 — online source directories

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

Historical 0.4.4 results. Telegram now uses TGden with paginated topic categories;
see [AI-DISCOVERY-FIX.md](AI-DISCOVERY-FIX.md) for the current 0.4.5 behavior.

The Sources → Source catalog button opens a native search page. RSS/sites,
Telegram, and the existing curated recommendations are separate choices.
Search accepts names, topics, handles and website addresses. Quick searches
cover technology, AI, programming, news and business. These searches query
external directories rather than filtering the small bundled list.

## Verified services

Live checks on 9 October 2026:

- **TGX Telegram directory:** reported **17,343 public channels**, alongside
  36,425 groups and 973 bots. NewsBrief requests channels only. A `technology`
  query reported 35 matches and returned the permitted 20. This is the size of
  the complete Telegram directory, not the number of technology channels.
- **Feedly RSS index:** a `technology` search requested up to 100 results;
  NewsBrief parsed 97 feed entries. The endpoint does not report its entire
  index size, so the app does not invent an RSS catalog count.
- A discovered Guardian technology feed was verified through the production
  collector and imported **32 articles** into Room.
- No login or API key was required by either directory in these checks.

Telegram uses the documented, read-only `search_directory` and `directory_stats`
JSON-RPC tools at `https://tgfind.net/api/mcp`. See [TGX documentation](https://tgfind.net/en/mcp).
One query returns at most 20 results; the app shows the matching total separately
and suggests refining the query. There is no invented pagination or claim that
all 17,343 channels are downloaded onto the phone.

RSS uses `https://cloud.feedly.com/v3/search/feeds` with correctly encoded query
parameters. Feedly documents this endpoint in its [Teams API introduction](https://feedly.com/new-features/posts/introducing-the-feedly-teams-api).
Availability of third-party services can change; directory errors are displayed
and never silently replaced by the tiny curated list. Search terms are sent to
the selected provider. Only subsequent source/article/image requests use the
existing source-routing policy.

## Import and offline behavior

Results show publisher/channel names, summaries, addresses, language and
recorded subscriber counts where supplied. Subscriber counts are directory
metadata, not real-time online users. The provider's name and known directory
size appear in the page. Sources already connected cannot be added again.

The Add action fetches real posts before saving the source. Empty, private,
deleted or unreachable feeds/channels are rejected with an explanation. Source
and initial article writes are transactional; a failed write leaves no empty
source. Readding a deleted source reconnects its retained articles without
replacing saved reader text. Imported posts appear immediately in the feed.

Directory topic metadata is stored with each source and used by the Technology,
Business and other feed filters. Existing built-in sources retain their previous
category mapping. Directory search does not enable thousands of feeds at once.

Room migration **3 → 4** adds source categories and a search-cache table, preserving
sources, enable/disable state, article bodies, image metadata and summaries.
Migration tests also cover upgrades from versions 1 and 2. Up to 50 recent query
results are saved locally; fresh results are reused for five minutes. If the
provider is unavailable, an older matching result is labelled as saved/offline.
Queries without cached results report the error. Article offline storage remains
unchanged; discovering completely new sources still requires a connection.

RSS URLs are validated and HTTP feed addresses from the directory are upgraded
to HTTPS before verification. Telegram results are restricted to valid public
channel handles. Existing Meduza/Zerkalo proxy requirements, reader behavior and
image routing remain enforced.

## Validation and APK

`bash scripts/build-on-mac.sh`: **60 tests passed**, zero failures/errors/skips;
`assembleDebug`: **BUILD SUCCESSFUL**.

Three additional live integration tests passed separately (both directories and
actual RSS discovery → collection → Room import). To repeat those opt-in checks,
use the build script's JDK/SDK environment and run:

```bash
./gradlew :app:testDebugUnitTest -PverifyDirectoryServices=true \
  --tests '*DirectoryServiceLiveTest' --console=plain
```

Regular unit tests exclude live service checks, so a directory outage cannot
break the offline test suite. Tests cover both JSON contracts, invalid results,
query encoding, duplicate handling, directory/cache metadata, offline fallback,
cancellation, cache retention, source verification, atomic import and migrations.

APK: `app/build/outputs/apk/debug/app-debug.apk` (version 0.4.4, versionCode 8).
APK size: 11,539,996 bytes. SHA-256:
`09bae60a5084bcecb8d91b4fc714852e06cb9005f330c83c1efcaaeef7b2ec80`.
Build output: validation/discovery-build.txt.
Live results: validation/directory-results.json
and test output.

Version **0.4.4 is installed on the Motorola Edge 70 Fusion**, upgrading 0.4.2
without uninstalling or clearing data. Launch succeeded with no startup crash.
On-phone checks displayed the native reader, 97 RSS technology results and the
Telegram directory's 17,343-channel count. A subsequent AI query displayed three
Telegram results. The migrated database passed its integrity check; all **661**
previous articles, saved bodies, and all 19 source settings were preserved.
Database version is now 4. Results: validation/phone-044-results.json.

This smoke test covers installation, migration, reader rendering and directory
search. Airplane-mode image checks and every publisher's full-article extraction
were not repeated during deployment.
