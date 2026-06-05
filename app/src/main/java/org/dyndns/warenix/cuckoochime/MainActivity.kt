package org.dyndns.warenix.cuckoochime

import android.Manifest
import android.util.Log
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.media.MediaPlayer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.dyndns.warenix.cuckoochime.ui.theme.CuckooChimeTheme
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private const val PREFS_NAME = "CuckooChimePrefs"
private const val KEY_CHIME_ACTIVE = "chime_active"
private const val KEY_SELECTED_SOUND_RES_ID = "selected_sound_res_id"
private const val KEY_SILENT_START_HOUR = "silent_start_hour"
private const val KEY_SILENT_START_MINUTE = "silent_start_minute"
private const val KEY_SILENT_END_HOUR = "silent_end_hour"
private const val KEY_SILENT_END_MINUTE = "silent_end_minute"

data class SoundOption(val nameResId: Int, val resId: Int)

val AvailableSounds = listOf(
    SoundOption(R.string.cuckoo_1_classic, R.raw.cuckoo),
    SoundOption(R.string.cuckoo_2_clock, R.raw.cuckoo_clock),
    SoundOption(R.string.ding, R.raw.ding),
    SoundOption(R.string.sweet_bird_chirping, R.raw.sweet_bird_chirping),
    SoundOption(R.string.bird_singing, R.raw.bird_singing)
)

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
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ChimeControlScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChimeControlScreen() {
    val context = LocalContext.current
    var isChimeActive by remember { mutableStateOf(getChimeActivePref(context)) }
    var isBirdVisible by remember { mutableStateOf(false) }

    var silentStartHour by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_START_HOUR, 22)) }
    var silentStartMinute by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_START_MINUTE, 0)) }
    var silentEndHour by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_END_HOUR, 7)) }
    var silentEndMinute by remember { mutableStateOf(getPrefInt(context, KEY_SILENT_END_MINUTE, 0)) }

    var selectedSoundResId by remember { mutableStateOf(getPrefInt(context, KEY_SELECTED_SOUND_RES_ID, R.raw.cuckoo)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.permission_required_for_chime), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions(context, permissionLauncher)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ChimeService.ACTION_BIRD_VISIBILITY) {
                    val visible = intent.getBooleanExtra(ChimeService.EXTRA_IS_VISIBLE, false)
                    Log.d("CuckooChime", "Bird visibility changed: $visible")
                    isBirdVisible = visible
                }
            }
        }
        val filter = IntentFilter(ChimeService.ACTION_BIRD_VISIBILITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Use same flag for older versions if possible, but actually RECEIVER_NOT_EXPORTED 
            // is only available from 33. For older versions, we just register.
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CuckooClockComponent(
                        isBirdVisible = isBirdVisible,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(32.dp))
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ControlCard(
                        isChimeActive = isChimeActive,
                        onChimeToggle = {
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
                        onTestChime = { testChime(context) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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

                    Spacer(modifier = Modifier.height(24.dp))

                    SoundSelectionSection(
                        selectedResId = selectedSoundResId,
                        onSoundSelected = { resId ->
                            selectedSoundResId = resId
                            setPrefInt(context, KEY_SELECTED_SOUND_RES_ID, resId)
                        },
                        onPreviewSound = { resId -> playPreviewSound(context, resId) }
                    )
                }
            }
        } else {
            // Portrait mode: single scrollable column to avoid infinite height measurement issues with weights
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                CuckooClockComponent(
                    isBirdVisible = isBirdVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                ControlCard(
                    isChimeActive = isChimeActive,
                    onChimeToggle = {
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
                    onTestChime = { testChime(context) }
                )

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

                SoundSelectionSection(
                    selectedResId = selectedSoundResId,
                    onSoundSelected = { resId ->
                        selectedSoundResId = resId
                        setPrefInt(context, KEY_SELECTED_SOUND_RES_ID, resId)
                    },
                    onPreviewSound = { resId -> playPreviewSound(context, resId) }
                )
                
                // Bottom spacer for better scrolling
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ControlCard(
    isChimeActive: Boolean,
    onChimeToggle: () -> Unit,
    onTestChime: () -> Unit
) {
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
                onClick = onChimeToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isChimeActive) Color(0xFFC62828) else Color(0xFF2E7D32)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isChimeActive) stringResource(R.string.stop_chime) else stringResource(R.string.start_chime), fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onTestChime) {
                Text(stringResource(R.string.test_chime), color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun CuckooClockComponent(isBirdVisible: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(0.7f), // Taller aspect ratio for the house
        contentAlignment = Alignment.Center
    ) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight
        
        // Calculate relative sizes and offsets based on the container size
        // These ratios are tuned to the clock_body.png artwork
        val clockFaceSize = maxWidth * 0.65f
        val clockFaceYOffset = maxHeight * 0.12f
        val birdSize = maxWidth * 0.15f
        val birdYOffset = maxHeight * 0.05f

        // 1. Clock Body (Bottom Layer)
        Image(
            painter = painterResource(id = R.drawable.clock_body),
            contentDescription = stringResource(R.string.clock_body_desc),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 2. Cuckoo Bird (Inside upper window)
        Box(
            modifier = Modifier
                .size(birdSize * 2f, birdSize * 1.5f)
                .align(Alignment.TopCenter)
                .offset(y = birdYOffset),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isBirdVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.cuckoo_bird),
                    contentDescription = stringResource(R.string.cuckoo_bird_desc),
                    modifier = Modifier.size(birdSize),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 3. Clock Face (Centered in the large circular hole)
        VintageClockFace(
            modifier = Modifier
                .size(clockFaceSize)
                .offset(y = clockFaceYOffset)
        )
    }
}

@Composable
fun VintageClockFace(modifier: Modifier = Modifier) {
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

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF3E2723).copy(alpha = 0.2f)) // Lighter background
    ) {
        val faceRadius = maxWidth / 2
        val numeralRadius = faceRadius * 0.75f
        val fontSize = (maxWidth.value / 16).sp

        Canvas(modifier = Modifier.fillMaxSize().padding(faceRadius * 0.1f)) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Hands
            val hours = currentTime.get(Calendar.HOUR)
            val minutes = currentTime.get(Calendar.MINUTE)
            val seconds = currentTime.get(Calendar.SECOND)

            // Hour hand
            rotate(degrees = (hours + minutes / 60f) * 30f) {
                drawLine(
                    color = goldColor,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.55f),
                    strokeWidth = radius * 0.05f,
                    cap = StrokeCap.Round
                )
            }

            // Minute hand
            rotate(degrees = (minutes + seconds / 60f) * 6f) {
                drawLine(
                    color = goldColor,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.8f),
                    strokeWidth = radius * 0.03f,
                    cap = StrokeCap.Round
                )
            }

            // Center pin
            drawCircle(goldColor, radius * 0.05f, center)
        }
        
        // Overlay Roman Numerals
        Box(modifier = Modifier.fillMaxSize()) {
           romanNumerals.forEachIndexed { index, text ->
               val angle = (index * 30.0) - 90.0
               Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                   Text(
                       text = text,
                       color = clockColor,
                       fontSize = fontSize,
                       fontFamily = FontFamily.Serif,
                       fontWeight = FontWeight.Bold,
                       modifier = Modifier.offset(
                           x = (numeralRadius * cos(Math.toRadians(angle)).toFloat()),
                           y = (numeralRadius * sin(Math.toRadians(angle)).toFloat())
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.silent_hours),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                stringResource(R.string.silent_hours_desc, formatTime(startHour, startMinute), formatTime(endHour, endMinute)),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onStartTimeClick,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.from), color = Color.White)
                }
                
                OutlinedButton(
                    onClick = onEndTimeClick,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.NightlightRound, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.until), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SoundSelectionSection(
    selectedResId: Int,
    onSoundSelected: (Int) -> Unit,
    onPreviewSound: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.choose_cuckoo_sound),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvailableSounds.forEach { sound ->
                    SoundOptionRow(
                        label = stringResource(sound.nameResId),
                        isSelected = selectedResId == sound.resId,
                        onClick = { onSoundSelected(sound.resId) },
                        onPreviewClick = { onPreviewSound(sound.resId) }
                    )
                }
            }
        }
    }
}

