package com.example.coinswapmobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.coinswapmobile.MainActivity
import com.example.coinswapmobile.R

object SwapNotificationHelper {
    const val CHANNEL_ID = "coinswap_swap"
    const val NOTIFICATION_ID = 42001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CoinSwap progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing coinswap status"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        ongoing: Boolean,
        showStopAction: Boolean = ongoing,
    ): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (showStopAction) {
            val stop = PendingIntent.getService(
                context,
                1,
                Intent(context, SwapForegroundService::class.java).apply {
                    action = SwapForegroundService.ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Stop", stop)
        }
        return builder.build()
    }

    fun notify(context: Context, title: String, text: String, ongoing: Boolean) {
        ensureChannel(context)
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(NOTIFICATION_ID, build(context, title, text, ongoing))
    }

    fun cancel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.cancel(NOTIFICATION_ID)
    }
}
