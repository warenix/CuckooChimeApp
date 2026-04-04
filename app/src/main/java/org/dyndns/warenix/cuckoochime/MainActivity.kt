package org.dyndns.warenix.cuckoochime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    var isChimeActive by remember { mutableStateOf(false) }
    
    // Permission launcher for notifications (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
        
        Button(onClick = { 
            isChimeActive = !isChimeActive 
            // Logic to start/stop alarm/service would go here
        }) {
            Text(if (isChimeActive) "Stop Chime" else "Start Chime")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(onClick = {
            val intent = Intent(context, ChimeService::class.java).apply {
                action = ChimeService.ACTION_PLAY_CHIME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }) {
            Text("Test Chime")
        }
    }
}