package io.newsbrief.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import io.newsbrief.app.ui.NewsBriefScreen
import io.newsbrief.app.ui.NewsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: NewsViewModel = viewModel()
                NewsBriefScreen(vm)
            }
        }
    }
}
