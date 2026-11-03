package io.newsbrief.app.network

import io.newsbrief.app.data.*

/** Stable IDs are stored in sources.kind and articles.sourcePluginId. */
data class SourcePluginInfo(val id: String, val title: String, val addressHint: String,
    val titleResource: Int? = null, val addressHintResource: Int? = null) {
    fun title(context: android.content.Context): String = titleResource?.let(context::getString) ?: title
    fun addressHint(context: android.content.Context): String = addressHintResource?.let(context::getString) ?: addressHint
}

interface NewsSourcePlugin {
    val info: SourcePluginInfo
    fun canonicalAddress(input: String): String
    suspend fun collect(source: SourceEntity): List<ArticleEntity>
    suspend fun discover(): List<SourceEntity> = emptyList()
    fun merge(existing: ArticleEntity, incoming: ArticleEntity): ArticleEntity = existing
}

interface SourceRegistry : NewsCollector {
    override fun supports(source: SourceEntity): Boolean = available.any { it.id == source.kind }
    val available: List<SourcePluginInfo>
    fun plugin(id: String): NewsSourcePlugin
    fun canonicalAddress(input: String, id: String): String
}

class EmptySourceRegistry : SourceRegistry {
    override val available = emptyList<SourcePluginInfo>()
    override fun plugin(id: String): NewsSourcePlugin = error("Источники пока не подключены")
    override fun canonicalAddress(input: String, id: String): String = error("Источники пока не подключены")
    override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
}

interface SourceRegistryOwner { val plugins: SourceRegistry }
