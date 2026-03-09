package dev.crossflow.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user chose, proceed either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestBatteryOptimizationExemption()

        // Start clipboard sync as foreground service (runs in background automatically)
        // Service will continue syncing even when this activity is closed
        // BootReceiver will auto-start on device reboot
        val svc = Intent(this, ClipboardSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme()) {
                Surface(Modifier.fillMaxSize()) { CrossFlowScreen() }
            }
        }
    }

    @Composable
    private fun dynamicColorScheme(): ColorScheme {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(this)
        } else {
            lightColorScheme()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }

        runCatching { startActivity(intent) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossFlowScreen() {
    val running  = ClipboardSyncService.isRunning.value
    val connectedDevices = ClipboardSyncService.connectedDevices
    val disconnectedDevices = ClipboardSyncService.disconnectedDevices
    val history  = ClipboardSyncService.clipHistory
    val expandedDevices = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("CrossFlow", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (running) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Passive Sharing Status ──────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFE3F2FD)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFF1976D2),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Passive Clipboard Sharing",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF1976D2)
                            )
                            Text(
                                if (running) "Active • Clipboard syncing in background without app open"
                                else "Inactive • Start the service to enable background sync",
                                fontSize = 11.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF0D47A1)
                            )
                        }
                        Icon(
                            if (running) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (running)
                                androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            else
                                androidx.compose.ui.graphics.Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Connected Devices ───────────────────────────────────────
            if (connectedDevices.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        Text("Connected devices",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp)
                        Badge { Text("${connectedDevices.size}") }
                    }
                }
                items(connectedDevices) { deviceName ->
                    val isExpanded = expandedDevices[deviceName] ?: false
                    AndroidDeviceCard(
                        name = deviceName,
                        isConnected = true,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedDevices[deviceName] = !isExpanded }
                    )
                }
            }

            // ── Disconnected Devices ────────────────────────────────────
            if (disconnectedDevices.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Cancel, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = androidx.compose.ui.graphics.Color(0xFFFF9800))
                        Text("Disconnected devices",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp)
                        Badge { Text("${disconnectedDevices.size}") }
                    }
                }
                items(disconnectedDevices) { deviceName ->
                    val isExpanded = expandedDevices[deviceName] ?: false
                    AndroidDeviceCard(
                        name = deviceName,
                        isConnected = false,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedDevices[deviceName] = !isExpanded }
                    )
                }
            }

            if (connectedDevices.isEmpty() && disconnectedDevices.isEmpty()) {
                item { EmptyCard("Scanning for CrossFlow devices on your Wi-Fi…") }
            }

            // ── Clipboard history ────────────────────────────────────────
            if (history.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Clipboard history",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp)
                    }
                }
                items(history.take(10)) { ClipHistoryCard(it) }
            }

            // ── Service controls ────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                Button(
                    onClick = {
                        context.stopService(Intent(context, ClipboardSyncService::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                    )
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Sync Service")
                }
            }
        }
    }
}

@Composable
private fun AndroidDeviceCard(
    name: String,
    isConnected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceInfo = ClipboardSyncService.getDeviceInfo(name)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                androidx.compose.ui.graphics.Color(0xFFE8F5E9)
            else
                androidx.compose.ui.graphics.Color(0xFFFFF3E0)
        )
    ) {
        Column {
            // Device header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Computer,
                    contentDescription = null,
                    tint = if (isConnected)
                        androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    else
                        androidx.compose.ui.graphics.Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name.replace("_", " "),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Text(
                        if (isConnected) "Connected" else "Offline",
                        color = if (isConnected)
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else
                            androidx.compose.ui.graphics.Color(0xFFFF9800),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expandable logs section
            if (isExpanded && deviceInfo != null) {
                Divider(modifier = Modifier.padding(horizontal = 12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    shape = MaterialTheme.shapes.small
                ) {
                    // Create a snapshot copy to avoid concurrent modification
                    val logsCopy = try {
                        deviceInfo.logs.toList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    if (logsCopy.isEmpty()) {
                        Text(
                            "No logs yet",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF999999)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .heightIn(max = 250.dp)
                        ) {
                            logsCopy.take(20).forEach { logEntry ->
                                Text(
                                    logEntry,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = androidx.compose.ui.graphics.Color(0xFF333333),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(running: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Sync else Icons.Default.SyncDisabled,
                contentDescription = null,
                tint = if (running) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = if (running) "Sync active" else "Starting…",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (running) "Listening for clipboard on port ${Protocol.PORT}"
                           else "Waiting for service to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClipHistoryCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(text, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
