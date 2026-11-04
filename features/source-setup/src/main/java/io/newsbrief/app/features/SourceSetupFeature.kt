package io.newsbrief.app.features

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.newsbrief.app.runtime.FeatureHost
import io.newsbrief.app.runtime.SetupWizard
import io.newsbrief.app.ui.DirectoryUi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.modules.features.source_setup.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourceSetupFeature : SetupWizard {
    override val id = "source-setup"

    @Composable override fun ShowSetup(host: FeatureHost, hasPage: (String) -> Boolean,
        showPage: @Composable (String) -> Unit, finish: () -> Unit): Boolean {
        val prefs = remember(host) { host.context.getSharedPreferences("newsbrief_source_setup", Context.MODE_PRIVATE) }
        var active by remember(host) { mutableStateOf<Boolean?>(null) }
        var step by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(host) {
            val existing = withContext(Dispatchers.IO) { host.services.store.sourceDao().count() > 0 }
            val legacy = host.services.settings.isSourcesCatalogV5Seeded()
            val completed = prefs.getBoolean("completed", false)
            val started = prefs.getBoolean("started", false)
            if (!completed && !started && (existing || legacy)) prefs.edit().putBoolean("completed", true).apply()
            if (!completed && !started && !existing && !legacy) prefs.edit().putBoolean("started", true).apply()
            active = !completed && (started || (!existing && !legacy))
        }
        if (active == false) return false
        if (active == null) {
            Box(Modifier.fillMaxSize()) { CircularProgressIndicator() }
            return true
        }
        val pages = listOf("welcome") + listOf("source-discovery", "source-management").filter(hasPage)
        val current = pages[step.coerceIn(0, pages.lastIndex)]
        val directory = if (current == "source-discovery") host.optional(DirectoryUi::class.java) else null
        val directoryBusy = directory?.directoryState?.collectAsStateWithLifecycle()?.value?.addingAddress != null
        val complete = {
            prefs.edit().putBoolean("completed", true).apply()
            active = false
            finish()
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text(stringResource(when (current) {
                    "source-discovery" -> R.string.setup_catalog
                    "source-management" -> R.string.setup_custom
                    else -> R.string.setup_welcome
                }), Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (current == "welcome") Text(stringResource(R.string.setup_intro), Modifier.padding(16.dp))
                    else showPage(current)
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.setup_back)) }
                    else TextButton(onClick = complete) { Text(stringResource(R.string.setup_skip)) }
                    Button(onClick = { if (step < pages.lastIndex) step++ else complete() }, enabled = !directoryBusy) {
                        Text(stringResource(if (step < pages.lastIndex) R.string.setup_next else R.string.setup_finish))
                    }
                }
            }
        }
        return true
    }
}
