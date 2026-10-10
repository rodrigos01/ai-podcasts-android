package com.rodrigos01.aipodcasts.data.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rodrigos01.aipodcasts.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that exists only while a long backend request (LLM generation) is in
 * flight. A backgrounded app loses network access (requests then die with DNS errors); a
 * foreground service keeps it. Ref-counted by [acquire] / [release] so concurrent requests share
 * one service. Android defers showing its notification for a few seconds, so quick requests
 * never visibly flash it.
 */
class ApiKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keep_alive_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    // Android 15 caps dataSync foreground services; give up gracefully rather than crash.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "api_keep_alive"
        private const val NOTIFICATION_ID = 2001
        private val active = AtomicInteger(0)

        fun acquire(context: Context) {
            if (active.getAndIncrement() == 0) {
                // Fails if the app is already in the background; nothing more to do then.
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, ApiKeepAliveService::class.java)
                    )
                }
            }
        }

        fun release(context: Context) {
            if (active.decrementAndGet() <= 0) {
                active.set(0)
                runCatching { context.stopService(Intent(context, ApiKeepAliveService::class.java)) }
            }
        }
    }
}
