# NewsBrief branch workflow

- `dev`: ongoing development. Source plugins remain enabled. The native article reader
  uses its built-in implementation; the article plugin API/registry/providers are excluded.
- `feature/article-plugins`: the full article plugin system, ready to merge into `dev`.
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
