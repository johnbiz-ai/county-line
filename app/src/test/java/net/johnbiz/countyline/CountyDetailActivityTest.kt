package net.johnbiz.countyline

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.notify.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CountyDetailActivityTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val johnson = County("20091", "Johnson", "20", "Kansas", "KS")
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        Notifications.createChannels(app)
        Notifications.postCrossing(app, from = null, to = johnson, details = null)
    }

    private fun actionIntent(): Intent =
        shadowOf(notifications.allNotifications.single().actions.single().actionIntent).savedIntents.last()

    @Test
    fun `things to do dismisses the crossing alert and opens the link`() {
        val intent = actionIntent()
        val activity = Robolectric.buildActivity(CountyDetailActivity::class.java, intent).setup().get()

        assertTrue(notifications.allNotifications.isEmpty())
        val view = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(intent.getStringExtra(CountyDetailActivity.EXTRA_OPEN_URL), view.dataString)
    }

    @Test
    fun `opening the detail screen directly opens no link`() {
        val activity = Robolectric.buildActivity(
            CountyDetailActivity::class.java,
            CountyDetailActivity.intent(app, johnson.fips),
        ).setup().get()
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
