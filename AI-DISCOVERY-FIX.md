# NewsBrief 0.4.5 — usable Telegram AI discovery

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

The Telegram AI shortcut in 0.4.4 returned three records because it searched one
English phrase in a small directory. Those records were crypto/game promotions;
the directory's overall channel count did not establish useful AI coverage.

Telegram discovery now uses **TGden**. The AI shortcut opens the provider's whole
AI category instead of issuing that narrow phrase search. AI, ИИ, «нейросети» and
«искусственный интеллект» map to the same browsable category. Technology, news,
business and programming shortcuts also browse corresponding categories. A
specific query such as ChatGPT or a publisher name uses real keyword search.

## Live checks, 9 October 2026

- AI category: **9,377 tagged records**. Two distinct pages returned 50 valid
  public channels; **32** remained after hiding crypto/gaming tags.
- Technology category: **18,503 tagged records**.
- ChatGPT keyword search: **333 matches**, 23 valid public channels on the first
  search response. A deliberately nonexistent name returned zero.
- A discovered **Hi, AI** news channel returned **13 readable Telegram posts**
  through NewsBrief's production collector.
- RSS search and a real Guardian RSS → Room import also passed.

Counts are reported by the provider and include listings that may be private,
missing public usernames or misclassified. They are not guarantees that every
listed record is a working news feed. Source verification remains mandatory
before adding it. The default AI filter hides records tagged crypto or gaming;
users can turn it off. It does not silently discard other small channels.

## Pagination and honest totals

«Загрузить ещё» remains visible below the list and loads the next category page.
Pages are merged without duplicate channel addresses. The category total is
labelled separately from the number currently shown, which changes with the
crypto/gaming filter. Pagination advances by raw records, including unusable
ones, so filtering does not skip offsets or repeat the first page. No thousands
of sources are enabled automatically.

Category requests use the live-verified REST contract:
`GET https://tgden.com/api/catalog?category=ai&limit=25&offset=25`.
The `offset` parameter returned distinct pages in checks. A `page=2` parameter
repeated page 1, so it is not used.

Keyword queries use the documented read-only `search_telegram` JSON-RPC tool at
`https://tgden.com/api/mcp`, with type `channel`, limit 25. The README's REST `q`
example returned unrelated top channels in live checks, so it is not used for
keyword search. The keyword tool's current first-page limit is shown honestly;
users refine a specific search to find additional matches. Documentation:
[TGden public API](https://github.com/JustJuice55/tgden-api).

The new Telegram cache namespace invalidates the previous three-result query
without deleting saved sources or articles. Each category page is cached
separately; offline retries use previously saved matching pages. Cache JSON also
retains topic tags and page offsets. Room stays at schema version 4; no additional
database migration or data reset is needed.

## Validation

**71 offline tests passed**, zero failures/errors/skips. **Six live integration
tests passed**. `assembleDebug` succeeded. Tests cover the actual JSON contracts,
private/missing usernames, demo-response rejection, category aliases, raw offsets,
last pages, crypto/gaming filtering, duplicate-page merging, cache invalidation,
page-specific caching, page-load failures and cancellation on topic changes.
Previously covered proxy routing, reader parsing, feed parsing, source import,
offline retention and Room migrations still pass.

Version 0.4.5 (versionCode 9) installed successfully on the Motorola Edge 70 Fusion.
The update preserved all **661 saved articles**, their saved text and source
settings. On-phone AI discovery displayed **16** channels with the default filter;
pressing «Загрузить ещё» increased that to **32**, while the category total remained
9,377. The installed APK's SHA-256 matches the tested build. On-phone checks are recorded
in validation/phone-045-results.json.
Build details: validation/ai-directory-build-results.json.
Live output: validation/ai-directory-live-tests.xml.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
