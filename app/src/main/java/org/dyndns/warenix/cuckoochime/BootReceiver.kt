package org.dyndns.warenix.cuckoochime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences("CuckooChimePrefs", Context.MODE_PRIVATE)
            val isChimeActive = prefs.getBoolean("chime_active", false)
            
            if (isChimeActive) {
                ChimeReceiver.setNextAlarm(appContext)
            }
        }
    }
}