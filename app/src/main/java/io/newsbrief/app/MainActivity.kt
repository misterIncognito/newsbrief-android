package io.newsbrief.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.AppGate
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import io.newsbrief.app.ui.NewsBriefScreen
import io.newsbrief.app.ui.NewsViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: NewsViewModel = viewModel()
                ProtectFeatures(vm.features.filterIsInstance<AppGate>()) { NewsBriefScreen(vm) }
            }
        }
    }
}

@Composable
private fun ProtectFeatures(gates: List<AppGate>, content: @Composable () -> Unit) {
    if (gates.isEmpty()) content() else gates.first().Protect { ProtectFeatures(gates.drop(1), content) }
}
