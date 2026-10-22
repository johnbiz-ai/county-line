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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.johnbiz.countyline.R
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.location.TrackingReadiness

@Composable
fun StatusScreen(
    state: StatusUiState,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTracking: (Boolean) -> Unit,
    onOpenCountyDetails: (County) -> Unit,
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
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

            CurrentCountyCard(state.currentCounty, onOpenCountyDetails)

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
private fun CurrentCountyCard(county: County?, onOpenDetails: (County) -> Unit) {
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.status_current_county), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = county?.let { stringResource(R.string.status_county_value, it.name, it.stateName) }
                        ?: stringResource(R.string.status_county_unknown),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (county != null) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.status_view_county_details))
            }
        }
    }
    if (county != null) {
        Card(onClick = { onOpenDetails(county) }, modifier = Modifier.fillMaxWidth()) { body() }
    } else {
        Card(Modifier.fillMaxWidth()) { body() }
    }
}

@Composable
private fun PermissionCard(
    readiness: TrackingReadiness,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (title, body, action) = when (readiness) {
        TrackingReadiness.NEEDS_FOREGROUND_LOCATION -> Triple(
            R.string.permission_foreground_title,
            R.string.permission_foreground_body,
            R.string.permission_foreground_action,
        )
        TrackingReadiness.NEEDS_BACKGROUND_LOCATION -> Triple(
            R.string.permission_background_title,
            R.string.permission_background_body,
            R.string.permission_background_action,
        )
        TrackingReadiness.FOREGROUND_ONLY -> Triple(
            R.string.permission_foreground_only_title,
            R.string.permission_foreground_only_body,
            R.string.permission_foreground_only_action,
        )
        TrackingReadiness.NEEDS_NOTIFICATIONS -> Triple(
            R.string.permission_notifications_title,
            R.string.permission_notifications_body,
            R.string.permission_notifications_action,
        )
        TrackingReadiness.READY -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            if (readiness == TrackingReadiness.FOREGROUND_ONLY) {
                Button(onClick = onOpenSettings) { Text(stringResource(action)) }
            } else {
                Button(onClick = onGrantClick) { Text(stringResource(action)) }
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
                Text(stringResource(R.string.tracking_title), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = canEnable || enabled,
                )
            }
            Text(
                text = stringResource(
                    when {
                        serviceRunning -> R.string.tracking_active
                        enabled -> R.string.tracking_paused
                        else -> R.string.tracking_off
                    },
                ),
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
            onOpenCountyDetails = {},
        )
    }
}
