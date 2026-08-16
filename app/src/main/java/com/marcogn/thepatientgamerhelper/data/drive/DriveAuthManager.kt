package com.marcogn.thepatientgamerhelper.data.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.marcogn.thepatientgamerhelper.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "DriveAuthManager"

/**
 * appDataFolder is per-app private storage, invisible in the Drive UI — this is the only scope
 * the app ever requests. See CLAUDE.md, "Phase 4" section, for why Credential Manager +
 * AuthorizationClient were chosen over the deprecated GoogleSignInClient.
 */
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val OAUTH_PLACEHOLDER = "[TO_COMPLETE]"

class DriveNotConfiguredException :
    Exception("Set DRIVE_OAUTH_WEB_CLIENT_ID in local.properties before using Drive backup")

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
        Log.i(TAG, "signIn: launching Credential Manager (serverClientId configured=${isConfigured()})")
        val credentialManager = CredentialManager.create(context)
        val bottomSheetRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(requireWebClientId())
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            )
            .build()
        val response = try {
            credentialManager.getCredential(context, bottomSheetRequest)
        } catch (e: NoCredentialException) {
            // Per Google's own "Sign in with Google" implementation guide: NoCredentialException
            // ("No credentials available") from the bottom-sheet flow above is exactly the
            // documented case for falling back to the explicit-button flow below (GetGoogleIdOption
            // relies on Play Services' account picker, which some devices/emulators — no Google
            // account added, outdated Play Services — never populate; GetSignInWithGoogleOption
            // triggers a different, more permissive system chooser). Both return the same
            // GoogleIdTokenCredential type, so the parsing below is unchanged either way.
            Log.i(TAG, "signIn: no credential for the bottom-sheet flow, retrying with the button flow")
            val buttonRequest = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(requireWebClientId()).build())
                .build()
            try {
                credentialManager.getCredential(context, buttonRequest)
            } catch (e2: GetCredentialException) {
                Log.w(TAG, "signIn failed (button flow): type=${e2.type} message=${e2.message}", e2)
                throw Exception("Google sign-in failed (${e2.type}): ${e2.message ?: "no detail"}", e2)
            }
        } catch (e: GetCredentialException) {
            // Another common real-world cause of a non-NoCredentialException failure here: no
            // matching "Android" OAuth client (SHA-1 of the signing certificate + applicationId)
            // registered in the same Google Cloud project as the "Web application" client id used
            // above — see CLAUDE.md, "Phase 4" section, "External configuration required". e.type
            // carries the underlying reason (e.g. TYPE_NO_CREDENTIAL) that e.message alone
            // sometimes doesn't make obvious.
            Log.w(TAG, "signIn failed: type=${e.type} message=${e.message}", e)
            throw Exception("Google sign-in failed (${e.type}): ${e.message ?: "no detail"}", e)
        }

        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential from Credential Manager"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).id.also {
            Log.i(TAG, "signIn: succeeded")
        }
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
        return try {
            Identity.getAuthorizationClient(context).authorize(request).awaitTask().toDriveAuthorization().also {
                Log.i(TAG, "authorize: resolved as ${it::class.simpleName}")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "authorize failed: statusCode=${e.statusCode} message=${e.message}", e)
            throw Exception("Drive authorization failed (status ${e.statusCode}): ${e.message ?: "no detail"}", e)
        }
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
