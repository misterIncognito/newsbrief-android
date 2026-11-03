package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.AiSettings

@Composable fun AiSettingsSection(vm: AiUi) {
    val config by vm.config.collectAsStateWithLifecycle()
    val saved by vm.hasKey.collectAsStateWithLifecycle()
    var model by remember(config) { mutableStateOf(config.model) }
    var key by remember { mutableStateOf("") }
    Text(stringResource(R.string.nb_text_067), style = MaterialTheme.typography.titleLarge)
    listOf(AiSettings.QWEN, AiSettings.OPENAI).forEach { provider ->
        FilterChip(config.provider == provider, { vm.setProvider(provider) }, label = { Text(provider) })
    }
    OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.nb_text_068)) })
    OutlinedButton(onClick = { vm.setModel(model) }) { Text(stringResource(R.string.nb_text_069)) }
    Text(if (saved) stringResource(R.string.nb_api_saved) else stringResource(R.string.nb_api_add))
    OutlinedTextField(key, { key = it }, visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.nb_text_072)) })
    OutlinedButton(onClick = { vm.saveKey(key); key = "" }) { Text(stringResource(R.string.nb_text_073)) }
}
