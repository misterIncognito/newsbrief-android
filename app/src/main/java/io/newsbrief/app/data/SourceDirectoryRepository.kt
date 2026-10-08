package io.newsbrief.app.data

import io.newsbrief.app.network.*
import kotlinx.coroutines.CancellationException

class SourceDirectoryRepository(private val cache: CatalogSearchDao, private val network: DirectoryNetwork,
                                private val now: () -> Long = { System.currentTimeMillis() }) {
    suspend fun search(provider: DirectoryProvider, rawQuery: String, offset: Int = 0): DirectoryResults {
        require(offset >= 0)
        val query = rawQuery.trim().take(120)
        require(query.length >= 2 || provider == DirectoryProvider.CURATED) { "Введи хотя бы 2 символа" }
        if (provider == DirectoryProvider.CURATED) return network.search(provider, query)
        val key = if (provider == DirectoryProvider.TELEGRAM) "TELEGRAM:tgden-v1:${query.lowercase(java.util.Locale.ROOT)}:$offset"
            else "${provider.name}:${query.lowercase(java.util.Locale.ROOT)}"
        val saved = cache.find(key)
        val previous = saved?.let { runCatching { DirectoryParser.decode(it.responseJson) }.getOrNull() }
        if (saved != null && previous != null && now() - saved.fetchedAt in 0..300_000L)
            return previous.copy(cached = true)
        try {
            val result = if (offset == 0) network.search(provider, query) else network.more(provider, query, offset)
            cache.save(CatalogSearchEntity(key, DirectoryParser.encode(result), now()))
            cache.prune()
            return result
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (previous != null) return previous.copy(cached = true, notice = "Каталог сейчас недоступен. Показаны сохранённые результаты.")
            throw e
        }
    }
}
