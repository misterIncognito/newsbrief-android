package io.newsbrief.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.runtime.FeaturePageNavigation

/** Features contribute their own pages and settings; the shell only arranges navigation. */
@Composable fun NewsBriefScreen(vm: NewsViewModel) {
    val feed = vm.features.firstOrNull { it.id == "feed" && it.title.isNotEmpty() }
    val sections = vm.features.filter { it.id != "feed" && (it.title.isNotEmpty() || it.hasSettings) }.sortedBy { it.order }
    var selected by rememberSaveable { mutableStateOf(if (feed != null) "feed" else if (sections.isNotEmpty()) "settings" else "home") }
    var returnPage by rememberSaveable { mutableStateOf("") }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val section = sections.firstOrNull { selected == "section:${it.id}" || selected == "page:${it.id}" }
    val inPage = selected.startsWith("page:")
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val overlay = overlays.lastOrNull()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screens = rememberSaveableStateHolder()
    LaunchedEffect(message) { message?.let { vm.clearMessage(); snackbar.showSnackbar(it) } }
    val back = {
        selected = if (returnPage.isNotEmpty()) returnPage
            else if (inPage && section?.hasSettings == true) "section:${section.id}" else "settings"
        returnPage = ""
    }
    val navigation = object : FeaturePageNavigation {
        override fun hasPage(featureId: String) = sections.any { it.id == featureId && it.title.isNotEmpty() }
        override fun openPage(featureId: String) {
            if (hasPage(featureId)) {
                returnPage = selected
                selected = "page:$featureId"
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (overlay != null) TextButton(onClick = overlay.onBack) { Text(overlay.backLabel) }
                else {
                    if (section != null) IconButton(onClick = back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    if (selected == "feed") vm.features.sortedBy { it.order }.forEach { it.Toolbar(vm.host, navigation) }
                    if (feed != null || sections.isNotEmpty()) Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Меню") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (feed != null) DropdownMenuItem(text = { Text("Лента") }, onClick = {
                                vm.features.forEach { it.onFeedSelected(vm.host) }
                                returnPage = ""; selected = "feed"; menuOpen = false
                            })
                            vm.features.sortedBy { it.order }.forEach { feature ->
                                feature.MenuItems(vm.host, openFeed = { returnPage = ""; selected = "feed" },
                                    closeMenu = { menuOpen = false })
                            }
                            if (sections.isNotEmpty()) DropdownMenuItem(text = { Text("Настройки") }, onClick = {
                                returnPage = ""; selected = "settings"; menuOpen = false
                            })
                        }
                    }
                }
            }
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
                section != null -> screens.SaveableStateProvider(selected) { section.Screen(vm.host, navigation) }
                selected == "feed" && feed != null -> screens.SaveableStateProvider(selected) { feed.Screen(vm.host, navigation) }
                else -> Text("Новости в одном месте", Modifier.padding(18.dp))
            }
        }
    }
}
