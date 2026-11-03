# Architecture refactor status

This is a work in progress. Existing `dev`, `feature/*`, and `integration/feed` branches
are retained. `isolated/*` branches add only the named feature over the refactor base;
`integration/isolated-feed` assembles feed, networking, source providers, RSS and storage.

Explicit assembly selection, module-owned build prerequisites and duplicate singleton provider checks are implemented. Feature services and the runtime receive their composition explicitly.
RSS, public Telegram and networking now use the transport/router from their composition. Synchronization receives image origin resolution from the composition instead of a global lookup.
The central feature registry still contains unrelated contracts; several consumers still
locate services globally, and other singleton registrations still permit replacement.
These need further decomposition before the architecture request can be considered complete.

The shell build/tests and a full-feature build/tests passed at the initial checkpoints.
The progressive integration run passed through 10 features. Stage 11 was interrupted
because concurrent Gradle runs caused extreme host memory pressure; it was not a test
failure. Later stages have not yet been checked. The new RSS and Telegram injection tests
passed separately. The updated feed integration build and unit tests passed. The synchronization-only assembly and its proxy-resolution test passed. The remaining integrations have not been revalidated after these changes.
