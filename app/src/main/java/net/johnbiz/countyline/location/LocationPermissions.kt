package net.johnbiz.countyline.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Snapshot of the location/notification permission state relevant to background tracking. */
enum class TrackingReadiness {
    /** No foreground location permission yet. */
    NEEDS_FOREGROUND_LOCATION,

    /** Foreground location granted, but "Allow all the time" (background) is still needed. */
    NEEDS_BACKGROUND_LOCATION,

    /** Foreground granted but user chose "While using the app" — background tracking cannot work. */
    FOREGROUND_ONLY,

    /** Android 13+ only: everything else is granted but notifications are blocked. */
    NEEDS_NOTIFICATIONS,

    /** Fully ready for background county tracking. */
    READY,
}

object LocationPermissions {

    val foregroundPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun hasForegroundLocation(context: Context): Boolean =
        context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.isGranted(Manifest.permission.POST_NOTIFICATIONS)

    /**
     * @param backgroundRationaleDenied true if the OS says we should no longer ask for background
     * location (typically because the user picked "While using the app" and dismissed the follow-up).
     */
    fun readiness(context: Context, backgroundRationaleDenied: Boolean): TrackingReadiness = when {
        !hasForegroundLocation(context) -> TrackingReadiness.NEEDS_FOREGROUND_LOCATION
        !hasBackgroundLocation(context) && backgroundRationaleDenied -> TrackingReadiness.FOREGROUND_ONLY
        !hasBackgroundLocation(context) -> TrackingReadiness.NEEDS_BACKGROUND_LOCATION
        !hasNotificationPermission(context) -> TrackingReadiness.NEEDS_NOTIFICATIONS
        else -> TrackingReadiness.READY
    }

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
