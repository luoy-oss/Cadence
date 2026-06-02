package com.cadence.cadence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.util.Log

class OverlayService : Service() {

    companion object {
        private const val TAG = "CadenceOverlay"
        private const val CHANNEL_ID = "cadence_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set

        // 静态方法供 MainActivity 调用
        fun sendScoreData(data: Map<String, Any>?) {
            Log.d(TAG, "sendScoreData: $data")
        }

        fun sendKeyConfig(config: Map<String, Any>?) {
            Log.d(TAG, "sendKeyConfig: $config")
        }

        fun startGame() {
            Log.d(TAG, "startGame")
        }

        fun pauseGame() {
            Log.d(TAG, "pauseGame")
        }

        fun resumeGame() {
            Log.d(TAG, "resumeGame")
        }

        fun stopGame() {
            Log.d(TAG, "stopGame")
        }
    }

    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "OverlayService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cadence Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Cadence overlay service notification"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Cadence")
                .setContentText("覆盖层运行中")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Cadence")
                .setContentText("覆盖层运行中")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
    }
}
