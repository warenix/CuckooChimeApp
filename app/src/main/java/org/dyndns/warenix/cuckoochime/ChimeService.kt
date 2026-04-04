package org.dyndns.warenix.cuckoochime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChimeService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_CHIME) {
            playChime()
        }
        return START_STICKY
    }

    private fun playChime() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, R.raw.cuckoo)
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
        mediaPlayer?.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chime Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cuckoo Chime Active")
            .setContentText("Listening for the next hour...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ChimeServiceChannel"
        const val ACTION_PLAY_CHIME = "ACTION_PLAY_CHIME"
    }
}