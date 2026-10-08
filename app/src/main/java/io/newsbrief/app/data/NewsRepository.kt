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
    settings: SettingsStore
) {
    private val ai = AiClient(settings)
    private val refreshMutex = Mutex()

    data class RefreshResult(val added: Int, val sources: Int, val errors: List<String>)

    fun observeSources(): Flow<List<SourceEntity>> = db.sourceDao().observe()
    fun observeLatest(): Flow<List<ArticleEntity>> = db.articleDao().observeRecent(System.currentTimeMillis() - 24L * 60 * 60 * 1_000)

    suspend fun seedDefaults() {
        if (db.sourceDao().count() != 0) return
        listOf(
            SourceEntity(name = "BBC Русская служба", kind = SourceKind.RSS,
                address = "https://feeds.bbci.co.uk/russian/rss.xml"),
            SourceEntity(name = "Зеркало", kind = SourceKind.RSS,
                address = "https://news.zerkalo.io/rss/all.rss"),
            SourceEntity(name = "BILD на русском", kind = SourceKind.TELEGRAM,
                address = "https://t.me/s/BILD_Russian")
        ).forEach { db.sourceDao().insert(it) }
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
        db.articleDao().deleteOld(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1_000)
        RefreshResult(added = total, sources = sources.size, errors = errors)
    }

    suspend fun digest(mode: String): DigestEntity? = db.digestDao().find(LocalDate.now().toString(), mode)

    suspend fun generateDigest(mode: String): DigestEntity {
        require(mode == DigestMode.SHORT || mode == DigestMode.FULL)
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1_000
        val recent = db.articleDao().recent(cutoff, 180)
            .distinctBy { FeedRules.normalizedTitle(it.title) }
        val digest = ai.generate(recent, mode)
        db.digestDao().save(digest)
        return digest
    }
}
