package io.newsbrief.app.features

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.newsbrief.app.runtime.AppGate
import io.newsbrief.app.runtime.FeatureHost
import io.newsbrief.modules.features.privacy.R

internal class PrivacyState(initial: Boolean, launch: Boolean, background: Boolean, pendingLock: Boolean = false) {
    var enabled by mutableStateOf(initial)
    var lockOnLaunch by mutableStateOf(launch)
    var lockOnBackground by mutableStateOf(background)
    var unlocked by mutableStateOf(!initial || !(launch || pendingLock))
    var authenticating by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var request: (Boolean?) -> Unit = {}
    fun shouldLockOnStop() = enabled && lockOnBackground && !authenticating
}
private val LocalPrivacy = staticCompositionLocalOf<PrivacyState> { error("Privacy gate missing") }
private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.fragmentActivity()
    else -> null
}

class PrivacyFeature : AppGate {
    override val id = "privacy"
    override val hasSettings = true
    override fun localizedSettingsTitle(context: Context) = context.getString(R.string.privacy_title)

    @Composable override fun Protect(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val activity = context.fragmentActivity()
        val prefs = remember(context) { context.getSharedPreferences("newsbrief_privacy", Context.MODE_PRIVATE) }
        val state = remember { PrivacyState(prefs.getBoolean("lock_enabled", false), prefs.getBoolean("lock_launch", true), prefs.getBoolean("lock_background", true), prefs.getBoolean("pending_lock", false)) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var requestedChange by remember { mutableStateOf<Boolean?>(null) }
        val prompt = remember(activity) {
            activity?.let {
                BiometricPrompt(it, ContextCompat.getMainExecutor(it), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        requestedChange?.let { enabled ->
                            prefs.edit().putBoolean("lock_enabled", enabled).putBoolean("pending_lock", false).commit()
                            state.enabled = enabled
                        }
                        requestedChange = null
                        val lockAgain = state.enabled && state.lockOnBackground && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                        prefs.edit().putBoolean("pending_lock", lockAgain).commit()
                        state.unlocked = !lockAgain
                        state.authenticating = false
                        state.error = null
                    }
                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        requestedChange = null
                        state.authenticating = false
                        if (state.shouldLockOnStop() && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            prefs.edit().putBoolean("pending_lock", true).commit()
                            state.unlocked = false
                        }
                        state.error = message.toString()
                    }
                })
            }
        }
        state.request = { change ->
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguard.isDeviceSecure || prompt == null) state.error = context.getString(R.string.privacy_setup)
            else if (!state.authenticating) {
                requestedChange = change
                state.authenticating = true
                state.error = null
                try {
                    @Suppress("DEPRECATION")
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(context.getString(R.string.privacy_prompt))
                        .setSubtitle(context.getString(R.string.privacy_subtitle))
                        .setDeviceCredentialAllowed(true).build()
                    prompt.authenticate(info)
                } catch (error: Exception) {
                    requestedChange = null
                    state.authenticating = false
                    state.error = error.localizedMessage ?: context.getString(R.string.privacy_setup)
                }
            }
        }
        DisposableEffect(lifecycle, state) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && state.shouldLockOnStop()) {
                    prefs.edit().putBoolean("pending_lock", true).commit()
                    state.unlocked = false
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        DisposableEffect(activity, state.enabled) {
            if (state.enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        CompositionLocalProvider(LocalPrivacy provides state) {
            if (!state.enabled || state.unlocked) content() else Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.privacy_locked), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { state.request(null) }, enabled = !state.authenticating) { Text(stringResource(R.string.privacy_unlock)) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    @Composable override fun Settings(host: FeatureHost) {
        val state = LocalPrivacy.current
        Text(stringResource(R.string.privacy_description))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.privacy_lock), modifier = Modifier.weight(1f))
            Switch(checked = state.enabled, enabled = !state.authenticating, onCheckedChange = { state.request(it) })
        }
        if (state.enabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.privacy_launch), modifier = Modifier.weight(1f))
                Switch(checked = state.lockOnLaunch, onCheckedChange = { state.lockOnLaunch = it; host.context.getSharedPreferences("newsbrief_privacy", Context.MODE_PRIVATE).edit().putBoolean("lock_launch", it).apply() })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.privacy_background), modifier = Modifier.weight(1f))
                Switch(checked = state.lockOnBackground, onCheckedChange = { state.lockOnBackground = it; host.context.getSharedPreferences("newsbrief_privacy", Context.MODE_PRIVATE).edit().putBoolean("lock_background", it).apply() })
            }
        }
        state.error?.let { Text(stringResource(R.string.privacy_error, it), color = MaterialTheme.colorScheme.error) }
    }
}
