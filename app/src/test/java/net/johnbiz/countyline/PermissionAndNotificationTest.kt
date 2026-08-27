package net.johnbiz.countyline

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import net.johnbiz.countyline.location.LocationPermissions
import net.johnbiz.countyline.location.TrackingReadiness
import net.johnbiz.countyline.notify.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PermissionAndNotificationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `readiness reports missing foreground location by default`() {
        assertEquals(
            TrackingReadiness.NEEDS_FOREGROUND_LOCATION,
            LocationPermissions.readiness(context, backgroundRationaleDenied = false),
        )
    }

    @Test
    fun `foreground-only is surfaced when background rationale is exhausted`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        assertEquals(
            TrackingReadiness.FOREGROUND_ONLY,
            LocationPermissions.readiness(context, backgroundRationaleDenied = true),
        )
    }

    @Test
    fun `notification channels are registered`() {
        Notifications.createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(Notifications.CHANNEL_CROSSINGS))
        assertNotNull(manager.getNotificationChannel(Notifications.CHANNEL_SERVICE))
    }

    @Test
    fun `crossing notification builds without a previous county`() {
        Notifications.createChannels(context)
        val to = net.johnbiz.countyline.core.County("36061", "New York", "36", "New York", "NY")
        Notifications.postCrossing(context, from = null, to = to)
        // No exception == pass; Robolectric has notifications enabled by default.
    }
}
