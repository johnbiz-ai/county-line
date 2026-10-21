package net.johnbiz.countyline

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import net.johnbiz.countyline.location.CountyTrackingService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class EnsureTrackingRunningTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        CountyTrackingService.isRunning = false
    }

    @Test
    fun `starts the service when tracking is on, permissions are ready, and it is not running`() {
        assertTrue(CountyTrackingService.ensureRunning(app, enabled = true, ready = true))
        val started = shadowOf(app).nextStartedService
        assertEquals(CountyTrackingService::class.java.name, started?.component?.className)
    }

    @Test
    fun `does not restart a service that is already running`() {
        CountyTrackingService.isRunning = true
        assertFalse(CountyTrackingService.ensureRunning(app, enabled = true, ready = true))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun `does nothing when tracking is off`() {
        assertFalse(CountyTrackingService.ensureRunning(app, enabled = false, ready = true))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun `does nothing when permissions are missing`() {
        assertFalse(CountyTrackingService.ensureRunning(app, enabled = true, ready = false))
        assertNull(shadowOf(app).nextStartedService)
    }
}
