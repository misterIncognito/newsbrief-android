# Module verification evidence

> Historical generated validation artifacts were removed; rerun the checks to produce current results.

`branches.json` records 22 actual checkouts and four real merge stages. `profiles.json`
records the full assembly and two explicit smaller selections from a complete checkout.
Counts are aggregated once across the included app/core/feature test result directories.
Each record includes exact commits, compilation-tree hashes, compiler project graphs,
selected plugin descriptors, DEX checks and APK SHA-256. `promotions.json` records the
history-preserving branch updates and protected refs. Original compile failure logs are
retained; all final checks passed.

`verify.py` is retained as evidence, not a drop-in CI command: it switches branches and
creates a verification branch. Inspect it before rerunning in a disposable clone with the
matching local toolchain paths. No device backup, user content or secrets are included.
