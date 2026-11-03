# AGP 9.4.1 upgrade — 10 October 2026

Applied Android Studio’s AGP Upgrade Assistant on `integration/feed`:
AGP 8.13.2 → 9.4.1 and Gradle 8.13 → 9.6.0. Regenerated the wrapper JAR and
launchers, pinned the distribution SHA-256 and retained the 60-second network timeout.
JDK 17.0.20.1, Kotlin 2.3.20, KSP 2.3.10, SDK 36, target SDK 35 and app version
0.4.12/code 16 remain unchanged. No application code, database schema or migration changed.

The assistant’s compatibility settings retain the existing Kotlin Android plugin,
legacy DSL and build defaults. AGP emits deprecation warnings for these settings;
this upgrade does not migrate to built-in Kotlin or the new DSL. Kotlin 2.3.20’s
published fully supported Gradle range ends at 9.3.0; the newer combination was
validated by the actual sync, builds and tests below, rather than inferred from that table.

## Actual validation

| Assembly | Tests | Failures / errors / skips | Build |
| --- | ---: | --- | --- |
| `integration/feed` | 33 | 0 / 0 / 0 | `verifyModuleInterfaces :app:testDebugUnitTest :app:assembleDebug` passed |
| Full assembly, temporary copy of `feature/app-integration` | 180 | 0 / 0 / 0 | Same checks plus `:app:assembleDebugAndroidTest` passed |

Android Studio’s Upgrade Assistant reported **Sync succeeded**. The regenerated
wrapper reports Gradle 9.6.0. Static module boundaries passed. Full verification
used two Gradle workers. Every feature’s resolved main compiler project graph still
contains only `:core:api`. Android test APK compilation passed; instrumentation
was not run on a device. No live source or proxy checks or phone deployment were performed.

Only `integration/feed` receives the committed upgrade. The full assembly was
validated in a temporary source copy; `dev`, other feature branches, release and
tags were not advanced. The change can be carried to them through normal Git merges
or cherry-picking this upgrade commit.

## Artifacts

- Feed APK: `app/build/outputs/apk/verified/newsbrief-agp-9.4.1-feed-debug.apk`
- Full APK: `app/build/outputs/apk/verified/newsbrief-agp-9.4.1-full-debug.apk`
- Exact test counts, hashes and compiler graphs: `validation/agp-9.4.1/*-results.json`
- Build logs and wrapper verification: `validation/agp-9.4.1/`

Sources: [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[AGP 9 migration behavior](https://developer.android.com/build/releases/agp-9-0-0-release-notes),
[Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
