# NewsBrief Gradle module verification

The full module assembly passes **180 tests**; the minimal persistent feed passes **33**.
All **22 actual branch checkouts**, **4 real incremental merge stages**, and **3 full-tree
composition checks** pass their unit tests, module-graph assertions and debug APK builds.
There are zero final test failures, errors or skips. The full app also compiles its Android-test
APK; this does not claim instrumentation was executed on a device.

## Enforced boundaries

There are 19 runtime feature libraries, one integration-test library, three shared core
libraries and the application project. Each feature's resolved main compiler project graph
contains only `:core:api`. No implementation library depends on another feature or the host
runtime. APIs include `AppFeature`, `AppUi`, `NewsStore`, `AppSettings`, `NewsSourcePlugin`,
`TelegramContent`, `HttpTransport`, `PageProxyPlugin`, `ArticleFetchPlugin`, `FeedLoader` and
prefetch ports. The static checker also verifies physical file ownership and forbids host/
foreign implementation references.

Plugin-discovery tests load service descriptors and connect a fixture implementation through
HTTP/parser interfaces without any production plugin. App assembly tests discover exactly
the selected feature IDs. Every verified APK is inspected for exact service descriptor
contents, selected/excluded concrete implementations and absence of fixture/test helpers.
The full-tree selected builds prove unselected projects are excluded even when their source
directories exist. Normal library source sets replace the former source-staging/filtering.

## Actual branches and merge stages

The manifest records exact commits, a hash of all compilation inputs under app/core/features
and shared build configuration, per-module test counts, resolved compiler projects, plugin
services and APK hashes. Documentation-only forward merges retain those compilation trees.

| Branch/stage | Passing tests | Debug APK |
| --- | ---: | --- |
| `integration/feed` (mvp) | 33 | Passed |
| `feature/app-integration` (app-integration) | 180 | Passed |
| `dev` (dev) | 12 | Passed |
| `feature/networking` (networking) | 15 | Passed |
| `feature/page-proxy` (page-proxy) | 15 | Passed |
| `feature/news-proxy` (news-proxy) | 19 | Passed |
| `feature/storage` (storage) | 19 | Passed |
| `feature/source-providers` (source-providers) | 14 | Passed |
| `feature/rss-source` (rss-source) | 24 | Passed |
| `feature/telegram-source` (telegram-source) | 19 | Passed |
| `feature/telegram-proxy` (telegram-proxy) | 24 | Passed |
| `feature/source-management` (source-management) | 12 | Passed |
| `feature/source-discovery` (source-discovery) | 21 | Passed |
| `feature/feed-filters` (feed-filters) | 17 | Passed |
| `feature/native-reader` (native-reader) | 24 | Passed |
| `feature/embedded-browser` (embedded-browser) | 21 | Passed |
| `feature/synchronization` (synchronization) | 18 | Passed |
| `feature/feed` (feed) | 14 | Passed |
| `feature/save-articles` (save-articles) | 14 | Passed |
| `feature/share-articles` (share-articles) | 14 | Passed |
| `feature/ai` (ai) | 14 | Passed |
| `feature/article-plugins` (article-plugins) | 27 | Passed |
| `verify/gradle-module-merges` (add-save) | 35 | Passed |
| `verify/gradle-module-merges` (add-share) | 37 | Passed |
| `verify/gradle-module-merges` (add-catalog) | 43 | Passed |
| `verify/gradle-module-merges` (add-proxy-plugins) | 57 | Passed |

The verification branch `verify/gradle-module-merges` starts with the MVP and adds Save,
Share, source management/discovery, and the news/Telegram proxy plugins through actual
forward merges. `integration/feed` remains the smaller MVP. Each module implementation is
stored on its matching feature branch, with default runtime prerequisites merged from their
own branches. Shared `dev` contains only the core and app shell.

## Full-tree selected compositions

| Profile | Requested features | Passing tests |
| --- | --- | ---: |
| full | All available | 180 |
| selected-mvp | feed,rss-source,storage | 33 |
| selected-share-browser | feed,rss-source,storage,share-articles,embedded-browser | 41 |

These include independent Share and Embedded Browser additions while Save, catalog,
native Reader, synchronization, AI and both proxy implementations remain excluded.
Existing RSS/Atom, Telegram, proxy, extraction, migration, offline data, cooldown and Compose
navigation/action checks continue to pass in their owning libraries or integration suite.

## APKs and compatibility

Use verified copies because the default debug output is overwritten by later builds.

- mvp: `app/build/outputs/apk/verified/newsbrief-modules-mvp-debug.apk`
  SHA-256: `80bf41a3d26ce3745d2f020c73634ded989e92b3371bce2b00333a0254260bc0`
- app-integration: `app/build/outputs/apk/verified/newsbrief-modules-app-integration-debug.apk`
  SHA-256: `b83a61becb66996ba0a3fb009e83702b18856141242829f9297a6107616b3376`

Room stays at schema 8. Schema JSON files and `NewsMigrations.kt` match `main` byte-for-byte;
only their owning directory changed to `features/storage`. Entity package/class names,
database filename, source/plugin IDs and persisted settings keys remain unchanged. Missing
providers are skipped during refresh while their cached articles remain. No destructive
migration, uninstall or data clear is part of this change.

This verification uses deterministic offline fixtures. It does not assert live Worker,
Telegram-service or catalog-provider availability. The Telegram service URL is still
configurable; in-app protected sources require their proxy plugin. External browsers own
their network requests after receiving the original article URL.

## Build issues resolved

Initial builds exposed script classpath configuration, dependencies previously supplied by
the single app module, parser/feed-loader implementation imports, cross-module smart casts,
obsolete test imports, Android-test BOM constraints and an internal gesture component used
by cross-module tests. These were corrected with shared root configuration, library-owned
dependencies, interface ports and explicit public/test APIs. The original failure logs are
retained in `validation/gradle-modules/initial-build-failures/`. All final builds above pass.

No push or changes to `main`, `release` or `v0.4.12` were made. Prior branch heads and previous
validation remain in Git history. The preceding source-selection approach is documented
in [the archived results](docs/source-selection-build-results.md), and the actual module
architecture is documented in [BRANCHES.md](BRANCHES.md).
