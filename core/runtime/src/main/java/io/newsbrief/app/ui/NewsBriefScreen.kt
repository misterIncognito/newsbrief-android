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

/** Available screens come from installed features, with no direct feature imports. */
@Composable fun NewsBriefScreen(vm: NewsViewModel) {
    val pages = vm.features.filter { it.title.isNotEmpty() }.sortedBy { it.order }
    val settingsFeatures = vm.features.filter { it.hasSettings }
    var selected by rememberSaveable { mutableStateOf(pages.firstOrNull()?.id ?: if (settingsFeatures.isNotEmpty()) "settings" else "home") }
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val overlay = overlays.lastOrNull()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screens = rememberSaveableStateHolder()
    LaunchedEffect(message) {
        message?.let { vm.clearMessage(); snackbar.showSnackbar(it) }
    }
    BackHandler(overlay != null) { overlay?.onBack?.invoke() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("NewsBrief", style = MaterialTheme.typography.headlineMedium)
                if (pages.size == 1 && settingsFeatures.isEmpty()) Text(pages.single().title, style = MaterialTheme.typography.labelMedium)
            }
            if (overlay != null) TextButton(onClick = overlay.onBack) { Text(overlay.backLabel) }
            else pages.firstOrNull { it.id == selected }?.Toolbar(vm.host)
        }
    }, bottomBar = {
        if (overlay == null && (pages.size > 1 || settingsFeatures.isNotEmpty())) NavigationBar {
            pages.forEach { page -> NavigationBarItem(selected == page.id, { selected = page.id },
                icon = { Text(if (page.id == "feed") "≡" else "+") }, label = { Text(page.title) }) }
            if (settingsFeatures.isNotEmpty()) NavigationBarItem(selected == "settings", { selected = "settings" }, icon = { Text("⚙") }, label = { Text("Настройки") })
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                overlay != null -> overlay.content()
                selected == "settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    settingsFeatures.forEach { it.Settings(vm.host) }
                }
                else -> screens.SaveableStateProvider(selected) {
                    val page = pages.firstOrNull { it.id == selected }
                    if (page != null) page.Screen(vm.host) else Text("Новости в одном месте", Modifier.padding(18.dp))
                }
            }
        }
    }
}
