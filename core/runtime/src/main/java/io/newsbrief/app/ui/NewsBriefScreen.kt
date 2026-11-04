package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.newsbrief.modules.core.api.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.runtime.FeaturePageNavigation
import io.newsbrief.app.runtime.SetupWizard

/** Features contribute their own pages and settings; the shell only arranges navigation. */
@Composable fun NewsBriefScreen(vm: NewsViewModel) {
    val context = LocalContext.current
    val feed = vm.features.firstOrNull { it.id == "feed" && it.title.isNotEmpty() }
    val sections = vm.features.filter { it.id != "feed" && (it.title.isNotEmpty() || it.hasSettings) }.sortedBy { it.order }
    var selected by rememberSaveable { mutableStateOf(if (feed != null) "feed" else if (sections.isNotEmpty()) "settings" else "home") }
    var returnPage by rememberSaveable { mutableStateOf("") }
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
    val setup = vm.features.filterIsInstance<SetupWizard>().firstOrNull()
    if (setup != null && setup.ShowSetup(vm.host, { id -> vm.features.any { it.id == id } }, { id ->
            vm.features.firstOrNull { it.id == id }?.SetupScreen(vm.host, navigation)
        }, { vm.host.command("refresh") }, {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NewsBrief", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                AlfaNotice()
            }
        })) return
    BackHandler(overlay != null || section != null) {
        if (overlay != null) overlay.onBack() else back()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NewsBrief", style = MaterialTheme.typography.titleLarge)
                    if (feed != null && sections.isEmpty()) Text(feed.localizedTitle(context), style = MaterialTheme.typography.labelMedium)
                }
                AlfaNotice()
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (overlay != null) TextButton(onClick = overlay.onBack) { Text(overlay.backLabel) }
                else {
                    if (section != null) IconButton(onClick = back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.nb_text_002))
                    }
                    if (feed != null) IconButton(onClick = {
                        vm.features.forEach { it.onFeedSelected(vm.host) }
                        returnPage = ""; selected = "feed"
                    }, modifier = Modifier.testTag("open-feed")) {
                        Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nb_text_004))
                    }
                    if (feed != null) vm.features.sortedBy { it.order }.forEach { feature ->
                        feature.HeaderActions(vm.host, openFeed = { returnPage = ""; selected = "feed" })
                    }
                    if (sections.isNotEmpty()) IconButton(onClick = {
                        returnPage = ""; selected = "settings"
                    }, modifier = Modifier.testTag("open-settings")) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nb_text_005))
                    }
                    if (selected == "feed") {
                        vm.features.sortedBy { it.order }.forEach { it.Toolbar(vm.host, navigation) }
                        Box(Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                            feed?.HeaderSummary(vm.host)
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
                    Text(stringResource(R.string.nb_text_005), style = MaterialTheme.typography.headlineSmall)
                    sections.forEach { feature ->
                        Card(onClick = { selected = if (feature.hasSettings) "section:${feature.id}" else "page:${feature.id}" },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(feature.localizedSettingsTitle(context), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                section != null && !inPage -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(section.localizedSettingsTitle(context), style = MaterialTheme.typography.headlineSmall)
                    if (section.title.isNotEmpty()) {
                        OutlinedButton(onClick = { selected = "page:${section.id}" }) { Text(stringResource(R.string.nb_open_section, section.localizedTitle(context))) }
                    }
                    section.Settings(vm.host)
                }
                section != null -> screens.SaveableStateProvider(selected) { section.Screen(vm.host, navigation) }
                selected == "feed" && feed != null -> screens.SaveableStateProvider(selected) { feed.Screen(vm.host, navigation) }
                else -> Text(stringResource(R.string.nb_text_001), Modifier.padding(18.dp))
            }
        }
    }
}
