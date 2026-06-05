package org.dyndns.warenix.cuckoochime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.util.Log
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
        val soundResId = intent?.getIntExtra(EXTRA_SOUND_RES_ID, R.raw.cuckoo) ?: R.raw.cuckoo
        
        // Start foreground immediately to satisfy Android 14+ requirements
        val notification = createNotification(chimeCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        
        serviceScope.launch {
            try {
                playChimes(chimeCount, soundResId)
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

    private suspend fun playChimes(count: Int, soundResId: Int) {
        repeat(count) { i ->
            Log.d("CuckooChime", "Bird show $i")
            sendBirdVisibility(true)
            delay(500) // Minimum time for bird to be out before sound
            playSingleChime(soundResId)
            delay(500) // Minimum time for bird to stay out after sound
            Log.d("CuckooChime", "Bird hide $i")
            sendBirdVisibility(false)
            if (i < count - 1) {
                delay(800) // Pause between chimes
            }
        }
        delay(1000) // Final wait for hide animation
    }

    private fun sendBirdVisibility(isVisible: Boolean) {
        val intent = Intent(ACTION_BIRD_VISIBILITY).apply {
            putExtra(EXTRA_IS_VISIBLE, isVisible)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private suspend fun playSingleChime(soundResId: Int) = suspendCancellableCoroutine<Unit> { continuation ->
        mediaPlayer?.release()
        
        val mp: MediaPlayer? = try {
            MediaPlayer.create(this as Context, soundResId)
        } catch (e: Exception) {
            Log.e("CuckooChime", "Failed to create MediaPlayer", e)
            null
        }

        if (mp == null) {
            Log.e("CuckooChime", "MediaPlayer is null")
            if (continuation.isActive) continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        
        mediaPlayer = mp
        
        mp.setOnCompletionListener { player: MediaPlayer ->
            Log.d("CuckooChime", "Chime completed")
            player.release()
            if (mediaPlayer == player) {
                mediaPlayer = null
            }
            if (continuation.isActive) continuation.resume(Unit)
        }
        
        mp.setOnErrorListener { player: MediaPlayer, what: Int, extra: Int ->
            Log.e("CuckooChime", "MediaPlayer error: $what, $extra")
            player.release()
            if (mediaPlayer == player) {
                mediaPlayer = null
            }
            if (continuation.isActive) continuation.resume(Unit)
            true
        }
        
        mp.start()
        Log.d("CuckooChime", "Chime started")
        
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
                getString(R.string.chime_service_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(chimeCount: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.chime_service_notification_title))
            .setContentText(getString(R.string.chime_service_notification_text, chimeCount))
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
        const val EXTRA_SOUND_RES_ID = "EXTRA_SOUND_RES_ID"
        const val ACTION_PLAY_CHIME = "ACTION_PLAY_CHIME"
        const val ACTION_BIRD_VISIBILITY = "org.dyndns.warenix.cuckoochime.ACTION_BIRD_VISIBILITY"
        const val EXTRA_IS_VISIBLE = "EXTRA_IS_VISIBLE"
    }
}