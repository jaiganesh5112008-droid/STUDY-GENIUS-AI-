package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun unwrapFirestoreError(exception: Throwable): Throwable {
    val causes = generateSequence(exception) { it.cause }
    return causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception
}

fun friendlyFirestoreMessage(exception: Throwable, fallback: String): String {
    val root = unwrapFirestoreError(exception)
    val msg = root.message.orEmpty()
    return when {
        root is FirebaseFirestoreException && root.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Cloud access denied. Please verify you are signed in with your Google account."
        root is FirebaseFirestoreException && root.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Cloud database temporarily unreachable. Changes will sync when reconnected."
        msg.contains("Quota exceeded", ignoreCase = true) ->
            "Cloud storage daily quota reached. Please try again tomorrow."
        msg.isNotBlank() -> msg
        else -> fallback
    }
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val rootError = unwrapFirestoreError(exception)

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
