package io.newsbrief.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API keys are encrypted with non-exportable Android Keystore AES key, never bundled into APK. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("newsbrief_settings", Context.MODE_PRIVATE)

    companion object {
        const val QWEN = "Qwen"
        const val OPENAI = "OpenAI"
        private const val ALIAS = "newsbrief_api_keys_v1"
    }

    data class Config(val provider: String, val model: String)

    fun read(): Config {
        val provider = prefs.getString("provider", QWEN) ?: QWEN
        val model = prefs.getString("model_$provider", null)
            ?: if (provider == QWEN) "qwen3.5-flash" else "gpt-5.4-mini"
        return Config(provider, model)
    }

    fun setProvider(provider: String) {
        require(provider == QWEN || provider == OPENAI)
        prefs.edit().putString("provider", provider).apply()
    }

    fun setModel(provider: String, model: String) {
        require(model.isNotBlank())
        prefs.edit().putString("model_$provider", model.trim()).apply()
    }

    private fun secret(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun saveApiKey(provider: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove("key_$provider").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val bytes = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + bytes
        prefs.edit().putString("key_$provider", Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun hasApiKey(provider: String): Boolean = prefs.contains("key_$provider")

    fun loadApiKey(provider: String): String {
        val encoded = prefs.getString("key_$provider", null) ?: return ""
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { "" } // e.g. keys invalidated after OS restore
    }

    fun isSourcesCatalogV2Seeded(): Boolean = prefs.getBoolean("sources_catalog_v2_seeded", false)

    fun markSourcesCatalogV2Seeded() {
        prefs.edit().putBoolean("sources_catalog_v2_seeded", true).apply()
    }

    fun isSourcesCatalogV3Seeded(): Boolean = prefs.getBoolean("sources_catalog_v3_seeded", false)

    fun markSourcesCatalogV3Seeded() {
        prefs.edit().putBoolean("sources_catalog_v3_seeded", true).apply()
    }
}
