# NewsBrief incremental features

Start from `dev` and merge the features you need. Each feature registers its screens,
settings and services automatically; startup, navigation and shared adapters belong to
the foundation. **`feature/app-integration` is optional.** It is the convenient complete
assembly and contains tests that exercise all features together.

`integration/feed` is a verified small working application: Feed + Sources + Networking
+ Storage. It loads RSS/Atom/Telegram, offers source discovery and filters, displays
feed-provided article text in native Compose, saves/shares articles and persists data.
It contains no background workers, full-page reader/browser, AI client or article extraction
plugins. Live source availability still depends on the publisher and configured services.

## Add features progressively

```sh
git switch -c my-app dev
git merge feature/feed
# Working feed UI, native feed-text reading and save/share; needs a data provider.
git merge feature/sources-discovery
# Adds sources, HTTP transport and manual loading. No synchronization feature required.
git merge feature/storage
# Adds Room and persistent settings. Existing databases use the original migrations.
```

Build or run after any step. Feed alone starts empty unless a host supplies data; no
sample news is inserted. Without Storage, articles/settings/saves last for the current
process and existing offline files are left untouched. With Storage, cached articles can
be read offline even without Sources or the full Reader. Installing a different composition
restarts the process: session-only state does not migrate automatically into Room.

Optional next additions:

```sh
git merge feature/reader-browser    # Full article extraction, AMP, images and browser
git merge feature/synchronization   # Background refresh, persisted cooldown and prefetch
git merge feature/ai                # Digest screen, AI settings and generation
git merge feature/article-plugins   # Pluggable article extraction providers
```

Prefetch needs a reader to fetch full pages, so it remains inactive when Reader is absent.
Synchronization can be developed alone using shared ports; real news requires a source
provider or cached data. AI can be developed alone, but summaries need articles. These
are data requirements, not a requirement to merge every feature.

## Ownership and real prerequisites

| Branch | Adds | Required feature branches |
| --- | --- | --- |
| `feature/feed` | News list, filters, gestures, visibility refresh | None |
| `feature/networking` | HTTP transport, proxy/redirect enforcement, Worker settings | None |
| `feature/sources-discovery` | RSS/Atom/Telegram providers, source/catalog UI and settings | Networking |
| `feature/storage` | Room, migrations, persistent encrypted settings | None |
| `feature/reader-browser` | Full article/AMP reader, images, internal browser, rich cards | Networking |
| `feature/synchronization` | Cooldown coordinator, WorkManager refresh and optional prefetch | None |
| `feature/ai` | AI client, digest selection/screen/settings | None |
| `feature/article-plugins` | Telegram/AMP/Worker/HTML extraction registry/providers | Reader/browser and Sources |
| `feature/app-integration` | Complete convenience assembly and full-app tests | None for runtime wiring; full-app tests need all features |

Prerequisites are already merged into the corresponding branch. For example, merging
Sources also brings Networking, but does not bring Storage, Reader, Synchronization or AI.
These are Git feature branches within the existing `:app` Gradle module.

The foundation owns records/ports, a working app host/view model/repository, memory-backed
fallbacks, manual feed loading and basic article presentation. Feature bindings implement
`AppFeature`; generated typed registration selects the available bindings. Source files and
Gradle dependencies for excluded features are omitted from compilation. Each feature
contributes its own UI/settings and only replaces the services it provides. The foundation
has no dependency on concrete Room, HTTP, WorkManager, AI or reader implementations.
Room annotations remain shared via `room-common`; schema 8 and migrations are unchanged.

`tools/features.json` declares ownership, registrations and prerequisites. Run
`python3 tools/check-feature-boundaries.py` to check a checkout. Build a smaller composition
from a branch containing more features with, for example:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  -PnewsbriefFeatures=feed,sources-discovery
```

Required features are included automatically. An empty `-PnewsbriefFeatures=` builds the
foundation only. The default includes every runtime feature present on that branch. Full-app
integration tests run only when all their required features are selected.

## Keep development mergeable

Bring shared changes into a feature with a normal forward merge:

```sh
git switch feature/feed
git merge dev
```

Merge prerequisite updates into their dependants, then merge updated features into your
application branch. Shared contract edits can still need ordinary conflict resolution.
Do not merge `feature/app-integration` unless you want the complete app. Feature branches
retain their Git history; the former cumulative branches remain under `archive/stacked/`.
The previous separation is documented in [the earlier guide](docs/independent-feature-history.md).

`release`, `main`, `v0.4.12` and the phone installation were not changed by this correction.
See [BUILD-RESULTS.md](BUILD-RESULTS.md) for tested combinations, actual incremental merges,
APK locations and limits of verification.
