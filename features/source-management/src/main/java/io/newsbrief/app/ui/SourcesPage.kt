package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.newsbrief.modules.core.api.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.SourceKind

@Composable
fun SourcesPage(vm: SourcesUi, openCatalog: (() -> Unit)? = null) {
    val context = LocalContext.current
    val sources by vm.sources.collectAsStateWithLifecycle()
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nb_text_026), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (openCatalog != null) OutlinedButton(onClick = openCatalog) { Text(stringResource(R.string.nb_text_027)) }
                if (vm.sourcePlugins.isNotEmpty()) OutlinedButton(onClick = { dialogVisible = true }) { Text(stringResource(R.string.nb_text_028)) }
            }
        }
        Text(stringResource(R.string.nb_text_029),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            items(sources, key = { it.id }) { source ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                Text(vm.sourcePlugins.firstOrNull { it.id == source.kind }?.title(context) ?: source.kind, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = source.enabled, onCheckedChange = { vm.toggleSource(source.id, it) })
                        }
                        Text(source.address.removePrefix("telegram-proxy:"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.deleteSource(source.id) }) { Text(stringResource(R.string.nb_text_030)) }
                    }
                }
            }
        }
    }
    if (dialogVisible) AddSourceDialog(vm.sourcePlugins, onClose = { dialogVisible = false }) { name, kind, url ->
        vm.addSource(name, kind, url)
        dialogVisible = false
    }
}

@Composable
private fun AddSourceDialog(plugins: List<io.newsbrief.app.network.SourcePluginInfo>, onClose: () -> Unit, onAdd: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(plugins.firstOrNull { it.id == SourceKind.RSS }?.id ?: plugins.firstOrNull()?.id.orEmpty()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.nb_text_031)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.nb_text_032), style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text((plugins.firstOrNull { it.id == kind }?.title(context) ?: stringResource(R.string.nb_choose_type)) + " ▾")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            plugins.forEach { plugin ->
                                DropdownMenuItem(text = { Text(plugin.title(context)) }, onClick = {
                                    kind = plugin.id
                                    expanded = false
                                })
                            }
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.nb_text_034)) })
                OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true,
                    label = { Text(plugins.firstOrNull { it.id == kind }?.addressHint(context).orEmpty()) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, kind, address) }, enabled = name.isNotBlank() && address.isNotBlank()) {
                Text(stringResource(R.string.nb_text_035))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.nb_text_036)) } }
    )
}
