package io.newsbrief.app.ui
import android.app.Application
import io.newsbrief.app.data.*
import io.newsbrief.app.runtime.*
/** Test fixture resolves actual feature controllers; it owns no feature state. */
class TestAppModel(app: Application, repository: TestNewsRepository, settings: AppSettings) :
    NewsViewModel(app, repository.services, InstalledFeatures.create()) {
    val feed get() = host.controller(FeedUi::class.java)
    val filters get() = host.controller(FeedFilterUi::class.java)
    val saved get() = host.controller(SavedUi::class.java)
    val readerUi get() = host.controller(ReaderUi::class.java)
    val browser get() = host.controller(BrowserUi::class.java)
    val sourcesUi get() = host.controller(SourcesUi::class.java)
    val directory get() = host.controller(DirectoryUi::class.java)
    val telegram get() = host.controller(TelegramProxyUi::class.java)
    init { feed }
}
