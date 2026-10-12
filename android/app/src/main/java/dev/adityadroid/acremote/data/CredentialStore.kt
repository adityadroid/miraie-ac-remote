package dev.adityadroid.acremote.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Credentials and cached session, encrypted at rest. The password is never logged. */
@Suppress("DEPRECATION") // security-crypto is deprecated upstream but still the requested store.
class CredentialStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "miraie_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val username: String? get() = prefs.getString(K_USER, null)
    val password: String? get() = prefs.getString(K_PASS, null)
    val hasCredentials: Boolean get() = !username.isNullOrEmpty() && !password.isNullOrEmpty()

    fun saveCredentials(username: String, password: String) = prefs.edit {
        putString(K_USER, username).putString(K_PASS, password)
    }

    fun loadSession(): Session? {
        val token = prefs.getString(K_TOKEN, null) ?: return null
        return Session(token, prefs.getString(K_USERID, null), prefs.getLong(K_EXP, 0L))
    }

    fun saveSession(s: Session) = prefs.edit {
        putString(K_TOKEN, s.accessToken).putString(K_USERID, s.userId).putLong(K_EXP, s.expiresAtMs)
    }

    fun clearSession() = prefs.edit { remove(K_TOKEN).remove(K_USERID).remove(K_EXP) }

    fun clearAll() = prefs.edit { clear() }

    private companion object {
        const val K_USER = "username"
        const val K_PASS = "password"
        const val K_TOKEN = "access_token"
        const val K_USERID = "user_id"
        const val K_EXP = "expires_at"
    }
}
