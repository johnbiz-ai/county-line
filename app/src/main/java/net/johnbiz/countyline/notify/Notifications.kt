package net.johnbiz.countyline.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.johnbiz.countyline.MainActivity
import net.johnbiz.countyline.R
import net.johnbiz.countyline.core.County

object Notifications {
    const val CHANNEL_CROSSINGS = "county_crossings"
    const val CHANNEL_SERVICE = "tracking_service"

    /** Fixed id for the ongoing foreground-service notification. */
    const val SERVICE_NOTIFICATION_ID = 1

    private const val CROSSING_NOTIFICATION_ID = 2

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

    @SuppressLint("MissingPermission") // caller guarantees POST_NOTIFICATIONS on API 33+
    fun postCrossing(context: Context, from: County?, to: County) {
        val text = if (from != null) {
            context.getString(R.string.crossing_text, from.displayName, to.name, to.stateName)
        } else {
            context.getString(R.string.crossing_text_no_prev, to.name, to.stateName)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CROSSINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.crossing_title, to.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(CROSSING_NOTIFICATION_ID, notification)
        }
    }

    private fun contentIntent(context: Context) = android.app.PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
