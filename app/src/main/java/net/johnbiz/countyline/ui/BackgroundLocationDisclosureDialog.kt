package net.johnbiz.countyline.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.johnbiz.countyline.R

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
        title = { Text(stringResource(R.string.disclosure_title)) },
        text = { Text(stringResource(R.string.disclosure_body)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.disclosure_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.disclosure_not_now)) }
        },
    )
}
