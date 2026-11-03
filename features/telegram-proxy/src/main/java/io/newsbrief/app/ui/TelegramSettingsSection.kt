package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.features.telegram_proxy.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun TelegramSettingsSection(vm: TelegramProxyUi) {
    val proxy by vm.telegramProxyUrl.collectAsStateWithLifecycle()
    val saved by vm.hasTelegramProxyKey.collectAsStateWithLifecycle()
    var url by remember(proxy) { mutableStateOf(proxy) }
    var key by remember { mutableStateOf("") }
    Text(stringResource(R.string.nb_tg_proxy), style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.nb_tg_url)) })
    Text(if (saved) stringResource(R.string.nb_tg_key_saved) else stringResource(R.string.nb_tg_key_missing))
    OutlinedTextField(key, { key = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.nb_tg_key)) })
    OutlinedButton(onClick = { vm.saveTelegramProxy(url, key); key = "" }) { Text(stringResource(R.string.nb_tg_save)) }
    if (saved) TextButton(onClick = vm::removeTelegramProxyKey) { Text(stringResource(R.string.nb_tg_remove)) }
}
