package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun WorkerSettingsSection(vm: WorkerSettingsUi) {
    val worker by vm.workerUrl.collectAsStateWithLifecycle()
    var value by remember(worker) { mutableStateOf(worker) }
    Text(stringResource(R.string.nb_text_076), style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("HTTPS URL Cloudflare Worker") })
    OutlinedButton(onClick = { vm.setWorkerUrl(value) }) { Text(stringResource(R.string.nb_text_077)) }
}
