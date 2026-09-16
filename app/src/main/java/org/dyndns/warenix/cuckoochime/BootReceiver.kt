package org.dyndns.warenix.cuckoochime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // BOOT_COMPLETED re-arms after reboot; TIME_SET/TIMEZONE_CHANGED
        // re-anchor the next top-of-hour after clock jumps (otherwise the
        // hourly chain only self-corrects at the next fire).
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences("CuckooChimePrefs", Context.MODE_PRIVATE)
            val isChimeActive = prefs.getBoolean("chime_active", false)
            
            if (isChimeActive) {
                ChimeReceiver.setNextAlarm(appContext)
            }
        }
    }
}