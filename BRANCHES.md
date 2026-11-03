# NewsBrief Gradle modules and plugin branches

NewsBrief now has real Android library modules. `:app` contains Android startup only.
`dev` contains the shell and shared core, with **no optional feature implementation**.
Each capability lives under `features/<name>/` on `feature/<name>`. The minimal application
assembly is `integration/feed`; the complete convenience assembly is `feature/app-integration`.
Runtime prerequisites are merged from their own branches, not copied into another module.

## Shared contracts and host

| Module | Responsibility |
| --- | --- |
| `:core:api` | Data models; storage/settings/source/content/HTTP/article/proxy/UI ports; plugin registration contract; shared formatting and URL policy |
| `:core:runtime` | Shell messages/overlay navigation/controller lifecycle, session storage/settings fallback and plugin composition |
| `:core:testing` | Test configuration helpers; excluded from the production APK |
| `:app` | Application/Activity, manifest/resources and chosen library dependencies |

Every feature library's **main compiler classpath includes only `:core:api` as a project
dependency**. No feature imports another feature implementation, the host ViewModel,
repository, application class or Room implementation. External libraries such as OkHttp,
Jsoup and Room belong to the implementing module. Integration tests deliberately depend
on multiple modules; those test dependencies are not production connections.

`AppFeature` registers screens, settings, toolbar actions, navigation handlers, services and
controller factories through `FeatureBindings`. Screens receive a generic `FeatureHost`
and resolve only their own UI contract. There is no combined `AppUi` interface.

Each feature owns its ViewModel, mutable state, coroutine jobs and UI actions:

| Feature | ViewModel / narrow contract |
| --- | --- |
| Feed | `FeedViewModel` / `FeedUi` |
| Feed filters | `FeedFiltersViewModel` / `FeedFilterUi` |
| Saved articles | `SavedArticlesViewModel` / `SavedUi` |
| AI | `AiViewModel` / `AiUi` |
| Source management | `SourcesViewModel` / `SourcesUi` |
| Source discovery | `DirectoryViewModel` / `DirectoryUi` |
| Telegram proxy | `TelegramProxyViewModel` / `TelegramProxyUi` |
| Worker settings | `WorkerSettingsViewModel` / `WorkerSettingsUi` |
| Native reader | `ReaderViewModel` / `ReaderUi` |
| Embedded browser | `BrowserViewModel` / `BrowserUi` |

The shell's retained `NewsViewModel` name refers only to messages, a generic overlay
stack and the lifecycle of the controller store. It contains no reader, feed, filtering,
saving, AI, catalog, browser or proxy state and does not reference their controller contracts.
Controllers are created lazily, retained once per shell and cleared with the shell.
Plugin startup runs once, after the Application publishes its fully constructed runtime.
Uninstalled features register no factories, state or jobs. Duplicate contract registrations
are rejected. Features resolve optional peers through contracts, never implementations.

`FeatureServices` composes registered data-service factories over the store/settings ports.
Manual ingestion and source connection belong to Source Providers. Article loading,
persistence and navigation history belong to Native Reader. Saving and digest generation
belong to their own modules. The old production `NewsRepository` is removed. Test fixtures
in App Integration delegate to the actual feature services; they are excluded from APKs.

Storage implements `NewsStore` and `AppSettings`; providers implement `NewsSourcePlugin`;
Telegram adapters implement `TelegramContent`; Networking implements `HttpTransport`;
page proxies implement `PageProxyPlugin`; extraction uses `ArticleFetchPlugin` and parser
ports. Synchronization receives `FeedLoader` and `PrefetchArticleStore`. Missing services
leave optional controls absent or return a clear unavailable result when explicitly invoked.

Each runtime library owns a Java service descriptor at
`src/main/resources/META-INF/services/io.newsbrief.app.runtime.AppFeature`. The descriptor
names its public no-argument plugin class. `InstalledFeatures` discovers those classes
through the API; `:app` does not import feature classes or maintain a hard-coded registry.
Descriptors are merged when packaging the APK, and consumer rules preserve constructors.
Excluded modules contribute no classes or descriptors to the APK.

## Feature modules and branches

Each row has a matching `feature/<name>` branch. Prerequisites below describe the default
runnable assembly, **not implementation imports or library compiler dependencies**.

| Name / Gradle module `:features:<name>` | Implementation | Default runtime prerequisites |
| --- | --- | --- |
| `feed` | Feed rows, refresh gestures, external article action | None |
| `networking` | HTTP transport and redirect/size/time policy | None |
| `source-providers` | Generic registry, source connection, manual ingestion and initial seeding | None |
| `rss-source` | RSS/Atom parser/provider; BBC Russian and The Verge initial defaults | Networking, Source Providers |
| `telegram-source` | Public preview parser/provider and content adapter | Networking, Source Providers |
| `telegram-proxy` | Configurable service provider/content, auth and channel/settings UI | Networking, Source Providers |
| `source-management` | Add/remove/enable source UI | None; adding needs a registered provider |
| `source-discovery` | Catalog, Feedly/TGden search and cache | Networking; connecting results needs the matching provider |
| `page-proxy` | Provider-neutral proxy selection and terminal routing | None |
| `news-proxy` | Cloudflare `/fetch`, `/article`, `/image` plugin and settings | Page Proxy |
| `storage` | Room schema/migrations, persistent settings and keys | None |
| `synchronization` | Workers, cooldown and optional article/image prefetch | None; prefetch waits for reader/images |
| `feed-filters` | Category/publisher controls and grouping | None |
| `native-reader` | Compose reader/cards, article/AMP parser, images and cache | Networking |
| `embedded-browser` | Internal WebView, original-page mode and policy | Networking |
| `save-articles` | Save action, saved-feed control and store-port service | None |
| `share-articles` | Independent Android share action | None |
| `ai` | AI client/selection/digest/settings UI | None; generating needs article data |
| `article-plugins` | Extensible extraction registry and AMP/HTML/content adapters | Native Reader |
| `app-integration` | Cross-module unit/device test sources; no runtime plugin | Its tests need all runtime modules |

