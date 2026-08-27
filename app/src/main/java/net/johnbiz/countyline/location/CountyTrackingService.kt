package net.johnbiz.countyline.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.johnbiz.countyline.county.CountyRepository
import net.johnbiz.countyline.core.CrossingDetector
import net.johnbiz.countyline.data.TrackingPreferences
import net.johnbiz.countyline.notify.Notifications

/**
 * Foreground service that keeps a low-power location subscription alive and
 * turns each fix into a county-crossing check.
 *
 * A foreground service (not WorkManager) is used deliberately: WorkManager's
 * minimum periodic interval is 15 minutes with no timeliness guarantee, and
 * background location delivery to deferred work is heavily throttled. A
 * `location`-typed foreground service is the supported way to get regular
 * background fixes for a "notify me promptly when I cross a line" use case.
 */
class CountyTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handleMutex = Mutex()

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var prefs: TrackingPreferences
    private lateinit var counties: CountyRepository
    private val detector = CrossingDetector()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch { handleLocation(location) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = TrackingPreferences(applicationContext)
        counties = CountyRepository.get(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        startInForeground()

        if (!LocationPermissions.hasForegroundLocation(this)) {
            Log.w(TAG, "Started without location permission; stopping.")
            stopTracking()
            return START_NOT_STICKY
        }

        // Warm the dataset and post an accurate ongoing notification.
        scope.launch {
            counties.resolver()
            updateServiceNotification()
        }
        requestLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = Notifications.serviceNotification(this, currentCounty = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(Notifications.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    @Suppress("MissingPermission") // guarded by hasForegroundLocation() in onStartCommand
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_M)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private suspend fun handleLocation(location: Location) = handleMutex.withLock {
        val resolver = counties.resolver()
        val resolved = resolver.resolve(location.latitude, location.longitude)

        val state = prefs.loadCrossingState()
        val result = detector.update(state, resolved)
        prefs.saveCrossingState(result.state)

        if (result.crossed) {
            Notifications.postCrossing(this, from = result.crossedFrom, to = result.crossedInto!!)
        }
        updateServiceNotification()
    }

    private suspend fun updateServiceNotification() {
        val current = prefs.loadCrossingState().current
        val manager = ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
        manager?.notify(
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.serviceNotification(this, current),
        )
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "CountyTracking"
        private const val ACTION_STOP = "net.johnbiz.countyline.action.STOP"

        /** Target time between fixes. ~2 min balances promptness against battery. */
        private const val INTERVAL_MS = 2 * 60 * 1000L
        private const val FASTEST_INTERVAL_MS = 60 * 1000L

        /** Skip updates while the user has moved less than this — counties are big. */
        private const val MIN_DISPLACEMENT_M = 250f

        fun start(context: Context) {
            val intent = Intent(context, CountyTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CountyTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
