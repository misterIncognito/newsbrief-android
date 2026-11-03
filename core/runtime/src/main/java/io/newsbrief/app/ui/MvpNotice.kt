package io.newsbrief.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Composable
fun MvpNotice() {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("MVP", color = Color(0xFFB00020)) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Early preview") },
        text = { Text("This is an early preview version and may contain issues or behave unexpectedly.") },
        confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } }
    )
}
