package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private val appCheckInstalled = AtomicBoolean(false)
private val firebaseInitLock = Any()

/**
 * Guarantees that the default [FirebaseApp] (`[DEFAULT]`) is initialized in this process.
 * First verifies whether [FirebaseApp.getInstance] is already available; then tries standard
 * google-services resource initialization; if that returns null (e.g., on standalone exported APKs
 * or OEM resource package lookup edge cases), falls back to explicit [FirebaseOptions] built from
 * compile-time bundled resource strings.
 */
fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
    runCatching { FirebaseApp.getInstance() }.getOrNull()?.let { return it }

    synchronized(firebaseInitLock) {
        runCatching { FirebaseApp.getInstance() }.getOrNull()?.let { return it }

        val appContext = context.applicationContext ?: context

        // 1. Standard google-services resource lookup
        val defaultInit = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
        if (defaultInit != null) {
            return runCatching { FirebaseApp.getInstance() }.getOrDefault(defaultInit)
        }

        // 2. Fallback: explicit lookup across standard google-services keys and compile-time fallback strings
        val appId = resolveStringResource(appContext, "google_app_id")
            ?: runCatching { appContext.getString(R.string.fb_fallback_app_id) }.getOrNull()
        val apiKey = resolveStringResource(appContext, "google_api_key")
            ?: runCatching { appContext.getString(R.string.fb_fallback_api_key) }.getOrNull()
        val projectId = resolveStringResource(appContext, "project_id")
            ?: runCatching { appContext.getString(R.string.fb_fallback_project_id) }.getOrNull()
        val senderId = resolveStringResource(appContext, "gcm_defaultSenderId")
            ?: runCatching { appContext.getString(R.string.fb_fallback_gcm_sender_id) }.getOrNull()
        val storageBucket = resolveStringResource(appContext, "google_storage_bucket")
            ?: runCatching { appContext.getString(R.string.fb_fallback_storage_bucket) }.getOrNull()

        if (appId.isNullOrBlank() || apiKey.isNullOrBlank()) {
            Log.e(TAG, "Unable to resolve Firebase applicationId or apiKey from resources.")
            return null
        }

        val optionsBuilder = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)

        if (!projectId.isNullOrBlank()) optionsBuilder.setProjectId(projectId)
        if (!senderId.isNullOrBlank()) optionsBuilder.setGcmSenderId(senderId)
        if (!storageBucket.isNullOrBlank()) optionsBuilder.setStorageBucket(storageBucket)

        return try {
            val initialized = FirebaseApp.initializeApp(appContext, optionsBuilder.build())
            Log.d(TAG, "Default FirebaseApp initialized via fallback resource options.")
            initialized
        } catch (e: IllegalStateException) {
            runCatching { FirebaseApp.getInstance() }.getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize default FirebaseApp with fallback options", e)
            null
        }
    }
}

fun getFirebaseStatusSummary(context: Context): Pair<Boolean, String> {
    val app = ensureFirebaseInitialized(context)
    return if (app != null) {
        true to "Ready • Gemini 3.8 Flash (${app.options.projectId ?: "Firebase AI"})"
    } else {
        false to "AI Engine unavailable (Firebase configuration missing)"
    }
}

private fun resolveStringResource(context: Context, name: String): String? {
    val packages = listOf(context.packageName, "com.example").distinct()
    for (pkg in packages) {
        val resId = context.resources.getIdentifier(name, "string", pkg)
        if (resId != 0) {
            val value = runCatching { context.getString(resId) }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
    }
    return null
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val firebaseApp = ensureFirebaseInitialized(context)
        if (firebaseApp == null) {
            Log.e(TAG, "FirebaseApp could not be initialized; skipping App Check provider setup.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = firebaseApp.persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug secret is persisted before
                // DebugAppCheckProviderFactory initializes.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            if (appCheckInstalled.compareAndSet(false, true)) {
                FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            val siteKey = resolveStringResource(context, RECAPTCHA_SITE_KEY_RES)
                ?: runCatching { context.getString(R.string.recaptcha_site_key) }.getOrNull()
            if (!siteKey.isNullOrBlank()) {
                if (appCheckInstalled.compareAndSet(false, true)) {
                    FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found or blank.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
