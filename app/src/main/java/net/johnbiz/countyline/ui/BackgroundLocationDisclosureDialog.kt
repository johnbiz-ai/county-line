package net.johnbiz.countyline.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Google Play "prominent disclosure" for background location. Must be shown, and
 * affirmatively accepted, *before* the `ACCESS_BACKGROUND_LOCATION` runtime
 * prompt. It states what data is accessed, that access happens in the
 * background, and why — independently of the privacy policy.
 *
 * See RELEASING.md › Background location.
 */
@Composable
fun BackgroundLocationDisclosureDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("County Line uses location in the background") },
        text = {
            Text(
                "To alert you when you cross into a new county, County Line checks your " +
                    "device's location even when the app is closed or not in use.\n\n" +
                    "Your location is used only on this device to look up which county you're " +
                    "in. It is never sent anywhere, saved off your device, or shared.\n\n" +
                    "On the next screen, choose \"Allow all the time\" to enable this.",
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
