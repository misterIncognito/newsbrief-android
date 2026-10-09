# NewsBrief independent feature verification

All ten checkouts build successfully with only their declared feature prerequisites.
The full app on `feature/app-integration` passes **160 offline unit tests**, with zero
failures, errors or skipped tests. Its debug APK and Android-test APK build successfully.
`dev` remains the app shell plus shared contracts; `release`, `main`, `v0.4.12` and the
phone installation were not changed by this split.

| Checkout | Unit tests passed | Debug build |
| --- | ---: | --- |
| `dev` | 10 | Passed |
| `feature/networking` | 13 | Passed |
| `feature/storage` | 17 | Passed |
| `feature/sources-discovery` | 32 | Passed |
| `feature/reader-browser` | 32 | Passed |
| `feature/synchronization` | 16 | Passed |
| `feature/feed` | 11 | Passed |
| `feature/ai` | 12 | Passed |
| `feature/article-plugins` | 65 | Passed |
| `feature/app-integration` | 160 | Passed |

Exact tested commits, tasks, APK hashes, logs and preserved refs are recorded in
[the verification manifest](validation/independent-feature-branches-results.json).
Later documentation/tool-check merges do not change the tested application sources.

Current full-app APK: `app/build/outputs/apk/debug/app-debug.apk`.
Preserved full-app copy: `app/build/outputs/apk/verified/newsbrief-independent-features-debug.apk`.
SHA-256: `983ad2925b2ced439e87e192fa0ba78bb15214c38199ed7835a398ace2ceb588`.

Room remains schema 8. Exported schemas and migration implementations are byte-for-byte
unchanged from `main`; migration tests pass on the independent storage branch. The full
suite also covers offline articles, saved items, source refresh, Telegram links, proxy
routing, AMP and UI navigation. Six port-level synchronization tests run without any
NewsBrief storage, source or reader implementation.

The initial isolated AI compile exposed a dependency on source-owned `FeedRules`. Moving
that pure helper into the foundation resolved it; the final matrix above passed afterward.
The [initial failure log](validation/independent-features/initial-ai-failure.txt) is retained.
Refactor compilation also required preserving Compose state imports and putting parameter
defaults on interfaces; all were corrected before final validation.

Live directory/AMP network checks were not requested and remain opt-in. The Android-test
APK was compiled; no device tests or phone deployment were performed for this refactor.
See [branch workflow](BRANCHES.md) for architecture and merge instructions and
[earlier build/deployment records](docs/stacked-build-results.md) for historical results.
