package io.newsbrief.app.features

import androidx.room.Room
import android.content.Context
import androidx.compose.runtime.Composable
import io.newsbrief.modules.features.storage.R
import io.newsbrief.app.data.*
import io.newsbrief.app.runtime.*

class StorageFeature : AppFeature {
    override val id = "storage"
    override val hasSettings = true
    override fun localizedSettingsTitle(context: Context) = context.getString(R.string.storage_title)
    @Composable override fun Settings(host: FeatureHost) { StorageSettings(host) }
    override fun configure(bindings: FeatureBindings) {
        bindings.persistentStorage = { context ->
            Room.databaseBuilder(context, NewsDatabase::class.java, "newsbrief.sqlite")
                .addMigrations(NewsMigrations.FROM_1_2, NewsMigrations.FROM_2_3, NewsMigrations.FROM_3_4,
                    NewsMigrations.FROM_4_5, NewsMigrations.FROM_5_6, NewsMigrations.FROM_6_7, NewsMigrations.FROM_7_8, NewsMigrations.FROM_8_9, NewsMigrations.FROM_9_10)
                .build() to SettingsStore(context)
        }
    }
}
