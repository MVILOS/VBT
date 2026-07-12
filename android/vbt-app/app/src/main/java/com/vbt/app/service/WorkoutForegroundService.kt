package com.vbt.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vbt.app.MainActivity

/**
 * Foreground service utrzymujący proces (i tym samym singletonowy VbtBleManager
 * z aktywnym połączeniem BLE) przy życiu podczas treningu, gdy ekran jest
 * zgaszony lub aplikacja w tle. Nie robi nic poza notyfikacją "Trening w toku".
 */
class WorkoutForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "workout_active"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_EXERCISE = "exercise"
        private const val EXTRA_REPS = "reps"

        fun start(context: Context, exerciseName: String, repCount: Int) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                putExtra(EXTRA_EXERCISE, exerciseName)
                putExtra(EXTRA_REPS, repCount)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val exerciseName = intent?.getStringExtra(EXTRA_EXERCISE).orEmpty()
        val repCount = intent?.getIntExtra(EXTRA_REPS, 0) ?: 0
        val notification = buildNotification(exerciseName, repCount)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(exerciseName: String, repCount: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildString {
            if (exerciseName.isNotBlank()) append(exerciseName) else append("Trening w toku")
            if (repCount > 0) append(" • $repCount powt.")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trening w toku")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trening w toku",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notyfikacja aktywnej sesji treningowej VBT"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
