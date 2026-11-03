# NewsBrief incremental feature verification

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

The runnable application no longer requires the all-feature integration branch. All nine
compositions and all ten canonical checkouts pass their offline tests and debug builds.
Three actual forward-merge stages also pass: Feed → Sources → Storage. The resulting
`integration/feed` branch is a small working news app. The complete app passes **167 tests**,
with zero failures, errors or skips, and builds its Android-test APK.

## Actual branch builds

| Checkout/stage | Passing tests | Debug APK |
| --- | ---: | --- |
| dev | 15 | Passed |
| feature/networking | 18 | Passed |
| feature/storage | 22 | Passed |
| feature/sources-discovery | 38 | Passed |
| feature/reader-browser | 35 | Passed |
| feature/synchronization | 21 | Passed |
| feature/feed | 17 | Passed |
| feature/ai | 17 | Passed |
| feature/article-plugins | 69 | Passed |
| feature/app-integration | 167 | Passed |
| `integration/feed`: step1-feed | 17 | Passed |
| `integration/feed`: step2-rss-feed | 40 | Passed |
| `integration/feed`: step3-offline-feed | 47 | Passed |

## Optional build compositions

Each profile was built from the complete source tree with `-PnewsbriefFeatures=...`.
DEX inspection verified both the presence of selected implementation classes and the
absence of every excluded NewsBrief implementation (Room, workers, reader, AI and plugins).

| Composition | Requested features (prerequisites added automatically) | Passing tests |
| --- | --- | ---: |
| shell |  | 15 |
| feed | feed | 17 |
| rss-feed | feed,sources-discovery | 40 |
| offline-feed | feed,sources-discovery,storage | 47 |
| background-feed | feed,sources-discovery,storage,synchronization | 53 |
| reader-feed | feed,sources-discovery,storage,reader-browser | 64 |
| ai-feed | feed,sources-discovery,storage,ai | 49 |
| plugins-feed | feed,sources-discovery,storage,article-plugins | 78 |
| full | All available | 167 |

New behavioral checks exercise a Compose feed using only the Feed binding, its actual
view model/repository and memory store: load a fixture article, open native feed text, save,
and show saved articles while AI/browser UI is absent. Another test uses the real RSS/Atom
parser with the Sources binding and proves importing/deduplication/native text reading
without Room, synchronization, reader or AI. Memory tests cover rollback, unique monotonic
IDs and saved visibility when a source is disabled. Existing proxy, migration, extraction,
cooldown, navigation and offline-data tests remain in the appropriate feature suites.

## Artifacts and history

Minimal persistent feed APK:
`app/build/outputs/apk/verified/newsbrief-branch-step3-offline-feed-debug.apk`.

Complete app APK:
`app/build/outputs/apk/verified/newsbrief-branch-app-integration-debug.apk`.

The ordinary `app/build/outputs/apk/debug/app-debug.apk` is overwritten by subsequent
builds; use the verified copies above. Exact commits, application-tree hashes, test counts,
APK SHA-256 values, class-presence checks and build logs are committed under
[`validation/incremental-features`](validation/incremental-features). Later guide/tool-only
merges retain the tested application trees. Feature branches were advanced using normal
merges and descriptive commits; no history was rewritten.

Room remains schema 8; schemas and `NewsMigrations.kt` are byte-for-byte unchanged from
`main`. Without Storage, the fallback never opens/deletes existing database/settings/image
files; session data is not persisted. `release`, `main`, `v0.4.12` and archived cumulative
branch refs remain unchanged. No push, phone deployment, device test or live-network
verification was performed for this correction. Android-test APK compilation is not a
claim that device tests ran.

## Resolved build failures

The first refactor compile exposed ambiguous trailing lambdas in two service factories;
explicit typed lambdas fixed them. The first Feed-only build showed that Android/Kotlin
source filters did not exclude inactive Kotlin implementations. Staging an explicit selected
source tree fixed this; the retained initial failure log is
`initial-exclusion-failure.txt`.
All final profile, branch and incremental-stage builds passed after those fixes.

See [the feature guide](BRANCHES.md), [previous independent builds](docs/independent-build-results.md)
and [older deployment records](docs/stacked-build-results.md).
