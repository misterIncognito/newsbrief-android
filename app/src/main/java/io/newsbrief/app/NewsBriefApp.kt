package io.newsbrief.app

import android.app.Application
import io.newsbrief.app.runtime.*

open class NewsBriefApp : Application(), io.newsbrief.app.sync.BackgroundTasksOwner {
    lateinit var runtime: FeatureRuntime
        private set
    val repository get() = runtime.repository
    val settings get() = runtime.settings
    override val backgroundTasks get() = runtime.backgroundTasks
    protected open fun installedFeatures(): List<AppFeature> = GeneratedFeatures.create()
    override fun onCreate() {
        super.onCreate()
        runtime = FeatureRuntime(this, installedFeatures())
    }
}
