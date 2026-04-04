package org.dyndns.warenix.cuckoochime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ChimeReceiver", "Broadcast received: ${intent.action}")
        // Logic to trigger chime or reschedule will go here
    }
}