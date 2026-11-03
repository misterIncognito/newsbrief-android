# Optional capability evidence

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

`branches/results.json` records the 22 checkouts and four real merge stages on the final
application source. `profiles/results.json` records 15 earlier compositions on snapshot
02889d1; `profiles/snapshot.json` describes that boundary. Each record contains passing test
counts, APK hashes, enabled features and DEX class-presence checks. Build outputs may include
routine warnings; exitCode and tests report actual outcomes.

Verification scripts are retained as evidence. The branch script creates a verification
branch and switches checkouts: inspect it before rerunning, and use a clean disposable copy
with matching local toolchain paths. It is not a CI entry point. `promotions.json` records
history-preserving branch merges. No device backup, API keys or user content are included.
