package net.johnbiz.countyline

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import net.johnbiz.countyline.location.LocationPermissions
import net.johnbiz.countyline.location.TrackingReadiness
import net.johnbiz.countyline.ui.BackgroundLocationDisclosureDialog
import net.johnbiz.countyline.ui.CountyLineTheme
import net.johnbiz.countyline.ui.StatusScreen
import net.johnbiz.countyline.ui.StatusViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StatusViewModel by viewModels()

    /** True while the Play-required background-location disclosure dialog is showing. */
    private var showBackgroundDisclosure by mutableStateOf(false)
    private var backgroundEverRequested = false

    private val foregroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refreshPermissions()
            // If foreground was just granted, immediately walk to the next step.
            if (LocationPermissions.hasForegroundLocation(this)) advancePermissionFlow()
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Denied here usually means the user picked "While using the app".
            viewModel.refreshPermissions(backgroundRationaleDenied = !granted)
            if (granted) advancePermissionFlow()
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountyLineTheme {
                val state by viewModel.uiState.collectAsState()
                StatusScreen(
                    state = state,
                    onGrantClick = ::advancePermissionFlow,
                    onOpenSettings = ::openAppSettings,
                    onToggleTracking = viewModel::setTracking,
                )
                if (showBackgroundDisclosure) {
                    BackgroundLocationDisclosureDialog(
                        onContinue = {
                            showBackgroundDisclosure = false
                            requestBackgroundLocation()
                        },
                        onDismiss = { showBackgroundDisclosure = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    /** Requests exactly the next missing permission in the Play-policy-mandated order. */
    private fun advancePermissionFlow() {
        when (LocationPermissions.readiness(this, backgroundRationaleDenied = false)) {
            TrackingReadiness.NEEDS_FOREGROUND_LOCATION ->
                foregroundLocationLauncher.launch(LocationPermissions.foregroundPermissions)

            // Show the prominent disclosure first; the OS prompt happens only after "Continue".
            TrackingReadiness.NEEDS_BACKGROUND_LOCATION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showBackgroundDisclosure = true
                }

            TrackingReadiness.FOREGROUND_ONLY -> openAppSettings()

            TrackingReadiness.NEEDS_NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

            TrackingReadiness.READY -> viewModel.refreshPermissions()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val canPrompt = ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) || !backgroundEverRequested
        if (canPrompt) {
            backgroundEverRequested = true
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            // User previously chose "While using the app" and won't be asked again in-app.
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }
}
