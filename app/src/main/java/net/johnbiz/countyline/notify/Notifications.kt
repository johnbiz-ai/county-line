package net.johnbiz.countyline.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import net.johnbiz.countyline.CountyDetailActivity
import net.johnbiz.countyline.MainActivity
import net.johnbiz.countyline.R
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CountyFactsFormat
import net.johnbiz.countyline.county.CountyLinks
import java.util.Locale

object Notifications {
    const val CHANNEL_CROSSINGS = "county_crossings"
    const val CHANNEL_SERVICE = "tracking_service"

    /** Fixed id for the ongoing foreground-service notification. */
    const val SERVICE_NOTIFICATION_ID = 1

    private const val CROSSING_NOTIFICATION_ID = 2

    private const val ACTION_THINGS_TO_DO = "net.johnbiz.countyline.action.THINGS_TO_DO"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val crossings = NotificationChannel(
            CHANNEL_CROSSINGS,
            context.getString(R.string.channel_crossings_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_crossings_desc) }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(crossings, service))
    }

    /** The persistent notification the foreground service must show while tracking. */
    fun serviceNotification(context: Context, currentCounty: County?) =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(
                currentCounty?.let { context.getString(R.string.service_notification_text, it.displayName) }
                    ?: context.getString(R.string.service_notification_text_unknown),
            )
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /**
     * Posts the crossing alert. With [details], it shows the county outline and facts; without,
     * it falls back to plain text. Either way, tapping opens the county's detail screen and the
     * "Things to do" action opens Google Maps from it, dismissing the alert.
     */
    @SuppressLint("MissingPermission") // caller guarantees POST_NOTIFICATIONS on API 33+
    fun postCrossing(context: Context, from: County?, to: County, details: CrossingDetails? = null) {
        val base = if (from != null) {
            context.getString(R.string.crossing_text, from.displayName, to.name, to.stateName)
        } else {
            context.getString(R.string.crossing_text_no_prev, to.name, to.stateName)
        }
        val summary = details?.facts?.let { CountyFactsFormat.summary(it, Locale.getDefault()) }
        val text = if (summary != null) "$base · $summary" else base

        val builder = NotificationCompat.Builder(context, CHANNEL_CROSSINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.crossing_title, to.name))
            .setContentText(text)
            .setContentIntent(detailIntent(context, to.fips))
            .addAction(0, context.getString(R.string.action_things_to_do), thingsToDoIntent(context, to, details))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        details?.outlineIcon?.let { builder.setLargeIcon(it) }
        val picture = details?.outlinePicture
        if (picture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(text),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(CROSSING_NOTIFICATION_ID, builder.build())
        }
    }

    /** Dismisses the crossing alert; action taps don't auto-cancel it. */
    fun cancelCrossing(context: Context) = NotificationManagerCompat.from(context).cancel(CROSSING_NOTIFICATION_ID)

    /** Back from the detail screen returns to the status screen. Request code keyed by FIPS so extras never collide. */
    private fun detailIntent(context: Context, fips: String): PendingIntent =
        backStackIntent(context, CountyDetailActivity.intent(context, fips), fips)

    /**
     * Opens Maps via the detail screen rather than directly, so the screen can dismiss the alert
     * (a direct `ACTION_VIEW` can't) and back from Maps lands on the county's details.
     */
    private fun thingsToDoIntent(context: Context, county: County, details: CrossingDetails?): PendingIntent =
        backStackIntent(
            context,
            CountyDetailActivity.intent(context, county.fips)
                .setAction(ACTION_THINGS_TO_DO) // distinct from the content intent, so extras don't collide
                .putExtra(CountyDetailActivity.EXTRA_OPEN_URL, CountyLinks.thingsToDo(county, details?.facts?.kind)),
            county.fips,
        )

    private fun backStackIntent(context: Context, intent: Intent, fips: String): PendingIntent =
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(fips.toInt(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!

    private fun contentIntent(context: Context) = android.app.PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
