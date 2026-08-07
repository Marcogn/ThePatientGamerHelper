package com.marcogn.thepatientgamerhelper.data.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.marcogn.thepatientgamerhelper.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * appDataFolder is per-app private storage, invisible in the Drive UI — this is the only scope
 * the app ever requests. See CLAUDE.md, "Phase 4" section, for why Credential Manager +
 * AuthorizationClient were chosen over the deprecated GoogleSignInClient.
 */
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val OAUTH_PLACEHOLDER = "[TO_COMPLETE]"

class DriveNotConfiguredException :
    Exception("Set google_oauth_web_client_id (res/values/drive_config.xml) before using Drive backup")

sealed interface DriveAuthorization {
    data class Authorized(val accessToken: String) : DriveAuthorization
    data class ConsentRequired(val pendingIntent: PendingIntent) : DriveAuthorization
}

/**
 * Two-step auth: [signIn] (Credential Manager) lets the user pick/confirm a Google account,
 * [authorize] (AuthorizationClient) requests the `drive.appdata` scope for that account. Once
 * granted, [authorize] called again from a plain [Context] (no Activity, no UI) returns a fresh
 * token silently — that's what the periodic [com.marcogn.thepatientgamerhelper.data.backup.BackupWorker]
 * relies on for unattended backups.
 */
@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    /** Whether `google_oauth_web_client_id` has been filled in with a real value. */
    fun isConfigured(): Boolean {
        val id = appContext.getString(R.string.google_oauth_web_client_id)
        return id.isNotBlank() && id != OAUTH_PLACEHOLDER
    }

    private fun requireWebClientId(): String {
        val id = appContext.getString(R.string.google_oauth_web_client_id)
        if (id.isBlank() || id == OAUTH_PLACEHOLDER) throw DriveNotConfiguredException()
        return id
    }

    /** Must be called with an Activity [context] — Credential Manager shows a system bottom sheet. */
    suspend fun signIn(context: Context): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(requireWebClientId())
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(context).getCredential(context, request)

        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential from Credential Manager"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).id
    }

    /**
     * Requests/refreshes the Drive scope. [context] can be an Activity (interactive, may need to
     * launch [DriveAuthorization.ConsentRequired].pendingIntent) or the application context
     * (background — resolves silently or not at all, never launches UI).
     */
    suspend fun authorize(context: Context): DriveAuthorization {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).awaitTask().toDriveAuthorization()
    }

    /** Completes authorization after the caller launched [DriveAuthorization.ConsentRequired].pendingIntent. */
    fun completeAuthorization(context: Context, data: Intent): DriveAuthorization =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).toDriveAuthorization()

    private fun AuthorizationResult.toDriveAuthorization(): DriveAuthorization =
        if (hasResolution()) {
            DriveAuthorization.ConsentRequired(pendingIntent ?: error("Drive authorization: resolution required but no pendingIntent"))
        } else {
            DriveAuthorization.Authorized(accessToken ?: error("Drive authorization succeeded but returned no access token"))
        }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
