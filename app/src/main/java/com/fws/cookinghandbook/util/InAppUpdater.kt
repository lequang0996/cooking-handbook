package com.fws.cookinghandbook.util

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play In-App Update, FLEXIBLE flow: the update downloads in the background while the user keeps
 * using the app, then the UI offers "Restart" to install it. Create it in onCreate (before STARTED).
 */
class InAppUpdater(private val activity: ComponentActivity) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private var promptedThisSession = false

    private val _downloaded = MutableStateFlow(false)
    val downloaded: StateFlow<Boolean> = _downloaded

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _downloaded.value = true
    }

    fun start() { manager.registerListener(listener) }
    fun stop() { manager.unregisterListener(listener) }

    /** Call from onResume: starts the flexible flow once per session, or surfaces an already-downloaded update. */
    fun check() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _downloaded.value = true
                !promptedThisSession &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    promptedThisSession = true
                    manager.startUpdateFlowForResult(
                        info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                }
            }
        }
    }

    fun completeUpdate() { manager.completeUpdate() }
}
