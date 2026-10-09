# NewsBrief feature branches

`dev` contains a minimal app shell and shared contracts. `release` stays on the deployed
shell at `aa5914a`; `main` and `v0.4.12` stay on the original complete application.
No branch in this split has been pushed or installed on the phone.

The old cumulative branches are preserved as `archive/stacked/<feature>`. Their commits
and history remain reachable. Use the new `feature/*` branches for further development;
do not merge the archived stack back into these branches. The full refactoring reference
is preserved as `refactor/feature-boundaries`.

| Branch | Owns | Feature prerequisites |
| --- | --- | --- |
| `feature/networking` | HTTP transport and redirect/proxy enforcement | None |
| `feature/storage` | Room database/DAOs/migrations and encrypted settings | None |
| `feature/sources-discovery` | RSS/Atom/Telegram ingestion, plugins, discovery/cache, source and catalog screens | Networking |
| `feature/reader-browser` | Native reader, AMP, embedded browser, image cache, article/card/save/share components | Networking |
| `feature/synchronization` | Refresh/cooldown coordinator, prefetch and background workers | None |
| `feature/feed` | News list, multi-select controls, refresh gestures and visibility callbacks | None |
| `feature/ai` | Digest selection/client and digest screen | None |
| `feature/article-plugins` | Extraction registry and Telegram/AMP/Worker/HTML providers | Reader/browser and sources/discovery |
| `feature/app-integration` | Startup, navigation, view model, repository adapters and full-app tests | All features |

Each feature branch starts from the same `dev` foundation and merges only the explicit
prerequisites above. There is no networking → storage → sources → reader → sync chain.
Storage, synchronization, feed and AI build without any other feature implementation.
Sources and the reader can be worked on independently of each other and of Room.
These remain Git feature branches inside the existing `:app` Gradle module.

## Dependency rules

- Shared records, DAO/settings ports, URL/proxy policy, reader results and small content
  utilities live in the foundation. It has no HTTP client, Room database/runtime,
  background scheduling or active feature UI. Existing Room record annotations remain
  shared deliberately to retain the exact schema; the foundation uses `room-common` only.
- Features depend on ports, never `NewsRepository`, `NewsViewModel`, `NewsBriefApp` or
  another feature's concrete `SettingsStore`/`NewsDatabase`.
- Synchronization accepts `FeedStore`, `NewsCollector`, `SyncSettings`, article and image
  ports. Its workers call `BackgroundTasksOwner`; only app integration supplies the host.
  Source seeding, screens, navigation and startup do not belong to synchronization.
- Reader extraction accepts an optional `ArticleExtractor` and `TelegramContent` port.
  Sources supplies the Telegram adapter; app integration injects it. The reader has built-in
  web/AMP extraction and needs no catalog, Telegram service client or database implementation.
- Feature screens accept their own state/callback interfaces. Feed UI receives category
  choices from its host and an article-content slot instead of importing the reader UI.
- Runtime wiring and implementation choices belong to app integration. Its current
  composition enables all features, including AI and article plugins. Adding a provider
  changes its feature branch; adding it to the app changes integration wiring.
- `tools/features.json` records file ownership and prerequisites. Run
  `python3 tools/check-feature-boundaries.py` to check the current checkout. Dependency
  scripts also reject missing prerequisites during Gradle configuration.

## Working and merging

Use normal merges to bring shared changes into any feature:

```sh
git switch feature/synchronization
git merge dev
```

For a transport change needed by sources or the reader, merge `feature/networking` into
those branches. Feature-only PRs for sources/reader can target networking while its changes
are unmerged; other independent feature PRs can target dev. Article plugins can target an
integration base containing reader and sources. Review shared contracts separately from
feature implementations. Avoid cherry-picking prerequisite commits piecemeal or merging
unrelated features into each other.

To verify the full application without restoring features to dev:

```sh
git switch feature/app-integration
git merge dev
git merge feature/networking feature/storage feature/sources-discovery feature/reader-browser \
  feature/synchronization feature/feed feature/ai feature/article-plugins
```

To deliberately restore the full app to dev later, merge `feature/app-integration` into dev.
Use normal merge commits while these extraction branches are in flight so Git retains the
shared feature history. Later edits to the same contracts or integration files may still
need ordinary conflict resolution; separate branches cannot eliminate that.

## Validation

The full refactoring reference passes 160 unit tests and builds the debug and Android-test
APKs. Each new feature branch is also checked with only its declared prerequisites before
this split is finalized. Results and exact commits are recorded in `BUILD-RESULTS.md` and
`validation/independent-feature-branches-results.json`.

Earlier extraction records are in [the archived branch workflow](docs/stacked-branch-history.md).