The Telegram service returns channels/posts, so its plugin implements the source/content
interfaces rather than the arbitrary-page proxy interface. Public Telegram support remains
independent. Neither missing nor failed service support silently falls back to public
Telegram. Protected Meduza/Zerkalo in-app requests require a matching page proxy, and a
selected proxy failure never falls back to a direct request. External browsers control
their own networking after NewsBrief passes them the original URL.

## Build and merge progressively

```sh
git switch -c my-app dev
git merge feature/feed
git merge feature/rss-source
git merge feature/storage
./gradlew verifyModuleInterfaces :app:testDebugUnitTest :app:assembleDebug
```

Build/run after every merge. Feed alone starts empty unless data is provided. RSS brings
its default provider registry and transport branches. Storage adds persistence; without it
the host uses session memory and does not open or delete existing database/settings files.
Session-only changes are not automatically imported when Storage is added.

`integration/feed` contains exactly Feed, RSS Source, Source Providers, Networking and
Storage. Article taps open the external browser. It has no catalog/source-management UI,
filters, Save/Share, reader, embedded browser, workers, AI, or proxy implementations. Existing
source selections and offline data remain; cached articles from absent providers can still
appear, but those providers are skipped during refresh. Deleted sources are not reseeded.

Add only the desired capability to your own application branch:

```sh
git merge feature/save-articles
git merge feature/share-articles
git merge feature/source-management
git merge feature/source-discovery
git merge feature/news-proxy
git merge feature/telegram-proxy
```

Save does not require Share or Room; it uses the store interface. Reader and Embedded Browser
are independent: with neither installed, taps open externally; with only Browser they open
internally; with Reader it handles taps and offers the browser action. Manual RSS refresh
does not require Synchronization. `feature/app-integration` is optional and is never a host
startup requirement. Actual progressive verification merges are preserved on
`verify/gradle-module-merges`; the MVP assembly remains free of those extra modules.

From a complete checkout, select a smaller composition:

```sh
./gradlew verifyModuleInterfaces :app:testDebugUnitTest :app:assembleDebug \
  -PnewsbriefFeatures=feed,rss-source,storage
```

An empty `-PnewsbriefFeatures=` selects only the shell/core. Settings includes only selected
feature projects and their default prerequisites; there is no copying or filtering Kotlin
sources. Each module compiles its own standard `src/main` and `src/test` trees.
`:app:testDebugUnitTest` runs the included libraries' tests as well as application tests.
Individual suites can be run with, for example, `:features:save-articles:testDebugUnitTest`.
Full device tests remain owned under `features/app-integration/device-tests` and are compiled
into `:app`'s Android-test APK only for the complete assembly.

## Develop a plugin

Create `feature/<new-name>` from `dev`, add an Android library under `features/<new-name>`,
and declare ownership, binding and optional default prerequisites in `tools/features.json`.
Its main project dependency is `implementation(project(":core:api"))`. Implement the relevant
port and a public no-argument `AppFeature` that registers it, and add its service descriptor.
The application host requires no implementation import or registration edit. Put module
unit tests alongside the implementation; shared test helpers come from `:core:testing`.

```kotlin
class CustomSourceFeature : AppFeature {
    override val id = "custom-source"
    override fun configure(bindings: FeatureBindings) {
        bindings.sourcePlugins += { settings -> CustomNewsSource(settings) }
    }
}
```

Shared API/host changes flow forward normally:

```sh
git switch feature/save-articles
git merge dev
```

Merge prerequisite changes into their dependants and updated features into your application
branch. Git conflicts can still require ordinary resolution when contracts change; no
force reset or rewritten history is needed. The pre-module branch heads are preserved under
`archive/pre-gradle-modules/`, and earlier archives remain. `main`, `release`, `v0.4.12` and
the previously installed APK are unchanged by this refactor.

`python3 tools/check-feature-boundaries.py` verifies physical ownership and forbidden imports.
`./gradlew verifyModuleInterfaces` checks the resolved compiler graph, not just declarations.
Tests verify service discovery and interface connections; APK inspection verifies exact
plugin descriptors and selected/excluded implementations. See [BUILD-RESULTS.md](BUILD-RESULTS.md).

## Verified feature-state workflow

The state split is recorded on `refactor/feature-state`. All canonical `feature/<name>`
branches and `integration/feed` have the same final shared contracts and host. Their
previous heads are preserved under `archive/pre-feature-state/`. The actual one-at-a-time
merge sequence is retained on `verify/feature-state-merges`; each of its 20 addition stages
passed tests and built a debug APK. See [current results](BUILD-RESULTS.md).

For a stateful plugin, implement a narrow API contract in its own module-owned ViewModel,
register its factory in `configure`, and resolve it only at that feature's screen boundary:

```kotlin
bindings.controller(MyFeatureUi::class.java, ::MyFeatureViewModel)
// Inside this feature's Screen(host):
MyFeaturePage(host.controller(MyFeatureUi::class.java))
```

The ViewModel owns its mutable flows and coroutine jobs. Do not add those fields or
feature-specific controller access to the shell. Use `host.optional(OtherFeatureUi::class.java)`
for an optional peer, and keep a sensible behavior when that peer is absent. Generic article
navigation handlers and article actions let readers, browsers, Save and Share extend the
basic feed without making their implementations dependencies of it.
