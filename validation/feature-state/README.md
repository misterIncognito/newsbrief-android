# Feature state separation validation — 3 November 2026

`results.json` records 46 successful configurations, including their test totals,
resolved main compiler graphs, plugin descriptors, actual controller classes in DEX,
and APK SHA-256 hashes. Build logs are included alongside the JSON.

The phases are explicit:

- `module-state-separation`, source snapshot `2b6448a`: empty shell, MVP and each of
  the 19 isolated runtime features; then 20 real progressive Git merges from `dev`.
  The progressive branch adds Feed, Networking, Source Providers, RSS, Save, Share,
  Filters, Storage, Management, Discovery, Page Proxy, News Proxy, Telegram Source,
  Telegram Proxy, Synchronization, Reader, Browser, Article Plugins, AI and the
  optional integration test module. Each stage passed tests and built an APK.
- `startup-safety`, source snapshot `83cbfbc`: the subsequent generic startup-order
  correction passed the complete 190-test assembly and Android-test APK compilation,
  plus the empty shell, isolated Synchronization, MVP and the actual working feed
  checkout. It changes only shared bootstrap code and its tests; feature implementation
  code and controller contracts are identical to the preceding phase.

Final checks: 190 full-app tests, 41 working-feed tests, 20 shell tests and 22 isolated
synchronization tests; zero failures, errors or skips. Optional live directory/AMP
checks were excluded by the normal build configuration. Android-test APKs were compiled,
not executed on a device. No phone installation or live upstream verification was done.

`branches.json` proves every canonical branch has exactly the final snapshot's shared
sources and its selected implementations. `dev` has no optional implementation files.
`promotions.json` records original heads and history-preserving updates; originals remain
under `archive/pre-feature-state/<branch>`. Release, main and the existing tag are preserved.
`storage-compatibility.json` verifies unchanged Room schema, migrations and entity models.

All APKs exclude legacy `NewsRepository`/`AppUi` and unit-test fixtures. Missing feature
ViewModels are absent from their DEX files, not merely disabled at runtime. Every feature
library's resolved main compiler project graph still contains only `:core:api`.

The initial failed runs are retained under `initial-failures/`. They exposed a leftover
card reference, missing saved-feed empty-message recomposition, an Android JSON test
harness requirement and an offscreen test click. Those issues were resolved. A later
startup-test-only WorkManager resource problem was resolved by initializing WorkManager
only when its feature is selected. Git's synchronization test rename/delete conflict was
resolved by keeping the test in its new App Integration owner.

Reproduce the selected checkout with:

```sh
./gradlew verifyModuleInterfaces :app:testDebugUnitTest :app:assembleDebug --max-workers=2
```

From a full source checkout, add `-PnewsbriefFeatures=feed,rss-source,storage` for the MVP,
`-PnewsbriefFeatures=synchronization` for isolated Sync, or `-PnewsbriefFeatures=` for
only the shell. Runtime prerequisites are included automatically.
