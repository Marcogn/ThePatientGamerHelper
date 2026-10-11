package com.marcogn.thepatientgamerhelper.ui.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcogn.thepatientgamerhelper.data.update.ApkDownloader
import com.marcogn.thepatientgamerhelper.data.update.ApkInstaller
import com.marcogn.thepatientgamerhelper.data.update.ApkVerifier
import com.marcogn.thepatientgamerhelper.data.update.GitHubReleaseClient
import com.marcogn.thepatientgamerhelper.data.update.UpdateManager
import com.marcogn.thepatientgamerhelper.data.update.UpdatePreferences
import com.marcogn.thepatientgamerhelper.domain.update.ApkRejection
import com.marcogn.thepatientgamerhelper.domain.update.UnusableReleaseReason
import com.marcogn.thepatientgamerhelper.domain.update.UpdateFailure
import com.marcogn.thepatientgamerhelper.domain.update.UpdateFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class UpdateUiTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = UpdatePreferences(context)
    private val installer = ApkInstaller(context)
    private val manager = UpdateManager(
        GitHubReleaseClient(), ApkDownloader(context), ApkVerifier(context), installer, preferences,
    )

    @Test
    fun `every failure maps to a non-blank message`() {
        val failures = listOf<UpdateFailure>(
            UpdateFailure.Network("x"), UpdateFailure.RateLimited, UpdateFailure.Http(500),
            UpdateFailure.InvalidResponse(null), UpdateFailure.Storage, UpdateFailure.SizeMismatch,
            UpdateFailure.ChecksumMismatch, UpdateFailure.InstallFailed(1, null), UpdateFailure.InstallFailed(1, "boom"),
        ) + UnusableReleaseReason.entries.map { UpdateFailure.UnusableRelease(it) } +
            ApkRejection.entries.map { UpdateFailure.VerificationFailed(it) }
        failures.forEach { assertTrue("$it", it.toMessage(context).isNotBlank()) }
    }

    @Test
    fun `http and install detail end up in the message`() {
        assertTrue(UpdateFailure.Http(503).toMessage(context).contains("503"))
        assertTrue(UpdateFailure.InstallFailed(1, "boom").toMessage(context).contains("boom"))
    }

    @Test
    fun `different signature gets its own reason`() {
        val signature = UpdateFailure.VerificationFailed(ApkRejection.DIFFERENT_SIGNATURE).toMessage(context)
        val notNewer = UpdateFailure.VerificationFailed(ApkRejection.NOT_NEWER).toMessage(context)
        assertTrue(signature != notNewer)
    }

    @Test
    fun `frequency defaults to weekly and the selection is persisted`() {
        val viewModel = UpdateViewModel(manager, installer, preferences)
        assertEquals(UpdateFrequency.WEEKLY, viewModel.frequency.value)

        UpdateFrequency.entries.forEach { option ->
            viewModel.onFrequencySelected(option)
            assertEquals(option, viewModel.frequency.value)
            assertEquals(option, preferences.frequency)
            // A new ViewModel (screen reopened, process restarted) reads the stored value back.
            assertEquals(option, UpdateViewModel(manager, installer, preferences).frequency.value)
        }
    }
}
