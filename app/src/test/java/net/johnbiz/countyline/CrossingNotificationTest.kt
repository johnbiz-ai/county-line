package net.johnbiz.countyline

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CountyCatalog
import net.johnbiz.countyline.core.CountyFacts
import net.johnbiz.countyline.core.CountyFactsTable
import net.johnbiz.countyline.notify.CrossingDetails
import net.johnbiz.countyline.notify.CrossingDetailsLoader
import net.johnbiz.countyline.notify.Notifications
import net.johnbiz.countyline.notify.OutlineBitmaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CrossingNotificationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val johnson = County("20091", "Johnson", "20", "Kansas", "KS")
    private val la = County("06037", "Los Angeles", "06", "California", "CA")
    private val square = listOf(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 0.0))

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        Notifications.createChannels(context)
    }

    private fun posted(): Notification =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()

    private fun details(facts: CountyFacts?) = CrossingDetails(
        facts = facts,
        outlineIcon = OutlineBitmaps.render(square, OutlineBitmaps.ICON_PX, OutlineBitmaps.ICON_PX),
        outlinePicture = OutlineBitmaps.render(square, OutlineBitmaps.PICTURE_WIDTH_PX, OutlineBitmaps.PICTURE_HEIGHT_PX),
    )

    private fun contentIntentOf(n: Notification): Intent = shadowOf(n.contentIntent).savedIntents.last()

    @Test
    fun `rich notification has outline, facts, picture style, and things-to-do action`() {
        Notifications.postCrossing(context, from = null, to = johnson, details(CountyFacts("County", 609863, 473.375)))
        val n = posted()

        assertNotNull(n.getLargeIcon())
        assertEquals("android.app.Notification\$BigPictureStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().endsWith("pop. 609,863 · 1,288/sq mi"))

        val action = n.actions.single()
        assertEquals("Things to do", action.title.toString())
        val view = shadowOf(action.actionIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertTrue(view.dataString!!.contains("Johnson%20County%2C%20KS"))
    }

    @Test
    fun `tapping opens the detail screen for that county`() {
        Notifications.postCrossing(context, from = null, to = johnson, details = null)
        val intent = contentIntentOf(posted())
        assertEquals(CountyDetailActivity::class.java.name, intent.component!!.className)
        assertEquals("20091", intent.getStringExtra(CountyDetailActivity.EXTRA_FIPS))
    }

    @Test
    fun `each county keeps its own content intent`() {
        Notifications.postCrossing(context, from = null, to = johnson, details = null)
        val first = posted().contentIntent
        Notifications.postCrossing(context, from = johnson, to = la, details = null)
        val second = posted().contentIntent
        assertEquals("20091", shadowOf(first).savedIntents.last().getStringExtra(CountyDetailActivity.EXTRA_FIPS))
        assertEquals("06037", shadowOf(second).savedIntents.last().getStringExtra(CountyDetailActivity.EXTRA_FIPS))
    }

    @Test
    fun `missing population leaves no gaps in the text`() {
        Notifications.postCrossing(context, from = null, to = johnson, details(CountyFacts("city", null, 6.88)))
        val text = posted().extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertFalse(text, text.contains("null"))
        assertFalse(text, text.contains("pop."))
        assertFalse(text, text.trimEnd().endsWith("·"))
    }

    @Test
    fun `without details the notification is text-only but still useful`() {
        Notifications.postCrossing(context, from = null, to = johnson, details = null)
        val n = posted()
        assertNull(n.getLargeIcon())
        assertEquals("android.app.Notification\$BigTextStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(1, n.actions.size)
    }

    @Test
    fun `outline bitmaps have the requested sizes`() {
        val icon = OutlineBitmaps.render(square, OutlineBitmaps.ICON_PX, OutlineBitmaps.ICON_PX)
        val picture = OutlineBitmaps.render(square, OutlineBitmaps.PICTURE_WIDTH_PX, OutlineBitmaps.PICTURE_HEIGHT_PX)
        assertEquals(256 to 256, icon.width to icon.height)
        assertEquals(1024 to 512, picture.width to picture.height)
    }

    private class FakeCatalog(private val rings: List<DoubleArray>?) : CountyCatalog {
        override fun county(fips: String): County? = null
        override fun outline(fips: String): List<DoubleArray>? = rings
    }

    private fun factsTable(csv: String) = CountyFactsTable.parse(csv.byteInputStream())

    @Test
    fun `loader builds details from facts and outline`() = runTest {
        val loader = CrossingDetailsLoader(
            facts = { factsTable("20091,County,609863,473.375\n") },
            catalog = { FakeCatalog(square) },
        )
        val details = loader.load("20091")!!
        assertEquals(609863, details.facts!!.population)
        assertEquals(256, details.outlineIcon!!.width)
        assertEquals(1024, details.outlinePicture!!.width)
    }

    @Test
    fun `loader returns null instead of throwing when enrichment fails`() = runTest {
        val loader = CrossingDetailsLoader(
            facts = { throw java.io.FileNotFoundException("county-facts.csv") },
            catalog = { FakeCatalog(square) },
        )
        assertNull(loader.load("20091"))
    }

    @Test
    fun `loader returns null when nothing is known`() = runTest {
        val loader = CrossingDetailsLoader(facts = { factsTable("") }, catalog = { FakeCatalog(null) })
        assertNull(loader.load("20091"))
    }
}
