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
                    CountyTrackingService.start(appContext)
                } else if (enabled) {
                    Log.w("BootReceiver", "Tracking was enabled but permissions are missing; not restarting.")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
