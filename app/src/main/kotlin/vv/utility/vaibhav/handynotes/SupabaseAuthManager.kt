package vv.utility.vaibhav.handynotes

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/** Small Java-callable adapter around supabase-kt's coroutine based Auth SDK. */
object SupabaseAuthManager {
    private const val TAG = "SupabaseAuthManager"
    interface Callback {
        fun onSuccess(value: String?)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var client: SupabaseClient? = null
    private var clientConfiguration: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    private fun getClient(context: Context): SupabaseClient {
        val appContext = context.applicationContext
        val url = ApiConfig.getSupabaseUrl(appContext)
        val key = ApiConfig.getSupabasePublishableKey(appContext)
        require(url.isNotBlank() && key.isNotBlank()) {
            "Set the Supabase project URL and publishable key in Server Settings first."
        }

        val config = "$url\n$key"
        if (client == null || clientConfiguration != config) {
            client = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
                install(Auth) {
                    sessionManager = AndroidSecureSessionManager(appContext)
                }
            }
            clientConfiguration = config
        }
        return client!!
    }

    @JvmStatic
    fun signIn(context: Context, email: String, password: String, callback: Callback) {
        scope.launch {
            try {
                val authClient = getClient(context)
                authClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val signedInEmail = authClient.auth.currentSessionOrNull()?.user?.email
                mainHandler.post { callback.onSuccess(signedInEmail) }
            } catch (error: Exception) {
                logAuthFailure("Sign-in", error)
                mainHandler.post { callback.onError(error.message ?: "Supabase sign-in failed") }
            }
        }
    }

    @JvmStatic
    fun signUp(context: Context, email: String, password: String, callback: Callback) {
        scope.launch {
            try {
                val authClient = getClient(context)
                authClient.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val signedInEmail = authClient.auth.currentSessionOrNull()?.user?.email ?: email
                mainHandler.post { callback.onSuccess(signedInEmail) }
            } catch (error: Exception) {
                logAuthFailure("Sign-up", error)
                mainHandler.post { callback.onError(error.message ?: "Supabase sign-up failed") }
            }
        }
    }

    @JvmStatic
    fun signOut(context: Context, callback: Callback) {
        scope.launch {
            try {
                getClient(context).auth.signOut()
                mainHandler.post { callback.onSuccess(null) }
            } catch (error: Exception) {
                logAuthFailure("Sign-out", error)
                mainHandler.post { callback.onError(error.message ?: "Supabase sign-out failed") }
            }
        }
    }

    @JvmStatic
    fun getSignedInEmail(context: Context, callback: Callback) {
        scope.launch {
            try {
                val authClient = getClient(context)
                authClient.auth.sessionStatus.first { it !is io.github.jan.supabase.auth.status.SessionStatus.Initializing }
                mainHandler.post { callback.onSuccess(authClient.auth.currentSessionOrNull()?.user?.email) }
            } catch (error: Exception) {
                mainHandler.post { callback.onError(error.message ?: "Could not read the Supabase session") }
            }
        }
    }

    /** Called from the API executor, never from the UI thread. */
    @JvmStatic
    fun getAccessTokenBlocking(context: Context): String? = runBlocking(Dispatchers.IO) {
        val authClient = getClient(context)
        authClient.auth.sessionStatus.first { it !is io.github.jan.supabase.auth.status.SessionStatus.Initializing }
        val token = authClient.auth.currentSessionOrNull()?.accessToken
        Log.d(TAG, "Access token available: ${!token.isNullOrBlank()}")
        token
    }

    /** Refresh explicitly after an API 401, then return the SDK-managed new token. */
    @JvmStatic
    fun refreshAccessTokenBlocking(context: Context): String? = runBlocking(Dispatchers.IO) {
        val authClient = getClient(context)
        Log.i(TAG, "Refreshing Supabase session")
        try {
            authClient.auth.refreshCurrentSession()
            val token = authClient.auth.currentSessionOrNull()?.accessToken
            Log.i(TAG, "Supabase session refresh complete; access token available: ${!token.isNullOrBlank()}")
            token
        } catch (error: Exception) {
            logAuthFailure("Session refresh", error)
            throw error
        }
    }

    private fun logAuthFailure(operation: String, error: Exception) {
        val detail = error.message
        Log.e(TAG, "$operation failed (${error.javaClass.simpleName})${if (detail.isNullOrBlank()) "" else ": $detail"}")
    }

    /** Avoid reusing a stored session after changing to a different Supabase project. */
    @JvmStatic
    fun resetForConfigurationChange(context: Context, callback: Callback) {
        scope.launch {
            try {
                client?.auth?.signOut()
            } catch (_: Exception) {
                // Clear the in-memory client even if the old project is unreachable.
            }
            synchronized(this@SupabaseAuthManager) {
                client = null
                clientConfiguration = null
            }
            AndroidSecureSessionManager(context.applicationContext).deleteSession()
            mainHandler.post { callback.onSuccess(null) }
        }
    }

}

/** Encrypted Supabase session persistence using an AES key held by Android Keystore. */
private class AndroidSecureSessionManager(context: Context) : SessionManager {
    private val preferences = context.getSharedPreferences("supabase_secure_session", Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.encodeToString(UserSession.serializer(), session).toByteArray(Charsets.UTF_8))
        val stored = cipher.iv + encrypted
        preferences.edit().putString(SESSION_KEY, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val storedText = preferences.getString(SESSION_KEY, null) ?: return null
        val stored = Base64.decode(storedText, Base64.NO_WRAP)
        if (stored.size <= IV_LENGTH) return null
        val iv = stored.copyOfRange(0, IV_LENGTH)
        val encrypted = stored.copyOfRange(IV_LENGTH, stored.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val sessionJson = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        return json.decodeFromString(UserSession.serializer(), sessionJson)
    }

    override suspend fun deleteSession() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "handy_notes_supabase_session_key"
        const val SESSION_KEY = "encrypted_session"
        const val IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
