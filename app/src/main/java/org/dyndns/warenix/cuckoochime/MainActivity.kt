package org.dyndns.warenix.cuckoochime

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.dyndns.warenix.cuckoochime.ui.theme.CuckooChimeTheme

private const val PREFS_NAME = "CuckooChimePrefs"
private const val KEY_CHIME_ACTIVE = "chime_active"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuckooChimeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChimeControlScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ChimeControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isChimeActive by remember { 
        mutableStateOf(getChimeActivePref(context)) 
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notification permission is required for the chime to work in the background.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions(context, permissionLauncher)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isChimeActive) "Chime is Active" else "Chime is Off",
            fontSize = 32.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                if (isChimeActive) {
                    stopChime(context)
                    isChimeActive = false
                } else {
                    if (hasRequiredPermissions(context)) {
                        startChime(context)
                        isChimeActive = true
                    } else {
                        checkAndRequestPermissions(context, permissionLauncher)
                    }
                }
            },
            modifier = Modifier.width(200.dp)
        ) {
            Text(if (isChimeActive) "Stop Chime" else "Start Chime")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = {
                testChime(context)
            },
            modifier = Modifier.width(200.dp)
        ) {
            Text("Test Chime")
        }
    }
}

private fun checkAndRequestPermissions(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
    // 1. Post Notifications (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // 2. Exact Alarms (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
        }
    }
}

private fun hasRequiredPermissions(context: Context): Boolean {
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else true

    return notificationsGranted && exactAlarmGranted
}

private fun startChime(context: Context) {
    ChimeReceiver().setNextAlarm(context)
    setChimeActivePref(context, true)
}

private fun stopChime(context: Context) {
    // Cancel Alarm
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ChimeReceiver::class.java).apply {
        action = ChimeReceiver.ACTION_CHIME
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        1001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()

    // Stop Service
    context.stopService(Intent(context, ChimeService::class.java))
    
    setChimeActivePref(context, false)
}

private fun testChime(context: Context) {
    val intent = Intent(context, ChimeService::class.java).apply {
        action = ChimeService.ACTION_PLAY_CHIME
        putExtra(ChimeService.EXTRA_CHIME_COUNT, 3)
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun getChimeActivePref(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_CHIME_ACTIVE, false)
}

private fun setChimeActivePref(context: Context, active: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_CHIME_ACTIVE, active).apply()
}