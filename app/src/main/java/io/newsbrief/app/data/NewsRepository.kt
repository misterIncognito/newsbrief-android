package io.newsbrief.app.data

import io.newsbrief.app.ai.AiClient
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.util.FeedRules
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NewsRepository(
    private val db: NewsDatabase,
    private val collector: NewsCollector,
    private val settings: SettingsStore
) {
    private val ai = AiClient(settings)
    private val refreshMutex = Mutex()

    data class RefreshResult(val added: Int, val sources: Int, val errors: List<String>)

    fun observeSources(): Flow<List<SourceEntity>> = db.sourceDao().observe()
    fun observeLatest(): Flow<List<ArticleEntity>> = db.articleDao().observeRecent(System.currentTimeMillis() - 24L * 60 * 60 * 1_000)

    suspend fun seedDefaults() {
        if (settings.isSourcesCatalogV2Seeded()) return
        val presets = if (db.sourceDao().count() == 0) {
            SourceCatalog.presets.filter { it.enabledByDefault }
        } else {
            SourceCatalog.newDefaults
        }
        presets.forEach { db.sourceDao().insert(it.asEntity()) }
        settings.markSourcesCatalogV2Seeded()
    }

    suspend fun addSource(name: String, kind: String, rawAddress: String): Boolean {
        require(name.isNotBlank()) { "Укажи название источника" }
        val address = FeedRules.canonicalAddress(rawAddress, kind)
        return db.sourceDao().insert(SourceEntity(name = name.trim().take(70), kind = kind, address = address)) != -1L
    }

    suspend fun toggleSource(id: Long, enabled: Boolean) = db.sourceDao().setEnabled(id, enabled)
    suspend fun removeSource(id: Long) = db.sourceDao().delete(id)

    suspend fun refresh(): RefreshResult = refreshMutex.withLock {
        seedDefaults()
        val sources = db.sourceDao().enabled()
        var total = 0
        val errors = mutableListOf<String>()
        for (source in sources) {
            try {
                val items = collector.collect(source).filter {
                    // Reject broken/future timestamps (5 min of clock skew allowed).
                    it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
                }
                // Cross-feed duplicate links are ignored by unique SQLite index.
                total += db.articleDao().insertAll(items).count { it != -1L }
            } catch (e: Exception) {
                errors += "${source.name}: ${e.message.orEmpty().take(120)}"
            }
        }
        // Do not silently destroy history. A future retention policy can be user-controlled.
        RefreshResult(added = total, sources = sources.size, errors = errors)
    }

    suspend fun digest(mode: String): DigestEntity? = db.digestDao().find(LocalDate.now().toString(), mode)

    suspend fun generateDigest(mode: String): DigestEntity {
        require(mode == DigestMode.SHORT || mode == DigestMode.FULL)
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1_000
        val recent = db.articleDao().recent(cutoff, 600)
        val digest = ai.generate(DigestArticleSelector.pick(recent), mode)
        db.digestDao().save(digest)
        return digest
    }
}
