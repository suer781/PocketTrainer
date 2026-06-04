package com.pockettrainer.training

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TrainingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "training_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.pockettrainer.STOP_TRAINING"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("口袋训练")
            .setContentText("模型训练中...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, TrainingForegroundService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "训练通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "模型训练进度通知" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
