package org.dyndns.warenix.cuckoochime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.Calendar

class ChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == ACTION_CHIME) {
            val calendar = Calendar.getInstance()
            
            if (!isSilentTime(context, calendar)) {
                // Calendar.HOUR is 0-11. For 12-hour format, if it's 0, it's 12.
                var hour = calendar.get(Calendar.HOUR)
                if (hour == 0) hour = 12

                val serviceIntent = Intent(context, ChimeService::class.java).apply {
                    putExtra(ChimeService.EXTRA_CHIME_COUNT, hour)
                    this.action = ChimeService.ACTION_PLAY_CHIME
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            
            // Schedule the next one
            setNextAlarm(context)
        } else if (action == Intent.ACTION_BOOT_COMPLETED) {
            // After boot, ensure the next alarm is scheduled
            setNextAlarm(context)
        }
    }

    private fun isSilentTime(context: Context, now: Calendar): Boolean {
        val prefs = context.getSharedPreferences("CuckooChimePrefs", Context.MODE_PRIVATE)
        val startHour = prefs.getInt("silent_start_hour", 22)
        val startMinute = prefs.getInt("silent_start_minute", 0)
        val endHour = prefs.getInt("silent_end_hour", 7)
        val endMinute = prefs.getInt("silent_end_minute", 0)

        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val currentTime = currentHour * 60 + currentMinute
        val startTime = startHour * 60 + startMinute
        val endTime = endHour * 60 + endMinute

        return if (startTime <= endTime) {
            currentTime in startTime until endTime
        } else {
            // Range crosses midnight
            currentTime >= startTime || currentTime < endTime
        }
    }

    fun setNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Calculate the start of the next hour
        val nextHour = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, ChimeReceiver::class.java).apply {
            action = ACTION_CHIME
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 14+ requires SCHEDULE_EXACT_ALARM permission, which we added to manifest.
        // setExactAndAllowWhileIdle ensures it fires even in Doze mode.
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextHour.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Handle cases where exact alarm permission is denied at runtime (if applicable)
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_CHIME = "org.dyndns.warenix.cuckoochime.ACTION_CHIME"
        private const val ALARM_REQUEST_CODE = 1001
    }
}