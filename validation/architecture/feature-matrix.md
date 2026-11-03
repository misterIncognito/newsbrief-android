# Feature selection validation

Tested source commit: `193a2709729516dc7287dd4704a3e3b0b27ab15b`.

The full 19-feature assembly and app-integration unit tests passed, and a debug APK was built. Each feature below also passed the app unit tests, its selected modules’ unit tests, and a debug build with only itself and its declared transitive prerequisites selected. This is not an exhaustive test of every feature combination.

| Feature | Result |
| --- | --- |
| `ai` | Pass |
| `article-plugins` | Pass |
| `embedded-browser` | Pass |
| `feed` | Pass |
| `feed-filters` | Pass |
| `native-reader` | Pass |
| `networking` | Pass |
| `news-proxy` | Pass |
| `page-proxy` | Pass |
| `rss-source` | Pass |
| `save-articles` | Pass |
| `share-articles` | Pass |
| `source-discovery` | Pass |
| `source-management` | Pass |
| `source-providers` | Pass |
| `storage` | Pass |
| `synchronization` | Pass |
| `telegram-proxy` | Pass |
| `telegram-source` | Pass |

The test suite uses Robolectric and local unit tests; no device or live-network tests were run. `FeatureBindings` remains in the implementation, so the architectural refactor is not complete.
