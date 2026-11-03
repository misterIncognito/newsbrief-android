package io.newsbrief.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Features contribute their own pages and settings; the shell only arranges navigation. */
@Composable fun NewsBriefScreen(vm: NewsViewModel) {
    val feed = vm.features.firstOrNull { it.id == "feed" && it.title.isNotEmpty() }
    val sections = vm.features.filter { it.id != "feed" && (it.title.isNotEmpty() || it.hasSettings) }.sortedBy { it.order }
    var selected by rememberSaveable { mutableStateOf(if (feed != null) "feed" else if (sections.isNotEmpty()) "settings" else "home") }
    val section = sections.firstOrNull { selected == "section:${it.id}" || selected == "page:${it.id}" }
    val inPage = selected.startsWith("page:")
    val inSettings = selected == "settings" || section != null
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val overlay = overlays.lastOrNull()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screens = rememberSaveableStateHolder()
    LaunchedEffect(message) { message?.let { vm.clearMessage(); snackbar.showSnackbar(it) } }
    val back = {
        selected = if (inPage && section?.hasSettings == true) "section:${section.id}" else "settings"
    }
    BackHandler(overlay != null || section != null) {
        if (overlay != null) overlay.onBack() else back()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NewsBrief", style = MaterialTheme.typography.headlineMedium)
                    MvpNotice()
                }
                if (feed != null && sections.isEmpty()) Text(feed.title, style = MaterialTheme.typography.labelMedium)
            }
            when {
                overlay != null -> TextButton(onClick = overlay.onBack) { Text(overlay.backLabel) }
                section != null -> TextButton(onClick = back) { Text("← Назад") }
                selected == "feed" -> feed?.Toolbar(vm.host)
            }
        }
    }, bottomBar = {
        if (overlay == null && feed != null && sections.isNotEmpty()) NavigationBar {
            NavigationBarItem(selected == "feed", { selected = "feed" }, icon = { Text("≡") }, label = { Text("Лента", maxLines = 1) })
            NavigationBarItem(inSettings, { selected = "settings" }, icon = { Text("⚙") }, label = { Text("Настройки", maxLines = 1) })
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                overlay != null -> overlay.content()
                selected == "settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Настройки", style = MaterialTheme.typography.headlineSmall)
                    sections.forEach { feature ->
                        Card(onClick = { selected = if (feature.hasSettings) "section:${feature.id}" else "page:${feature.id}" },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(feature.settingsTitle, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                section != null && !inPage -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(section.settingsTitle, style = MaterialTheme.typography.headlineSmall)
                    if (section.title.isNotEmpty()) {
                        OutlinedButton(onClick = { selected = "page:${section.id}" }) { Text("Открыть: ${section.title}") }
                    }
                    section.Settings(vm.host)
                }
                section != null -> screens.SaveableStateProvider(selected) { section.Screen(vm.host) }
                selected == "feed" && feed != null -> screens.SaveableStateProvider(selected) { feed.Screen(vm.host) }
                else -> Text("Новости в одном месте", Modifier.padding(18.dp))
            }
        }
    }
}