@Composable
fun SoundOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFF2E7D32).copy(alpha = 0.3f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF2E7D32) else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviewClick) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.preview_sound_desc),
                    tint = Color.White
                )
            }
            
            Text(
                label, 
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            
            RadioButton(
                selected = isSelected,
                onClick = null, // Surface handles click
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2E7D32))
            )
        }
    }
}

private var previewMediaPlayer: MediaPlayer? = null

fun playPreviewSound(context: Context, resId: Int) {
    try {
        previewMediaPlayer?.stop()
        previewMediaPlayer?.release()
        
        previewMediaPlayer = MediaPlayer.create(context, resId).apply {
            setOnCompletionListener { 
                it.release()
                if (previewMediaPlayer == it) previewMediaPlayer = null
            }
            start()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun formatTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}

fun getChimeActivePref(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_CHIME_ACTIVE, false)
}

fun setChimeActivePref(context: Context, active: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_CHIME_ACTIVE, active)
        .apply()
}

fun getPrefInt(context: Context, key: String, defaultValue: Int): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(key, defaultValue)
}

fun setPrefInt(context: Context, key: String, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(key, value)
        .apply()
}

fun startChime(context: Context) {
    val appContext = context.applicationContext
    setChimeActivePref(appContext, true)
    ChimeReceiver.setNextAlarm(appContext)
}

fun stopChime(context: Context) {
    val appContext = context.applicationContext
    setChimeActivePref(appContext, false)
    val intent = Intent(appContext, ChimeReceiver::class.java).apply {
        action = ChimeReceiver.ACTION_CHIME
        setPackage(appContext.packageName)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        appContext, ChimeReceiver.ALARM_REQUEST_CODE, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent)
}

fun testChime(context: Context) {
    val appContext = context.applicationContext
    val intent = Intent(appContext, ChimeReceiver::class.java).apply {
        action = ChimeReceiver.ACTION_CHIME
        putExtra("TEST_CHIME", true)
        setPackage(appContext.packageName)
    }
    appContext.sendBroadcast(intent)
}

fun hasRequiredPermissions(context: Context): Boolean {
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    
    val alarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
    
    return notificationPermission && alarmPermission
}

fun checkAndRequestPermissions(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
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
