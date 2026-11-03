> Historical generated validation artifacts were removed; rerun the checks to produce current results.

Historical snapshot before the independent feature split. Branch names below now live under `archive/stacked/`.

# NewsBrief branch workflow

`dev` is now a minimal Compose app shell. Networking, Room storage, source ingestion
and discovery, reader/browser, synchronization, AI and article plugins are excluded.
The shell does not open or delete the existing database, image cache or credentials.
`release` is the minimal-shell snapshot promoted from `dev`.
`main` and the annotated `v0.4.12` tag retain the deployed full application.
Future dev changes reach release only through another explicit promotion.

| Feature branch | Own implementation | Required feature branch / PR base |
| --- | --- | --- |
| `feature/networking` | HTTP transport, Worker routing and URL validation | `dev` |
| `feature/storage` | Room entities/DAOs/migrations, preferences/credentials, disk image cache | `feature/networking` |
| `feature/sources-discovery` | RSS/Atom/Telegram ingestion, source plugins, catalogs, search, filters and shared content contracts | `feature/storage` |
| `feature/reader-browser` | Native article extraction, AMP, embedded browser, image and article presentation components | `feature/sources-discovery` |
| `feature/synchronization` | Refresh/cooldown, prefetch, workers, repository, app wiring, feed/source UI and view model | `feature/reader-browser` |
| `feature/ai` | Existing digest client, screen and provider settings | `feature/synchronization` |
| `feature/article-plugins` | Existing article interface, registry and providers | `feature/synchronization` |

The five core branches are stacked because the implementations depend on one another.
Each contains an explicit restoration commit for its own removal, plus normal merges
of its prerequisites. Each branch builds and tests with those prerequisites included;
the synchronization layer restores the complete app UI and activates background work.
The existing AI and article-plugin branches also include the core stack via normal
merges; their original restoration commits remain in history. Merging either one
into the shell brings its prerequisites too.
They are Git feature branches within the existing `:app` Gradle module, not five new
Gradle modules. Each owns a dependency script under `app/features/`; absent features
contribute no runtime libraries to the shell. Build configuration rejects missing
prerequisites rather than silently substituting a different implementation.

Restore progressively in the order shown above, or merge the complete stack:

```sh
git switch dev
git merge feature/synchronization
# Optional features, after the core app is restored:
git merge feature/ai
git merge feature/article-plugins
```

For a feature-only PR, target its prerequisite branch as listed in the table. Targeting
`dev` also includes any required lower layers. Once prerequisites land in `dev`, retarget
the next PR to `dev`. Preserve merge history when integrating these extraction branches;
squashing a prerequisite into dev can reintroduce removal/restoration conflicts.

Bring later shell or shared changes into a feature with an ordinary merge:

```sh
git switch feature/storage
git merge dev
```

For updates in a lower feature branch, merge that branch into its dependants as well.
Cherry-pick a self-contained fix only when its prerequisites already exist. Later edits
to the same files may require normal conflict resolution. No history rewriting is used.
All branches remain local until explicitly pushed; this extraction does not install
an APK on the phone.

## Core extraction verification

| Branch | Passing offline tests |
| --- | ---: |
| `dev` shell | 2 |
| `feature/networking` | 9 |
| `feature/storage` | 18 |
| `feature/sources-discovery` | 41 |
| `feature/reader-browser` | 66 |
| `feature/synchronization` | 151 |
| `feature/ai` with core prerequisites | 152 |
| `feature/article-plugins` with core prerequisites | 153 |
| Temporary combined core + AI + article-plugin merge | 154 |

All listed variants build a debug APK. The full core and optional/combined variants
also build Android test APKs. Device tests were not run for this extraction.
The shell tests cover its welcome screen and preservation of existing database,
image-cache files and encrypted-credential preferences on startup. APK inspection
confirms absent feature classes and absence of Room/OkHttp/WorkManager/Jsoup in the shell.
Room schema 8 and all stored migration definitions are restored unchanged on Storage.

