package io.newsbrief.app

import android.app.Application
import io.newsbrief.app.runtime.*

open class NewsBriefApp : Application(), FeatureRuntimeOwner, io.newsbrief.app.sync.BackgroundTasksOwner {
    final override lateinit var runtime: FeatureRuntime
        private set
    val settings get() = runtime.settings
    override val backgroundTasks get() = runtime.backgroundTasks
    protected open fun installedFeatures(): List<AppFeature> = InstalledFeatures.create()
    override fun onCreate() {
        super.onCreate()
        runtime = FeatureRuntime(this, installedFeatures())
        runtime.start()
    }
}
