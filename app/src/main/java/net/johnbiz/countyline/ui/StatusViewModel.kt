package net.johnbiz.countyline.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.data.TrackingPreferences
import net.johnbiz.countyline.location.CountyTrackingService
import net.johnbiz.countyline.location.LocationPermissions
import net.johnbiz.countyline.location.TrackingReadiness

data class StatusUiState(
    val trackingEnabled: Boolean = false,
    val currentCounty: County? = null,
    val readiness: TrackingReadiness = TrackingReadiness.NEEDS_FOREGROUND_LOCATION,
) {
    val isServiceRunning: Boolean get() = trackingEnabled && readiness == TrackingReadiness.READY
}

class StatusViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = TrackingPreferences(app)
    private val readiness = MutableStateFlow(currentReadiness(backgroundRationaleDenied = false))

    val uiState: StateFlow<StatusUiState> =
        combine(prefs.trackingEnabled, prefs.currentCounty, readiness) { enabled, county, ready ->
            StatusUiState(trackingEnabled = enabled, currentCounty = county, readiness = ready)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUiState())

    /** Call from the Activity whenever permission state may have changed. */
    fun refreshPermissions(backgroundRationaleDenied: Boolean = false) {
        readiness.update { currentReadiness(backgroundRationaleDenied) }
    }

    /** User toggled the tracking switch. Assumes permissions are already satisfied when [enabled] is true. */
    fun setTracking(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setTrackingEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled) CountyTrackingService.start(app) else CountyTrackingService.stop(app)
        }
    }

    private fun currentReadiness(backgroundRationaleDenied: Boolean): TrackingReadiness =
        LocationPermissions.readiness(getApplication(), backgroundRationaleDenied)
}
