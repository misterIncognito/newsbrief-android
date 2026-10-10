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
    val source by vm.readerSource.collectAsStateWithLifecycle()
    val browser by vm.browserUrl.collectAsStateWithLifecycle()
    val forceProxy by vm.browserForceProxy.collectAsStateWithLifecycle()
    val worker by vm.workerUrl.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screens = rememberSaveableStateHolder()
    LaunchedEffect(message) {
        message?.let { vm.clearMessage(); snackbar.showSnackbar(it) }
    }
    BackHandler(source != null || browser != null) {
        if (browser != null) vm.closeBrowser() else if (source != null) vm.closeReader()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("NewsBrief", style = MaterialTheme.typography.headlineMedium)
                if (pages.size == 1 && settingsFeatures.isEmpty()) Text(pages.single().title, style = MaterialTheme.typography.labelMedium)
            }
            when {
                browser != null -> TextButton(onClick = vm::closeBrowser) { Text("← К статье") }
                source != null -> TextButton(onClick = vm::closeReader) { Text("← Назад") }
                selected == "feed" -> TextButton(onClick = { vm.refresh() }, enabled = !refreshing) { Text(if (refreshing) "Загрузка…" else "Обновить") }
            }
        }
    }, bottomBar = {
        if (source == null && browser == null && (pages.size > 1 || settingsFeatures.isNotEmpty())) NavigationBar {
            pages.forEach { page -> NavigationBarItem(selected == page.id, { selected = page.id },
                icon = { Text(if (page.id == "feed") "≡" else "+") }, label = { Text(page.title) }) }
            if (settingsFeatures.isNotEmpty()) NavigationBarItem(selected == "settings", { selected = "settings" }, icon = { Text("⚙") }, label = { Text("Настройки") })
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                browser != null && vm.bindings.browser != null -> vm.bindings.browser?.invoke(browser!!, worker, forceProxy)
                source != null && vm.presentation != null -> vm.presentation.Reader(vm)
                source != null -> Unit
                selected == "settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    settingsFeatures.forEach { it.Settings(vm) }
                }
                else -> screens.SaveableStateProvider(selected) {
                    val page = pages.firstOrNull { it.id == selected }
                    if (page != null) page.Screen(vm) else Text("Новости в одном месте", Modifier.padding(18.dp))
                }
            }
        }
    }
}
