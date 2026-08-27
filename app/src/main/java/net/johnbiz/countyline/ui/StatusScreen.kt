package net.johnbiz.countyline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.location.TrackingReadiness

@Composable
fun StatusScreen(
    state: StatusUiState,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTracking: (Boolean) -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("County Line", style = MaterialTheme.typography.headlineMedium)

            CurrentCountyCard(state.currentCounty)

            PermissionCard(
                readiness = state.readiness,
                onGrantClick = onGrantClick,
                onOpenSettings = onOpenSettings,
            )

            TrackingToggle(
                enabled = state.trackingEnabled,
                canEnable = state.readiness == TrackingReadiness.READY,
                serviceRunning = state.isServiceRunning,
                onToggle = onToggleTracking,
            )
        }
    }
}

@Composable
private fun CurrentCountyCard(county: County?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Current county", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = county?.let { "${it.name}\n${it.stateName}" } ?: "Unknown — waiting for a fix",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    readiness: TrackingReadiness,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (readiness == TrackingReadiness.READY) return

    val (title, body, action) = when (readiness) {
        TrackingReadiness.NEEDS_FOREGROUND_LOCATION -> Triple(
            "Location permission needed",
            "County Line needs location access to tell which county you're in.",
            "Grant location",
        )
        TrackingReadiness.NEEDS_BACKGROUND_LOCATION -> Triple(
            "Allow all the time",
            "To detect crossings while the app is closed, set location access to \"Allow all the time\".",
            "Grant background location",
        )
        TrackingReadiness.FOREGROUND_ONLY -> Triple(
            "Background tracking is off",
            "Location is set to \"While using the app\", so crossings won't be detected in the background. " +
                "Open settings and choose \"Allow all the time\".",
            "Open settings",
        )
        TrackingReadiness.NEEDS_NOTIFICATIONS -> Triple(
            "Notifications are blocked",
            "County Line can't alert you about crossings until notifications are allowed.",
            "Allow notifications",
        )
        TrackingReadiness.READY -> Triple("", "", "")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (readiness == TrackingReadiness.FOREGROUND_ONLY) {
                Button(onClick = onOpenSettings) { Text(action) }
            } else {
                Button(onClick = onGrantClick) { Text(action) }
            }
        }
    }
}

@Composable
private fun TrackingToggle(
    enabled: Boolean,
    canEnable: Boolean,
    serviceRunning: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Background tracking", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = canEnable || enabled,
                )
            }
            Text(
                text = when {
                    serviceRunning -> "Active — you'll get a notification each time you cross a county line."
                    enabled -> "Enabled, but paused until permissions are granted."
                    else -> "Off. Turn on to start watching for county crossings."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusScreenPreview() {
    CountyLineTheme {
        StatusScreen(
            state = StatusUiState(
                trackingEnabled = true,
                currentCounty = County("36061", "New York", "36", "New York", "NY"),
                readiness = TrackingReadiness.READY,
            ),
            onGrantClick = {},
            onOpenSettings = {},
            onToggleTracking = {},
        )
    }
}
