package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "Auth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

class GoogleAuthManager(
    private val auth: FirebaseAuth = Firebase.auth
) {
    /**
     * Silent auto sign-in for returning users who already authorized this app.
     * Uses ONLY GetGoogleIdOption in its own GetCredentialRequest.
     */
    suspend fun trySilentSignIn(activityContext: Context): GoogleSignInOutcome? {
        if (auth.currentUser != null) {
            return GoogleSignInOutcome.Success(auth.currentUser!!)
        }
        val webClientId = resolveWebClientId(activityContext) ?: return null
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(idToken, null)
                ).await()
                val user = authResult.user
                if (user != null) GoogleSignInOutcome.Success(user) else null
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            null
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In unavailable", e)
            null
        }
    }

    /**
     * Explicit interactive Google Sign-In triggered by tapping the "Sign in with Google" button.
     * Uses ONLY GetSignInWithGoogleOption in its own GetCredentialRequest.
     */
    suspend fun signInInteractive(activityContext: Context): GoogleSignInOutcome {
        val webClientId = resolveWebClientId(activityContext)
            ?: return GoogleSignInOutcome.Error(
                "Google Sign-In configuration is missing (default_web_client_id)."
            )

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken,
                    null
                )
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                    ?: return GoogleSignInOutcome.Error("Firebase sign-in returned no user.")
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Error("Unsupported credential type returned by Google Sign-In.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled by user", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInOutcome.Error(
                e.localizedMessage ?: "Google Sign-In failed. Check your Google account on this device."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Sign-In failed", e)
            GoogleSignInOutcome.Error(
                e.localizedMessage ?: "Unable to complete sign-in. Please try again."
            )
        }
    }

    /**
     * Clears Credential Manager state and signs out of Firebase Auth safely inside NonCancellable
     * so UI scope cancellation never interrupts cleanup.
     */
    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context.applicationContext)
                    .clearCredentialState(ClearCredentialStateRequest())
            }.onFailure { err ->
                Log.w(TAG, "clearCredentialState warning", err)
            }
            auth.signOut()
        }
    }

    
private fun resolveWebClientId(context: Context): String? {
    return runCatching {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )

        if (resourceId == 0) null
        else context.getString(resourceId)
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

