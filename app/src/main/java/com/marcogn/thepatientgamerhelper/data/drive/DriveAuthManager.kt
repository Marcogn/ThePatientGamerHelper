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
 * the app ever requests. See CLAUDE.md, sezione "Fase 4", per la scelta di Credential Manager +
 * AuthorizationClient al posto di GoogleSignInClient (deprecato).
 */
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val OAUTH_PLACEHOLDER = "[DA_COMPLETARE]"

class DriveNotConfiguredException :
    Exception("Configura google_oauth_web_client_id (res/values/drive_config.xml) prima di usare il backup su Drive")

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
            "Credenziale inattesa da Credential Manager"
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
            DriveAuthorization.ConsentRequired(pendingIntent ?: error("Autorizzazione Drive: risoluzione richiesta ma senza pendingIntent"))
        } else {
            DriveAuthorization.Authorized(accessToken ?: error("Autorizzazione Drive riuscita ma senza access token"))
        }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
