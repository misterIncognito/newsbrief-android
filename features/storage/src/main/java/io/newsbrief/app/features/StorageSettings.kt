package io.newsbrief.app.features

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.NewsDatabase
import io.newsbrief.app.runtime.FeatureHost
import io.newsbrief.modules.features.storage.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun StorageSettings(host: FeatureHost) {
    val database = host.services.store as? NewsDatabase ?: return
    val count by database.articleDao().observeStoredCount().collectAsState(initial = 0)
    var includeSaved by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var bytes by remember { mutableLongStateOf(0) }
    val context = host.context
    val scope = rememberCoroutineScope()
    LaunchedEffect(count, deleting) {
        bytes = withContext(Dispatchers.IO) {
            val file = context.getDatabasePath("newsbrief.sqlite")
            listOf(file, File(file.path + "-wal"), File(file.path + "-shm"), File(context.filesDir, "reader-images"))
                .sumOf { root -> if (root.isDirectory) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else root.length() }
        }
    }
    val labels = listOf(R.string.storage_all, R.string.storage_day, R.string.storage_five, R.string.storage_ten)
    val days = listOf(0, 1, 5, 10)
    Text(stringResource(R.string.storage_count, count))
    Text(stringResource(R.string.storage_size, Formatter.formatFileSize(context, bytes)))
    Text(stringResource(R.string.storage_note), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = includeSaved, onCheckedChange = { includeSaved = it })
        Text(stringResource(R.string.storage_include_saved))
    }
    Text(stringResource(R.string.storage_cleanup), style = MaterialTheme.typography.titleMedium)
    labels.forEachIndexed { index, label ->
        OutlinedButton(onClick = { selection = index }, enabled = !deleting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(label))
        }
    }
    selection?.let { index ->
        AlertDialog(onDismissRequest = { selection = null }, title = { Text(stringResource(R.string.storage_confirm_title)) },
            text = { Text(stringResource(R.string.storage_confirm, stringResource(labels[index]),
                stringResource(if (includeSaved) R.string.storage_saved_deleted else R.string.storage_saved_kept))) },
            confirmButton = { TextButton(onClick = {
                selection = null
                deleting = true
                val include = includeSaved
                scope.launch {
                    try {
                        val cutoff = System.currentTimeMillis() - days[index].toLong() * 24 * 60 * 60 * 1000
                        database.articleDao().cleanArticles(cutoff, days[index] == 0, include)
                        if (days[index] == 0 && database.savedArticleDao().observeUrls().first().isEmpty()) {
                            withContext(Dispatchers.IO) {
                                val images = File(context.filesDir, "reader-images")
                                check(!images.exists() || images.deleteRecursively()) { "Could not remove offline images" }
                            }
                        }
                    } catch (error: Exception) { host.report(context.getString(R.string.storage_error, error.localizedMessage)) }
                    finally { deleting = false }
                }
            }) { Text(stringResource(R.string.storage_delete)) } },
            dismissButton = { TextButton(onClick = { selection = null }) { Text(stringResource(R.string.storage_cancel)) } })
    }
}
