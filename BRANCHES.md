# NewsBrief optional capabilities

`dev` contains the application host, shared contracts and session-backed storage. The
baseline article action opens the original HTTPS link using Android's external browser.
There is no fallback native article renderer, catalog, saved-items screen or sharing action.
Features contribute their own screens, settings, article actions, controls and services.
`feature/app-integration` is an optional complete assembly and cross-feature test suite.

## Minimal app

The current `integration/feed` branch contains exactly:

- Feed list and refresh gestures.
- RSS/Atom provider and generic source registry.
- Networking transport.
- Room storage for the persistent feed and existing offline data.

Tapping a feed item opens the external browser. The MVP has no source-management/catalog
screens, filter controls, Save/Share buttons, native reader, embedded browser, synchronization,
AI or proxy implementations. There is no empty Settings tab. The feed is cached; reading
full pages is the external browser's responsibility. Previously stored article bodies,
images, summaries, saved records and settings remain available to their optional features.
Existing cached Telegram items may still appear as ordinary feed records even when no
Telegram provider is installed; those sources are skipped during refresh.

For a fresh database, the RSS plugin supplies BBC Russian and The Verge as a small initial
source list. This is not the searchable catalog. Existing source selections are preserved;
removed sources are not silently restored. Without Storage, the same app works in memory
until its process exits. Session-only state is not automatically imported when Storage is
added later; existing persistent data is opened using the unchanged migrations.

Build a minimal branch progressively:

```sh
git switch -c my-mvp dev
git merge feature/feed
git merge feature/rss-source
git merge feature/storage
```

RSS brings its Networking and Source Providers prerequisites. Feed alone displays data
supplied through the host's store/collector ports; it starts empty without a provider or
stored articles. You can build/run after each merge.

## Independent feature branches

| Feature branch | Owns | Required feature branches |
| --- | --- | --- |
| `feature/feed` | Feed rows, refresh gestures, default external article action | None |
| `feature/networking` | HTTP download, size/time limits and redirect enforcement | None |
| `feature/source-providers` | Generic provider registry and initial source seeding | None |
| `feature/rss-source` | RSS/Atom parsing and fetching, minimal RSS defaults | Networking, Source Providers |
| `feature/telegram-source` | Public Telegram preview provider/parser and reader-content adapter | Networking, Source Providers |
| `feature/source-management` | Add/remove/enable sources | None; adding requires a registered provider |
| `feature/source-discovery` | Curated/catalog search, Feedly/TGden discovery and cache | Networking; connecting a result requires its provider |
| `feature/page-proxy` | Provider-neutral proxy registry and terminal routing policy | None |
| `feature/news-proxy` | Cloudflare Worker page/image/extraction plugin and its settings | Page Proxy |
| `feature/telegram-proxy` | Telegram service provider/content plugin, auth/config and channel browser | Networking, Source Providers |
| `feature/save-articles` | Save action, saved-feed control and storage-port service | None |
| `feature/share-articles` | Share action and Android share intent | None |
| `feature/feed-filters` | Category/publisher selection and grouping | None |
| `feature/native-reader` | Native article/AMP parsing, rich cards, images and image cache | Networking |
| `feature/embedded-browser` | Internal WebView, original-page mode and request policy | Networking |
| `feature/synchronization` | Background workers, cooldown and optional prefetch | None |
| `feature/storage` | Room, migrations and encrypted persistent settings | None |
| `feature/ai` | AI client, digest selection/screen and settings | None |
| `feature/article-plugins` | Extensible article extraction registry, AMP/HTML and content-port adapters | Native Reader |
| `feature/app-integration` | Complete convenience assembly and cross-feature tests | No runtime registration requirement; its tests need all capabilities |

Save works through the store port, using session memory or Room when present. Share does
not require Save. Native Reader does not require Embedded Browser, a source catalog or
article plugins. With only Embedded Browser added, feed taps open the internal browser.
When Native Reader is also present, it handles feed taps and offers the browser action.
Without either, article taps use the external browser. Synchronization does not own feed UI
or source loading; manual RSS refresh works without it. Prefetch waits for a reader/image
store. AI needs article data to generate a summary, not every feature implementation.

## Proxy and content plugins

`PageProxyPlugin` accepts a provider-neutral resource/type request. The optional Page Proxy
registry chooses an installed provider. `CloudflarePageProxy` implements `/fetch`, `/article`
and `/image`; Worker URL encoding and extraction-provider registration live in News Proxy.
Another page service can implement the same contract without changing HTTP transport or
feed code. A selected provider's failure is terminal; it does not fall back to a direct
request or silently select another service. Protected Meduza/Zerkalo hosts and explicitly
proxy-required requests fail before networking when no matching plugin is installed.
Ordinary image requests may use an installed proxy or go direct when none is present;
images belonging to protected articles retain their proxy requirement.

The Telegram service API provides channels/posts, so it implements `NewsSourcePlugin` and
`TelegramContent` rather than pretending to fetch arbitrary web pages. Public Telegram is
a separate plugin. Missing/failing Telegram service support never falls back to public
Telegram or a publisher request. Both providers retain their original persisted IDs.
The service URL remains configurable; no deployment of that service is implied.

These plugins govern requests made by NewsBrief. The MVP hands the original article URL
to the external browser; that browser controls its own connections. Protected in-app
feeds/pages still need a suitable proxy plugin. Existing Worker/service configuration keys
and legacy Room records remain in shared contracts/storage for data compatibility; that
does not install or activate a proxy implementation.

## Add only what you want

```sh
git switch integration/feed
git merge feature/save-articles      # Save only; no Share
git merge feature/share-articles     # Independently add Share
git merge feature/source-management  # Manage configured sources
git merge feature/source-discovery   # Independently add catalog/search
git merge feature/news-proxy         # Generic Page Proxy plus the Worker provider
git merge feature/telegram-proxy     # Independently add Telegram service support
```

The minimal `integration/feed` branch is kept free of these optional additions. Actual
verification merges are preserved on `verify/optional-feature-merges`. Use your own
application branch for a different combination. Default builds enable features present
on the branch; from a complete checkout, a composition can also be selected explicitly:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  -PnewsbriefFeatures=feed,rss-source,storage
```

Prerequisites are selected automatically. An empty `-PnewsbriefFeatures=` builds just the
host. Excluded implementation sources and dependency scripts do not reach compilation;
typed feature registration includes only selected bindings. These remain Git capability
branches within the existing `:app` Gradle module, not separate Gradle modules.
`tools/features.json` records ownership/prerequisites. Run
`python3 tools/check-feature-boundaries.py` to check a checkout.

## Merge shared development forward

```sh
git switch feature/save-articles
git merge dev
```

Merge prerequisite updates into their dependants, then merge updated capabilities into
your application branch. Normal conflict resolution can still be needed for shared ports.
No force reset or history rewrite was used. Previous bundled branches, including the
previously installed feed assembly, are preserved under `archive/bundled/`; earlier stacks
remain under `archive/stacked/`. The old combined `sources-discovery` and `reader-browser`
branches are archived in favor of the narrower branches above. `main`, `release` and
`v0.4.12` remain unchanged.

See [BUILD-RESULTS.md](BUILD-RESULTS.md) for exact tested commits, combinations and APKs,
and [the preceding guide](docs/coarse-incremental-feature-history.md) for historical context.
