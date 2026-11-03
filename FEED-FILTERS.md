# Feed filters and source discovery — 0.4.3

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

## Behavior

The feed has source-category chips and a publisher dropdown. Categories cover
technology, business, Belarus, Poland, world, general news and custom sources.
These are source categories, not an AI classification of individual articles.
Categories and publisher selections intersect; selecting another category resets
the publisher. Reset returns all enabled sources. Filter state stays in the
ViewModel when opening/closing Reader and on rotation.

RSS and Telegram alternatives are grouped by publisher, including Zerkalo and BBC.
Room applies the selected source IDs before the 250-row limit, so an older article
from a selected source is not hidden by newer articles from unrelated sources.
Disabled/removed sources remain excluded. Filtering works offline and does not
change source enablement or delete stored articles.

The Verge and Business Insider were optional catalog entries, not installed
sources. This upgrade adds/enables The Verge, Business Insider Polska and Business
Insider via Google News once. Other source choices are preserved. Deleted defaults
are not re-added after the new seeding flag is stored. Fresh installs have 14 defaults.
Catalog search matches names, addresses and categories, ignoring case/outer spaces.

## Verification

`bash scripts/build-on-mac.sh`: **BUILD SUCCESSFUL**, 42 tests, zero failures.
New tests cover categories, publisher grouping, custom sources, intersected filters,
filtering before the global limit, disabled sources, retained offline text,
search and one-time addition of the three new feed sources.

Live Mac HTTP checks parsed these valid XML feeds:

- The Verge Atom: 10 entries, https://www.theverge.com/rss/index.xml
- Business Insider Polska RSS: 20 items, https://businessinsider.com.pl/.feed
- Business Insider via Google News RSS: 100 items; links may lead through Google News.

APK: `app/build/outputs/apk/debug/app-debug.apk`, versionCode 7.
SHA-256: `3b8ccb39b4d5205c680d4e74e78ba25a53a3e91618c691c72baf610ed6b7451d`.
Build output: validation/filters-build.txt.

Device installation and visual verification are pending: the previously connected
Motorola phone disconnected during this request. No device data was changed.
