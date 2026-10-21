package net.johnbiz.countyline.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.johnbiz.countyline.location.CountyTrackingService
import net.johnbiz.countyline.location.LocationPermissions
import net.johnbiz.countyline.data.TrackingPreferences

/**
 * Restarts county tracking after a reboot or an app update, but only if the
 * user had it switched on and the required permissions are still granted.
 */
class BootReceiver : BroadcastReceiver() {

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val prefs = TrackingPreferences(appContext)
                val enabled = prefs.isTrackingEnabled()
                val ready = LocationPermissions.hasForegroundLocation(appContext) &&
                    LocationPermissions.hasBackgroundLocation(appContext)
                if (enabled && ready) {
                    startSafely { CountyTrackingService.start(appContext) }
                } else if (enabled) {
                    Log.w(TAG, "Tracking was enabled but permissions are missing; not restarting.")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        /**
         * Runs [start] and reports whether the service could be started. The system can refuse a
         * foreground-service start from the background (for example, Android 15 sends boot
         * broadcasts when a force-stopped app is relaunched): Android 12+ throws
         * `ForegroundServiceStartNotAllowedException` and older versions an
         * `IllegalStateException`. Uncaught, that would crash the app from this receiver.
         */
        internal fun startSafely(start: () -> Unit): Boolean = try {
            start()
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "System refused to start tracking from the background; not restarting.", e)
            false
        }
    }
}
