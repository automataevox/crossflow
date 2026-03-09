package dev.crossflow.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Main Compose window UI — Windows-style with slightly flat, Fluent-inspired design. */
@Composable
fun CrossFlowApp(manager: SyncManager) {
    val running = manager.isRunning.value
    val connectedDevices = manager.connectedDevices
    val disconnectedDevices = manager.disconnectedDevices
    val history = manager.clipHistory
    val expandedDevices = remember { mutableStateMapOf<String, Boolean>() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary       = androidx.compose.ui.graphics.Color(0xFF0067C0),
            secondary     = androidx.compose.ui.graphics.Color(0xFF005A9E),
            surface       = androidx.compose.ui.graphics.Color(0xFFF3F3F3),
            background    = androidx.compose.ui.graphics.Color(0xFFEFEFEF),
            onPrimary     = androidx.compose.ui.graphics.Color.White,
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8E8E8)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Title bar ────────────────────────────────────────────
                Surface(
                    color = androidx.compose.ui.graphics.Color(0xFF0067C0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp))
                        Text("CrossFlow",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        // Status dot
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(
                                    if (running)
                                        androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                    else
                                        androidx.compose.ui.graphics.Color(0xFFFF5722)
                                )
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Connected Devices
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
                            WinDeviceCard(
                                name = deviceName,
                                isConnected = true,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedDevices[deviceName] = !isExpanded },
                                manager = manager
                            )
                        }
                    }

                    // Disconnected Devices
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
                            WinDeviceCard(
                                name = deviceName,
                                isConnected = false,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedDevices[deviceName] = !isExpanded },
                                manager = manager
                            )
                        }
                    }

                    if (connectedDevices.isEmpty() && disconnectedDevices.isEmpty()) {
                        item { WinEmptyCard("Scanning for CrossFlow devices on your network…") }
                    }

                    // History
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
                        items(history.take(10)) { WinClipCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WinDeviceCard(
    name: String,
    isConnected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    manager: SyncManager
) {
    val deviceInfo = manager.getDeviceInfo(name)
    
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isConnected)
            androidx.compose.ui.graphics.Color(0xFFE8F5E9)
        else
            androidx.compose.ui.graphics.Color(0xFFFFF3E0),
        tonalElevation = if (isExpanded) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Device header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
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
                    shape = RoundedCornerShape(4.dp)
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
private fun WinClipCard(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(10.dp),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = androidx.compose.ui.graphics.Color(0xFF333333)
        )
    }
}

@Composable
private fun WinEmptyCard(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color(0xFF666666))
        }
    }
}