Merge checks pass in both directions between dev and every feature. Recombining the
five core layers restores all application/test sources exactly as in the tested
pre-extraction baseline; only optional dependency registration changed in the main
build script. The two optional feature branches were synced by real merges; their
README conflicts were resolved without changing feature behavior or rewriting history.

Initial configuration/compilation issues were corrected and recorded. See
full results,
resolved failures, and
branch history.

## Historical extraction records

The records below describe the earlier AI/article-plugin splits before the five core
areas were removed. Their statements about what dev contained apply to those commits.

- `dev`: ongoing development. Source plugins remain enabled. The native article reader
  uses its built-in implementation; the article plugin API/registry/providers are excluded.
  AI digest generation and its controls are excluded; the start screen is the news feed.
- `feature/article-plugins`: the full article plugin system, ready to merge into `dev`.
- `feature/ai`: AI digest generation, its screen and provider settings, ready to merge into `dev`.
- `release` and `main`: the verified 0.4.12 baseline, unchanged by this extraction.
- `v0.4.12`: the immutable local annotated release tag.

The feature branch is based on the commit that removes the article plugin layer from
`dev`. A subsequent commit on the feature branch restores that layer by reverting the
removal. This gives Git a real, forward feature change to merge; simply branching from
the older plugin-enabled commit would leave the removal on `dev` unreversed.

When article plugins are ready to return:

```sh
git switch dev
git merge feature/article-plugins
```

The merge restores the article plugin interface, registry, all five providers, app wiring,
plugin-specific tests and article-plugin documentation. It retains source plugins, Room
schema 8, feed cooldown, multi-select filters, the embedded browser and offline storage.
Built-in reader behavior is retained on `dev` to avoid disabling article reading while
this architectural feature is developed separately.

Later edits to the same reader/wiring files may need ordinary merge conflict resolution.
No history rewriting, force pushes or reverse changes on release branches are required.
Branches and tag are local until explicitly pushed.

## Article extraction verification (before AI separation)

- `dev` removal: `7f573ab`; 152 unit tests pass.
- `feature/article-plugins` restoration: `8bc1ce4`; 154 unit tests pass.
- Debug APK and Android test APK compile successfully on both branches.
- APK inspection confirms article plugin classes are absent from `dev` and present
  on the feature branch; source plugins remain available on both.
- Git's merge-tree check restores the complete feature tree without conflicts.
- Release branches and `v0.4.12` still point to `5defa09`. No phone deployment or push
  was performed for this branch extraction.

See recorded results,
dev build output, and
feature build output.

## AI feature branch

AI follows the same removal/restoration workflow. `dev` opens to the news feed and
excludes the digest screen, AI client, digest selection logic, provider configuration
and generation actions. Source catalogs (including AI news topics), proxy settings,
reader and offline storage stay available. Room schema 8 and historical digest rows
remain intact; existing encrypted credentials and preference keys are retained.

`feature/ai` restores the AI removal commit. It is independent of article plugins:
merging it back enables AI with the built-in reader, without restoring article plugins.

Bring ongoing development into either feature branch with a regular merge:

```sh
git switch feature/ai
git merge dev
```

Restore AI when ready:

```sh
git switch dev
git merge feature/ai
```

PRs use the same source and target branches after publishing them. Cherry-picking
individual fixes is also supported. Keep the removal/restoration history; do not
rewrite it away with squash merges when syncing feature branches. Edits to shared
UI/repository/settings files may require normal conflict resolution.

### AI extraction verification

- `dev` removal: `04d5783`; 151 unit tests pass, including a Compose regression test
  covering the feed start screen, absence of AI controls, retained digest rows and
  untouched provider/model/encrypted-key preferences.
- `feature/ai` restoration: `90618f5`; 152 unit tests pass. Its application code is
  identical to the AI-enabled pre-extraction baseline, with article plugins excluded.
- Both branches build debug and Android test APKs. No paid AI requests or phone
  deployment were performed.
- Merge checks cover syncing `dev` into `feature/ai` and restoring AI into `dev`.
- Release branches, release tag and the existing article-plugin branch are preserved.

See AI verification,
dev build output, and
AI feature build output.
