# NewsBrief plugins (0.4.11)

On `dev`, news ingestion uses an injectable Kotlin source-plugin registry. Native article
reading uses a built-in implementation. The article plugin system is isolated on
`feature/article-plugins`; see [BRANCHES.md](BRANCHES.md). Source plugins ship in the APK. The existing source IDs (`RSS`, `TELEGRAM`) are
unchanged; there is no conversion or replacement of the user's source list.

## Source plugins

`network/SourcePlugins.kt` defines `NewsSourcePlugin` and `SourcePluginRegistry`.
Each plugin supplies a stable ID, display name, input hint, address normalization,
collection and optional channel discovery and merge behavior. The Add Source dialog
uses these descriptors directly. Unsupported IDs fail before making a request.

Built-ins:

| Persisted ID | Source | Transport |
|---|---|---|
| `RSS` | RSS, Atom and RDF feeds | Existing protected-source routing |
| `TELEGRAM` | Public Telegram HTML previews | Existing public preview reader |
| `TELEGRAM_PROXY` | Stored posts from NewsBrief Telegram | Authenticated Telegram service API |

`WebCollectors` remains a compatibility facade for existing callers and parser tests;
it dispatches through the registry. Parsing itself lives in `FeedParsers`.

To add a source type, implement `NewsSourcePlugin`, register it alongside the built-ins
in `SourcePluginRegistry.builtIns` (or inject a registry at the app composition root),
and keep its ID stable. The UI and collector require no additional type branches.
Default merges preserve cached reader text; the Telegram proxy merge accepts edited
post bodies while keeping existing cover references, row identity and bookmarks.

## Article reading on dev

`ArticleReader` uses its built-in Telegram service/preview, AMP and publisher extraction
paths. There is no article plugin interface, request type, registry or provider class in
this branch. Offline selection, native rendering, prefetch, image caching and mandatory
proxy routing retain their existing behavior. Telegram service failures never fall back
to public Telegram previews or direct publishers.

The separate `feature/article-plugins` branch contains the complete article plugin system
and its extensibility/fallback-order tests. Merge that branch into `dev` to restore it;
no revert is required at merge time. Source-plugin IDs remain in Room to preserve source
identity and saved Telegram posts, independently of the article-fetch implementation.

## Configure the Telegram service

The implementation follows the sibling `newsbrief-telegram` project, including its
`src/worker.mjs`, `src/auth.mjs`, `src/posts.mjs` and README API contract. That service
is not deployed yet; no endpoint or credential is bundled in the app.

1. Deploy and configure the Telegram service separately; enable collection for its channels.
2. In Android **Настройки → Telegram-прокси**, enter its HTTPS base URL and your read-enabled access key.
3. Save the connection. In **Источники → Каналы Telegram-прокси**, search the service's collected channels and add one. You can also choose **Своя ссылка → Telegram через прокси** and enter a public username, t.me channel URL or canonical positive numeric ID.

Adding a channel subscribes the local NewsBrief feed to already-collected data. The
Android connector does not join/leave channels on the service's shared Telegram account
or change its administrator configuration. Existing online RSS/Telegram catalogs remain
available independently. The proxy channel browser lists only enabled cached channels.

The connector exchanges `POST /auth {accessKey}` for a bearer token, then uses:

- `GET /channels` to discover enabled channels.
- `GET /channel/{username-or-id}` to resolve the canonical channel ID.
- `GET /posts?channel={numeric-id}&top=100` for the latest 100 stored posts per refresh.
- `GET /posts/{channelId}:{messageId}` to explicitly refresh one post.
- `GET /health` is also supported by the client.

Keys use the existing Android Keystore AES-GCM encrypted preferences. Bearer tokens
are memory-only, renewed on expiry or once after HTTP 401. Changing the service URL
clears its stored key; changing either connection or key invalidates its cached token.
Authenticated requests use HTTPS, bounded bodies, deadlines and disabled redirects.
Permission/authentication failures preserve existing local articles and are shown as
source errors; there is no fallback to another Telegram transport.

Full post text, UTF-16 URL/TextUrl entities and publication dates are preserved.
Public posts retain their t.me URL; numeric/private posts use the service's post URL
and still render as complete Telegram posts in the feed. Outgoing publisher links
open in the native reader through the article Worker. The service currently returns
`mediaType`, not downloadable photos: no image URLs are invented. Existing cached
images remain intact. Local Save/Share and offline storage remain available; this
change does not switch them to the service's separate saved-news API.

## Storage and verification

Room schema 8 adds `articles.sourcePluginId` with an empty-string default and backfills
it from each existing source's `kind`. The additive 7→8 migration preserves articles,
bookmarks, disabled sources, images, summaries and catalog caches. Tests exercise
upgrade chains from versions 1–7 and reopen the resulting database.

Tests cover source-registry extension/duplicate IDs, RSS/Atom and Telegram preservation,
proxy authentication/rotation/401 retry/redirect denial/bounded responses, entity links,
numeric posts, edited-post merges, offline reading after source removal, publisher
proxy propagation and actual Compose plugin/channel/configuration screens. Existing
live reader checks exercise Meduza AMP, Zerkalo AMP and devby extraction with cover
images through `/image` and offline image-cache reopening. Telegram service requests
use deterministic contract fixtures until that service is deployed.

Build and results: `BUILD-RESULTS.md` and `validation/plugins-0411-results.json`.
