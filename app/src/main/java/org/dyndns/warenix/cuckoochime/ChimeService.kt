package org.dyndns.warenix.cuckoochime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
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
    private var originalAlarmVolume: Int = -1
    private var playJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CHIME) {
            stopChime()
            return START_NOT_STICKY
        }

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

        // Never overlap two chime runs: a second start while playing would
        // double the audio and corrupt the saved alarm volume on restore.
        if (playJob?.isActive == true) {
            Log.d("CuckooChime", "Chime already playing, ignoring new start")
            return START_NOT_STICKY
        }

        acquireWakeLock()
        // Snapshot night mode once: alarm stream + volume override apply
        // to this chime run only. Daytime chimes stay on the media stream
        // so they follow the phone's media volume.
        val nightMode = isNightModeActive()
        boostAlarmVolumeIfNeeded(nightMode)
        
        playJob = serviceScope.launch {
            try {
                playChimes(chimeCount, soundResId, nightMode)
            } finally {
                playJob = null
                restoreAlarmVolume()
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

    private fun stopChime() {
        Log.d("CuckooChime", "Stop chime requested")
        // Cancel only the play job: cancelling the whole scope would leave
        // this service instance unable to play later chimes if reused.
        playJob?.cancel()
        playJob = null
        mediaPlayer?.release()
        mediaPlayer = null
        restoreAlarmVolume()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isNightModeActive(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ChimeReceiver.KEY_NIGHT_MODE, false)
    }

    private fun boostAlarmVolumeIfNeeded(nightMode: Boolean) {
        if (!nightMode) {
            originalAlarmVolume = -1
            return
        }
        // Never overwrite a saved volume: only the outermost boost owns restore.
        if (originalAlarmVolume >= 0) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val percent = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(ChimeReceiver.KEY_NIGHT_VOLUME, 100).coerceIn(0, 100)
            // Ensure at least 1 so night mode is never accidentally silent,
            // unless user explicitly picked 0.
            val targetVolume = if (percent == 0) 0 else ((percent / 100f * maxVolume).toInt().coerceIn(1, maxVolume))
            if (originalAlarmVolume != targetVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
                Log.d("CuckooChime", "Night mode: set alarm volume $originalAlarmVolume -> $targetVolume ($percent%)")
            }
        } catch (e: Exception) {
            Log.e("CuckooChime", "Failed to boost alarm volume", e)
            originalAlarmVolume = -1
        }
    }

    private fun restoreAlarmVolume() {
        if (originalAlarmVolume < 0) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            Log.d("CuckooChime", "Night mode: restored alarm volume to $originalAlarmVolume")
        } catch (e: Exception) {
            Log.e("CuckooChime", "Failed to restore alarm volume", e)
        } finally {
            originalAlarmVolume = -1
        }
    }

    private suspend fun playChimes(count: Int, soundResId: Int, useAlarmStream: Boolean) {
        repeat(count) { i ->
            Log.d("CuckooChime", "Bird show $i")
            sendBirdVisibility(true)
            delay(500) // Minimum time for bird to be out before sound
            playSingleChime(soundResId, useAlarmStream)
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

    private suspend fun playSingleChime(soundResId: Int, useAlarmStream: Boolean) = suspendCancellableCoroutine<Unit> { continuation ->
        mediaPlayer?.release()
        
        val mp: MediaPlayer? = try {
            if (useAlarmStream) {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(
                        this@ChimeService,
                        Uri.parse("android.resource://${packageName}/$soundResId")
                    )
                    prepare()
                }
            } else {
                // Daytime behavior: default media stream, follows phone media volume.
                MediaPlayer.create(this as Context, soundResId)
            }
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
        val stopIntent = Intent(this, ChimeService::class.java).apply {
            action = ACTION_STOP_CHIME
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.chime_service_notification_title))
            .setContentText(getString(R.string.chime_service_notification_text, chimeCount))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.stop_chime),
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        restoreAlarmVolume()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    companion object {
        const val PREFS_NAME = "CuckooChimePrefs"
        const val CHANNEL_ID = "ChimeServiceChannel"
        const val NOTIFICATION_ID = 1
        const val STOP_REQUEST_CODE = 2
        const val EXTRA_CHIME_COUNT = "EXTRA_CHIME_COUNT"
        const val EXTRA_SOUND_RES_ID = "EXTRA_SOUND_RES_ID"
        const val ACTION_PLAY_CHIME = "ACTION_PLAY_CHIME"
        const val ACTION_STOP_CHIME = "ACTION_STOP_CHIME"
        const val ACTION_BIRD_VISIBILITY = "org.dyndns.warenix.cuckoochime.ACTION_BIRD_VISIBILITY"
        const val EXTRA_IS_VISIBLE = "EXTRA_IS_VISIBLE"
    }
}