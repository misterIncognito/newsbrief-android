# NewsBrief 0.4.10: return-to-feed refresh, Save and Share

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

Returning to the News feed from another tab, an article or a browser opened from the feed
starts a source refresh. Resuming the app with the feed visible also refreshes it. The
effect is scoped to feed visibility and the resumed Android lifecycle; recomposition or
an off-screen feed does not trigger downloads. If refresh is already running, the automatic
request is coalesced. Manual refresh and source changes retain their existing queuing behavior.
Successful automatic refresh does not show a result snackbar. Feed filters remain selected,
and the news and Saved lists keep separate scroll positions across reader navigation.

Save and Share buttons appear under news cards, Telegram posts and article titles in the
native reader. Save toggles a persistent bookmark. A new **Сохранённые** view beside
**Все новости** shows all saved items, including articles from disabled/deleted sources and
publisher articles opened through Telegram that do not have their own feed card. Removing
a bookmark does not delete cached article content. Existing category/source filters apply
to **Все новости** and remain selected when returning from Saved.

Bookmarks are stored in Room's new `saved_articles` table. Migration 6 → 7 adds that table;
the existing migration chain remains available. Article content, images metadata, links,
proxy requirements, sources and summaries are preserved. Saving a stale feed preview cannot
replace richer cached reader content. Saved items are protected if the age-pruning helper
is used. Save is a bookmark action; full-text/image downloading continues through the
existing reader and background prefetch. Already cached content remains readable offline.

Share opens Android's native chooser with the title and original publisher/post URL as
plain text. It does not share a Worker or AMP URL, and no message is sent automatically.

The debug APK is version 0.4.10, code 14. The connected-phone check found no device, so
installation and physical-phone verification are pending. Existing phone data was not changed.

## Validation

`testDebugUnitTest assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
**130 tests pass**, zero failures/errors/skips. Ten new tests cover:

- Feed visibility/foreground transitions without refreshing a hidden feed or on recomposition.
- Automatic refresh coalescing with a running refresh, quiet success and filter retention.
- Complete Compose navigation: scroll down, open a reader, save, return, refresh while
  retaining position, switch to Saved and remove a bookmark without deleting content.
- Android share payload and actual system chooser invocation; visible Save/Unsave state.
- Persistent bookmarks after database reopen, disabled/deleted sources, standalone publisher
  readers, saved Telegram links/text, and protection from the age-pruning helper.
- Migration from schema 6 while preserving article data, metadata, source settings, summaries,
  catalog cache and proxy requirements. Earlier-version migration tests now run through 7.

An initial full-screen test timed out because asynchronous Room/ViewModel callbacks and
Compose frames needed explicit synchronization in the test setup. The final test uses a
controlled resumed lifecycle, coroutine scheduler and UI-idle synchronization and passes
with the full suite. The physical Android test APK was built but not run on a phone.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Counts/hash: validation/saved-refresh-0410-results.json.
Build log: validation/build-0410-output.txt.
