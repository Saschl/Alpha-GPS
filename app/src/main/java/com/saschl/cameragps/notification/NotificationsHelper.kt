package com.saschl.cameragps.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.saschl.cameragps.MainActivity
import com.saschl.cameragps.R

internal object NotificationsHelper {

    private const val NOTIFICATION_CHANNEL_ID = "general_notification_channel"

    fun createNotificationChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

        // create the notification channel
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.foreground_service_notification),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, notificationId: Int, notification: Notification) {
        val notificationManager =
            context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(notificationId, notification)
    }

    fun buildNotification(context: Context, activeCameras: Int): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(
                context.getString(
                    R.string.foreground_service_notification,
                    activeCameras
                )
            )
            .setContentText(
                context.getString(
                    R.string.foreground_service_notification,
                    activeCameras
                )
            )
            .setSmallIcon(R.drawable.ic_gps_fixed)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Intent(context, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    context,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            })
            .build()
    }

    fun buildNotification(context: Context, title: String, content: String): Notification {
        // TODO separate channels for standby and connected
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_gps_fixed)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Intent(context, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    context,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            })
            .build()
    }
}