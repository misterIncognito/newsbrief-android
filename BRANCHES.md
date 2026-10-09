# NewsBrief branch workflow

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
