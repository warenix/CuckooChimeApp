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
        val isTest = intent.getBooleanExtra("TEST_CHIME", false)
        val appContext = context.applicationContext
        
        if (action == ACTION_CHIME || isTest) {
            val calendar = Calendar.getInstance()
            
            if (isTest || !isSilentTime(appContext, calendar)) {
                // Calendar.HOUR is 0-11. For 12-hour format, if it's 0, it's 12.
                var hour = if (isTest) 1 else calendar.get(Calendar.HOUR)
                if (hour == 0) hour = 12

                val prefs = appContext.getSharedPreferences("CuckooChimePrefs", Context.MODE_PRIVATE)
                val soundResId = prefs.getInt("selected_sound_res_id", R.raw.cuckoo)

                val serviceIntent = Intent(appContext, ChimeService::class.java).apply {
                    putExtra(ChimeService.EXTRA_CHIME_COUNT, hour)
                    putExtra(ChimeService.EXTRA_SOUND_RES_ID, soundResId)
                    this.action = ChimeService.ACTION_PLAY_CHIME
                    setPackage(appContext.packageName)
                }
                ContextCompat.startForegroundService(appContext, serviceIntent)
            }
            
            if (!isTest) {
                // Schedule the next one only if it's not a test
                setNextAlarm(appContext)
            }
        } else if (action == Intent.ACTION_BOOT_COMPLETED) {
            // After boot, ensure the next alarm is scheduled
            setNextAlarm(appContext)
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
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Calculate the start of the next hour
        val nextHour = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(appContext, ChimeReceiver::class.java).apply {
            action = ACTION_CHIME
            setPackage(appContext.packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a PendingIntent that opens MainActivity when the user clicks the alarm icon
        val showIntent = Intent(appContext, MainActivity::class.java).apply {
            setPackage(appContext.packageName)
        }
        val showOperation = PendingIntent.getActivity(
            appContext,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(nextHour.timeInMillis, showOperation)

        // setAlarmClock() is the most precise way to schedule an alarm and helps bypass battery optimizations/batching
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            // Handle cases where exact alarm permission is denied at runtime (if applicable)
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_CHIME = "org.dyndns.warenix.cuckoochime.ACTION_CHIME"
        const val ALARM_REQUEST_CODE = 1001
    }
}