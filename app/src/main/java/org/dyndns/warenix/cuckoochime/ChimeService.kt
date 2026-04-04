package org.dyndns.warenix.cuckoochime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class ChimeService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chimeCount = intent?.getIntExtra(EXTRA_CHIME_COUNT, 1) ?: 1
        
        // Start foreground immediately to satisfy Android 14+ requirements
        val notification = createNotification(chimeCount)
        startForeground(NOTIFICATION_ID, notification)

        acquireWakeLock()
        
        serviceScope.launch {
            try {
                playChimes(chimeCount)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CuckooChime::WakeLock").apply {
            acquire(10 * 60 * 1000L /* 10 minutes max safety timeout */)
        }
    }

    private suspend fun playChimes(count: Int) {
        repeat(count) { i ->
            playSingleChime()
            if (i < count - 1) {
                delay(1500) // 1.5 second delay between chimes
            }
        }
    }

    private suspend fun playSingleChime() = suspendCancellableCoroutine<Unit> { continuation ->
        mediaPlayer?.release()
        
        // Note: R.raw.cuckoo must exist for this to compile correctly.
        // If it shows an error in the IDE, please ensure res/raw/cuckoo.mp3 is created.
        val mp: MediaPlayer? = try {
            MediaPlayer.create(this as Context, R.raw.cuckoo)
        } catch (e: Exception) {
            null
        }

        if (mp == null) {
            if (continuation.isActive) continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        
        mediaPlayer = mp
        
        mp.setOnCompletionListener { player: MediaPlayer ->
            player.release()
            if (mediaPlayer == player) {
                mediaPlayer = null
            }
            if (continuation.isActive) continuation.resume(Unit)
        }
        
        mp.setOnErrorListener { player: MediaPlayer, _: Int, _: Int ->
            player.release()
            if (mediaPlayer == player) {
                mediaPlayer = null
            }
            if (continuation.isActive) continuation.resume(Unit)
            true
        }
        
        mp.start()
        
        continuation.invokeOnCancellation {
            mp.release()
            if (mediaPlayer == mp) {
                mediaPlayer = null
            }
        }
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

    private fun createNotification(chimeCount: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cuckoo Chime")
            .setContentText("Chiming $chimeCount times...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ChimeServiceChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_CHIME_COUNT = "EXTRA_CHIME_COUNT"
        const val ACTION_PLAY_CHIME = "ACTION_PLAY_CHIME"
    }
}