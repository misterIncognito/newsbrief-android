# NewsBrief optional capability verification

All **22 actual branch checkouts**, **4 real progressive merge stages** and **15 optional
composition profiles** pass their automated tests and debug builds, with zero failures,
errors or skips. The final full app passes **177 tests**; the minimal persistent feed
passes **30 tests**. The full app also builds its Android-test APK; device instrumentation
was not executed as part of this verification.

## Actual branch and merge builds

These checkouts include the final provider-skipping and navigation changes. Exact commits,
application-tree hashes, APK hashes and class-presence checks are retained in
[the branch manifest](validation/optional-capabilities/branches/results.json).

| Checkout/stage | Passing tests | Debug APK |
| --- | ---: | --- |
| `integration/feed` (mvp) | 30 | Passed |
| `feature/app-integration` (app-integration) | 177 | Passed |
| `dev` (dev) | 9 | Passed |
| `feature/networking` (networking) | 12 | Passed |
| `feature/page-proxy` (page-proxy) | 12 | Passed |
| `feature/news-proxy` (news-proxy) | 16 | Passed |
| `feature/storage` (storage) | 16 | Passed |
| `feature/source-providers` (source-providers) | 11 | Passed |
| `feature/rss-source` (rss-source) | 21 | Passed |
| `feature/telegram-source` (telegram-source) | 16 | Passed |
| `feature/telegram-proxy` (telegram-proxy) | 21 | Passed |
| `feature/source-management` (source-management) | 9 | Passed |
| `feature/source-discovery` (source-discovery) | 18 | Passed |
| `feature/feed-filters` (feed-filters) | 14 | Passed |
| `feature/native-reader` (native-reader) | 21 | Passed |
| `feature/embedded-browser` (embedded-browser) | 18 | Passed |
| `feature/synchronization` (synchronization) | 15 | Passed |
| `feature/feed` (feed) | 11 | Passed |
| `feature/save-articles` (save-articles) | 11 | Passed |
| `feature/share-articles` (share-articles) | 11 | Passed |
| `feature/ai` (ai) | 11 | Passed |
| `feature/article-plugins` (article-plugins) | 24 | Passed |
| `verify/optional-feature-merges` (add-save) | 32 | Passed |
| `verify/optional-feature-merges` (add-share) | 34 | Passed |
| `verify/optional-feature-merges` (add-catalog) | 40 | Passed |
| `verify/optional-feature-merges` (add-proxy-plugins) | 54 | Passed |

The four merge stages start from the MVP, then add Save, Share, source management/discovery,
and the two proxy plugins using normal Git merges. The resulting branch is preserved as
`verify/optional-feature-merges`; `integration/feed` remains the smaller MVP.

## Optional composition builds

Profiles were selected from complete snapshot `02889d1` before the final provider-skipping
and single-page navigation polish. The actual branch builds above verify the final source.
Prerequisites are added automatically. Every APK was inspected for selected implementation
classes and absence of excluded implementations, including both proxy clients, catalog,
Save/Share, native reader and embedded browser.

| Profile | Requested features | Passing tests |
| --- | --- | ---: |
| full | All available | 177 |
| mvp | feed,rss-source,storage | 30 |
| mvp-session | feed,rss-source | 23 |
| feed | feed | 11 |
| shell |  | 9 |
| mvp-save-articles | feed,rss-source,storage,save-articles | 32 |
| mvp-share-articles | feed,rss-source,storage,share-articles | 32 |
| mvp-source-management | feed,rss-source,storage,source-management | 30 |
| mvp-source-discovery | feed,rss-source,storage,source-discovery | 36 |
| mvp-feed-filters | feed,rss-source,storage,feed-filters | 35 |
| mvp-news-proxy | feed,rss-source,storage,news-proxy | 37 |
| mvp-telegram-source | feed,rss-source,storage,telegram-source | 32 |
| mvp-telegram-proxy | feed,rss-source,storage,telegram-proxy | 37 |
| mvp-native-reader | feed,rss-source,storage,native-reader | 39 |
| mvp-embedded-browser | feed,rss-source,storage,embedded-browser | 36 |

## Behavior covered

The Feed-only Compose test imports a fixture through the real view model/repository and
memory store, verifies optional controls/screens are absent, and taps the item to verify
Android ACTION_VIEW receives the original HTTPS URL. RSS/Atom integration tests cover
import, full content and deduplication without Room, reader, synchronization or AI.
The final provider test retains cached Telegram-service content while skipping its absent
provider during an RSS refresh. Save and Share each have independent service/intent tests.
Routing tests cover missing required proxies, terminal provider failures, protected-host
redirects and image policy. Existing migration, offline-content, extraction, Telegram,
cooldown, filtering and AI tests remain in their owning suites.

## Artifacts and data preservation

Use the verified APK copies; the ordinary debug output is overwritten by later builds.

- MVP: `app/build/outputs/apk/verified/newsbrief-capability-mvp-debug.apk`
- Full app: `app/build/outputs/apk/verified/newsbrief-capability-app-integration-debug.apk`

mvp APK SHA-256: `6cb7924a932acf9db22511712ae7c005be3d5528a7cb011d1eb3eb3c9c578208`.

app-integration APK SHA-256: `a3e50b417288b99e53d07191702614d303916abb7d9d81240408772d21dc8595`.

Room remains schema 8; schemas and migrations are unchanged from `main`. Existing source
selections and stored articles/images/summaries/saved records are retained. Missing providers
are skipped rather than replaced with another provider. Without Storage the host uses
session memory and does not open/delete existing persistent files.

These checks use offline fixtures and do not assert current external-service availability.
The Telegram service URL remains configurable. NewsBrief proxy policies apply to in-app
requests; the external browser controls its own networking.

All final checks passed. No release/tag changes or push were made. Build logs and manifests
are in [validation/optional-capabilities](validation/optional-capabilities). Documentation-only
forward merges preserve the tested `app` trees. Previous validation and the broader feed
assembly remain in Git history and [the preceding results](docs/coarse-incremental-build-results.md).

The tested MVP was installed on the connected Motorola with an in-place update. All **372** existing private files matched their pre-install hashes before launch; the app launched successfully. A private backup remains outside Git. Device visual/tap checks, if performed, are recorded separately.

Database rows also matched their pre-install contents after launch, including 1273 articles and 21 sources. Phone was locked; no on-device feed or article tap assertion. Automated Compose external-navigation test passed.
