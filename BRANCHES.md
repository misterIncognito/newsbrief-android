# NewsBrief branch workflow

`dev` is now a minimal Compose app shell. Networking, Room storage, source ingestion
and discovery, reader/browser, synchronization, AI and article plugins are excluded.
The shell does not open or delete the existing database, image cache or credentials.
`main`, `release` and the annotated `v0.4.12` tag remain on the deployed release.

| Feature branch | Own implementation | Required feature branch / PR base |
| --- | --- | --- |
| `feature/networking` | HTTP transport, Worker routing and URL validation | `dev` |
| `feature/storage` | Room entities/DAOs/migrations, preferences/credentials, disk image cache | `feature/networking` |
| `feature/sources-discovery` | RSS/Atom/Telegram ingestion, source plugins, catalogs, search, filters and shared content contracts | `feature/storage` |
| `feature/reader-browser` | Native article extraction, AMP, embedded browser, image and article presentation components | `feature/sources-discovery` |
| `feature/synchronization` | Refresh/cooldown, prefetch, workers, repository, app wiring, feed/source UI and view model | `feature/reader-browser` |
| `feature/ai` | Existing digest client, screen and provider settings | Restore the five core areas first |
| `feature/article-plugins` | Existing article interface, registry and providers | Restore the five core areas first |

The five core branches are stacked because the implementations depend on one another.
Each contains an explicit restoration commit for its own removal, plus normal merges
of its prerequisites. Each branch builds and tests with those prerequisites included;
only synchronization restores the complete app UI and activates background work.
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

See [recorded results](validation/article-plugin-branches-results.json),
[dev build output](validation/build-dev-without-article-plugins-output.txt), and
[feature build output](validation/build-feature-article-plugins-output.txt).

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

See [AI verification](validation/ai-branches-results.json),
[dev build output](validation/build-dev-without-ai-output.txt), and
[AI feature build output](validation/build-feature-ai-output.txt).
