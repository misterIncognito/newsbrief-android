# BBC and Zerkalo Telegram sources — 0.4.2

Added official public channels alongside their existing RSS feeds:

- BBC Russian: https://t.me/s/bbcrussian
- Zerkalo: https://t.me/s/zerkalo_io

Both public previews returned 20 message wrappers in the live Mac check.
The sources are enabled and added once on upgrade or first launch. Existing source
choices are preserved; subsequently disabling/removing these additions is respected.
No Room schema change is required. New installations now have 11 default sources.
Newly added sources are fetched first, ahead of potentially slow older feeds.
Telegram posts preserve full text, paragraphs and image metadata for native/offline reading.

`bash scripts/build-on-mac.sh`: BUILD SUCCESSFUL, 35 tests, zero failures.
Regression coverage includes the new catalog upgrade and long Telegram posts with
paragraphs/photos, plus existing refresh/routing/parsing/migration tests.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `554c5d102891dcf1c681913b312506da838a98c428227cca1df4c7c71909dab5`.
Build output: [validation/telegram-build.txt](validation/telegram-build.txt).

Installed on the connected Motorola phone without clearing data. Room confirmed
both new sources enabled, while Meduza and Polsat remained disabled as selected.
After the final installation, Room contained **20 BBC Telegram posts and 20
Zerkalo Telegram posts**, with both sources enabled. The phone lock screen still
prevents onscreen verification.
