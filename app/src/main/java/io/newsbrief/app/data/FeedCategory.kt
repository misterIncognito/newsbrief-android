package io.newsbrief.app.data

/** Categories describe sources, so no network or AI classification is needed offline. */
enum class FeedCategory(val label: String) {
    ALL("Все"), TECH("Технологии"), BUSINESS("Бизнес"), BELARUS("Беларусь"),
    POLAND("Польша"), WORLD("Мир"), GENERAL("Общие новости"), OTHER("Другие")
}

data class FeedSourceOption(val key: String, val label: String, val ids: List<Long>)

