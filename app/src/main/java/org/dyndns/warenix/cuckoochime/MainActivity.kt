package org.dyndns.warenix.cuckoochime

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BlurMaskFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.dyndns.warenix.cuckoochime.ui.theme.CuckooChimeTheme
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private const val PREFS_NAME = "CuckooChimePrefs"
private const val KEY_CHIME_ACTIVE = "chime_active"
private const val KEY_SILENT_START_HOUR = "silent_start_hour"
private const val KEY_SILENT_START_MINUTE = "silent_start_minute"
private const val KEY_SILENT_END_HOUR = "silent_end_hour"
private const val KEY_SILENT_END_MINUTE = "silent_end_minute"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuckooChimeTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2E7D32), Color(0xFF1B1B1B))
                            )
                        )
                ) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        ChimeControlScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Composable
fun ChimeControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isChimeActive by remember { mutableStateOf(getChimeActivePref(context)) }

    var silentStartHour by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_START_HOUR, 22)) }
    var silentStartMinute by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_START_MINUTE, 0)) }
    var silentEndHour by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_END_HOUR, 7)) }
    var silentEndMinute by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_END_MINUTE, 0)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permission required for chime.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions(context, permissionLauncher)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CuckooHouseHeader()

        Spacer(modifier = Modifier.height(24.dp))

        VintageClockFace()

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF5D4037)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isChimeActive) Color(0xFFC62828) else Color(0xFF2E7D32)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isChimeActive) "Stop Chime" else "Start Chime", fontWeight = FontWeight.Bold)
                }
                
                TextButton(onClick = { testChime(context) }) {
                    Text("Test Chime", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SilentHoursSection(
            startHour = silentStartHour,
            startMinute = silentStartMinute,
            endHour = silentEndHour,
            endMinute = silentEndMinute,
            onStartTimeClick = {
                TimePickerDialog(context, { _, h, m ->
                    silentStartHour = h
                    silentStartMinute = m
                    setPrefInt(context, KEY_SILENT_START_HOUR, h)
                    setPrefInt(context, KEY_SILENT_START_MINUTE, m)
                }, silentStartHour, silentStartMinute, false).show()
            },
            onEndTimeClick = {
                TimePickerDialog(context, { _, h, m ->
                    silentEndHour = h
                    silentEndMinute = m
                    setPrefInt(context, KEY_SILENT_END_HOUR, h)
                    setPrefInt(context, KEY_SILENT_END_MINUTE, m)
                }, silentEndHour, silentEndMinute, false).show()
            }
        )
    }
}

@Composable
fun CuckooHouseHeader() {
    val houseShape = GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height * 0.4f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        lineTo(0f, size.height * 0.4f)
        close()
    }

    Box(
        modifier = Modifier
            .size(160.dp, 180.dp)
            .clip(houseShape)
            .background(Color(0xFF3E2723)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Circular window
        Box(
            modifier = Modifier
                .padding(bottom = 40.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B1B1B)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "Cuckoo Bird",
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun VintageClockFace() {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }

    val romanNumerals = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
    val clockColor = Color(0xFFFFF9C4) // Cream
    val goldColor = Color(0xFFFFD700)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(260.dp)
            .drawBehind {
                // Inner shadow effect
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 10.dp.toPx())
                )
            }
            .clip(CircleShape)
            .background(Color(0xFF4E342E)) // Dark Wood
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Draw numerals
            romanNumerals.forEachIndexed { index, text ->
                val angle = (index * 30.0) - 90.0
                val x = center.x + (radius - 25.dp.toPx()) * cos(Math.toRadians(angle)).toFloat()
                val y = center.y + (radius - 25.dp.toPx()) * sin(Math.toRadians(angle)).toFloat()
                
                // Simplified text drawing using native canvas for better control if needed, 
                // but for this example we'll just use points as placeholders or a small circle
                drawCircle(clockColor, 2.dp.toPx(), Offset(x, y))
            }

            // Hands
            val hours = currentTime.get(Calendar.HOUR)
            val minutes = currentTime.get(Calendar.MINUTE)
            val seconds = currentTime.get(Calendar.SECOND)

            // Hour hand
            rotate(degrees = (hours + minutes / 60f) * 30f) {
                drawLine(
                    color = goldColor,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.5f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Minute hand
            rotate(degrees = (minutes + seconds / 60f) * 6f) {
                drawLine(
                    color = goldColor,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.7f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center pin
            drawCircle(goldColor, 5.dp.toPx(), center)
        }
        
        // Overlay Roman Numerals (Text needs to be outside Canvas for Compose ease or use drawText)
        Box(modifier = Modifier.fillMaxSize()) {
           romanNumerals.forEachIndexed { index, text ->
               val angle = (index * 30.0) - 90.0
               Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                   Text(
                       text = text,
                       color = clockColor,
                       fontSize = 14.sp,
                       fontFamily = FontFamily.Serif,
                       modifier = Modifier.offset(
                           x = (100.dp * cos(Math.toRadians(angle)).toFloat()),
                           y = (100.dp * sin(Math.toRadians(angle)).toFloat())
                       )
                   )
               }
           }
        }
    }
}

@Composable
fun SilentHoursSection(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Text(
            text = "Silent Hours",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Shhh... ${formatTime(startHour, startMinute)} to ${formatTime(endHour, endMinute)}",
            style = TextStyle(fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.8f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SilentButton(
                icon = Icons.Default.WbSunny,
                label = "From",
                onClick = onStartTimeClick
            )
            SilentButton(
                icon = Icons.Default.NightlightRound,
                label = "Until",
                onClick = onEndTimeClick
            )
        }
    }
}

@Composable
fun SilentButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontFamily = FontFamily.Serif)
    }
}

private fun checkAndRequestPermissions(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ChimeReceiver::class.java).apply { action = ChimeReceiver.ACTION_CHIME }
    val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
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

private fun getPrefInt(context: Context, key: String, defaultValue: Int): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(key, defaultValue)
}

private fun setPrefInt(context: Context, key: String, value: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(key, value).apply()
}

private fun formatTime(hour: Int, minute: Int): String {
    val ampm = if (hour < 12) "AM" else "PM"
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return if (minute == 0) "$h $ampm" else String.format(Locale.getDefault(), "%d:%02d %s", h, minute, ampm)
}