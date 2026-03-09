package dev.crossflow.android

import android.app.*
import android.content.*
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Tracks state and logs for a connected device. */
data class DeviceInfo(
    val name: String,
    val logs: MutableList<String> = mutableListOf(),
    var lastSeen: LocalDateTime = LocalDateTime.now()
) {
    fun addLog(message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        logs.add(0, "[$timestamp] $message")
        if (logs.size > 50) logs.removeAt(logs.lastIndex)
    }
}

class ClipboardSyncService : Service() {

    // ── shared observable state (read from MainActivity) ──────────────────────
    companion object {
        val isRunning   = mutableStateOf(false)
        val deviceList  = mutableStateListOf<String>()
        val connectedDevices = mutableStateListOf<String>()
        val disconnectedDevices = mutableStateListOf<String>()
        val clipHistory = mutableStateListOf<String>()
        val deviceInfo = mutableMapOf<String, DeviceInfo>()
        
        private var instance: ClipboardSyncService? = null
        
        private const val NOTIF_ID = 1
        const val ACTION_SYNC_NOW = "dev.crossflow.android.action.SYNC_NOW"
        const val ACTION_SYNC_TEXT = "dev.crossflow.android.action.SYNC_TEXT"
        const val EXTRA_SYNC_TEXT = "dev.crossflow.android.extra.SYNC_TEXT"

        fun addManualPeer(hostName: String, ipAddress: String) {
            Log.d("ClipboardSyncService", "addManualPeer called: $hostName @ $ipAddress, instance=$instance")
            instance?.addPeerInternal(hostName, ipAddress)
        }
        
        fun getDeviceInfo(name: String): DeviceInfo? = deviceInfo[name]
    }

    private val TAG = "ClipboardSyncService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var nsdHelper: NsdHelper
    private lateinit var clipManager: ClipboardManager
    private lateinit var tcpServer: TcpServer
    private val peers = mutableMapOf<String, Pair<InetSocketAddress, String>>()
    
    private val deviceName: String by lazy {
        (Build.MANUFACTURER + "_" + Build.MODEL)
            .replace(" ", "_").take(32)
    }
    private var lastSentContent = ""
    private var lastPolledClipboard = ""  // Track previous clipboard state for polling

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { !it.isLoopback && it.isUp }
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.toString().contains(".") }
                .firstOrNull()
                ?.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
            null
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        try {
            Log.d(TAG, "🟢 onCreate() - Initializing ClipboardSyncService for persistent background sync...")
            Log.d(TAG, "Building notification...")
            val notification = buildNotification()
            Log.d(TAG, "Notification built successfully, calling startForeground()...")
            startForeground(NOTIF_ID, notification)
            Log.d(TAG, "✓ Foreground service started - service will run in background")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Failed to start foreground service: ${e.message}", e)
            stopSelf()
            return
        }

        try {
            clipManager = getSystemService(ClipboardManager::class.java)
            nsdHelper   = NsdHelper(this, scope)
            tcpServer   = TcpServer { msg -> handleIncomingClip(msg) }

            // TCP server
            Log.d(TAG, "Starting TCP server on port ${Protocol.PORT}...")
            scope.launch { tcpServer.start(scope) }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during initialization: ${e.message}", e)
            stopSelf()
            return
        }

        // NSD register + discover
        Log.d(TAG, "Starting NSD discovery as $deviceName...")
        nsdHelper.register(deviceName)
        nsdHelper.startDiscovery(deviceName)

        // Consume resolved peers
        scope.launch {
            for (info in nsdHelper.resolvedPeers) addPeer(info)
        }
        // Consume lost peers
        scope.launch {
            for (name in nsdHelper.lostPeers) removePeer(name)
        }

        // Clipboard listener (sometimes doesn't fire on some devices)
        clipManager.addPrimaryClipChangedListener {
            val text = clipManager.primaryClip
                ?.getItemAt(0)?.text?.toString() ?: return@addPrimaryClipChangedListener
            Log.d(TAG, "📋 LISTENER: Clipboard changed to '${text.take(30)}...' (length=${text.length})")
            if (text.isNotBlank() && text != lastSentContent) {
                Log.d(TAG, "✓ LISTENER: Broadcasting new content to peers")
                lastSentContent = text
                lastPolledClipboard = text  // Also update polling tracker
                addToHistory(text)
                scope.launch { 
                    Log.d(TAG, "📤 LISTENER: Starting async broadcast to peers...")
                    broadcastClipboard(text)
                }
            } else {
                Log.d(TAG, "⊘ LISTENER: Skipped (blank=${text.isBlank()}, isDupe=${text == lastSentContent})")
            }
        }

        // Clipboard polling (fallback for devices where listener doesn't work)
        scope.launch {
            Log.d(TAG, "🔄 Background clipboard polling STARTED (1s interval)")
            while (isActive) {
                try {
                    delay(1000) // Check every 1 second for faster detection
                    val text = clipManager.primaryClip
                        ?.getItemAt(0)?.text?.toString() ?: ""
                    
                    // Log every poll for debugging
                    if (text.isNotBlank() && text != lastPolledClipboard) {
                        Log.d(TAG, "📋 Clipboard CHANGED from:'${lastPolledClipboard.take(20)}' to:'${text.take(20)}...'")
                        lastPolledClipboard = text
                        
                        if (text != lastSentContent) {  // Don't echo our own broadcasts
                            Log.d(TAG, "✓ Broadcasting new clipboard in background")
                            lastSentContent = text
                            addToHistory(text)
                            broadcastClipboard(text)
                        } else {
                            Log.d(TAG, "Skipped (already sent by us)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Clipboard poll error: ${e.message}")
                }
            }
            Log.d(TAG, "⚠️ Background clipboard polling STOPPED")
        }

        // Background peer re-announcement (ensures Windows can find us)
        scope.launch {
            Log.d(TAG, "🔄 Background peer re-announcement STARTED (10s interval)")
            while (isActive) {
                try {
                    delay(10_000)  // Re-announce every 10 seconds
                    val knownPeers = peers.toMap()
                    Log.d(TAG, "Re-announcing to ${knownPeers.size} known peers")
                    knownPeers.forEach { (name, pair) ->
                        val remoteIp = pair.first.hostName
                        try {
                            withTimeout(2_000) {
                                Socket().use { socket ->
                                    socket.connect(pair.first, 2000)
                                    val announcement = Protocol.encode(
                                        ClipMessage(
                                            type = "peer_announce",
                                            content = "${getLocalIpAddress()}:${Protocol.PORT}",
                                            source = deviceName
                                        )
                                    )
                                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                                        writer.println(announcement)
                                        writer.flush()
                                    }
                                }
                                Log.d(TAG, "✓ Re-announced to $name at $remoteIp")
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Re-announce to $name failed: ${e.javaClass.simpleName}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Peer re-announcement error: ${e.message}")
                }
            }
            Log.d(TAG, "⚠️ Background peer re-announcement STOPPED")
        }

        // Continuous NSD discovery restart (catches devices that come online)
        scope.launch {
            Log.d(TAG, "🔄 Starting continuous NSD re-discovery (every 30s)...")
            while (isActive) {
                try {
                    delay(30_000)  // Re-start discovery every 30 seconds
                    Log.d(TAG, "🔎 Re-starting NSD discovery to catch new devices...")
                    nsdHelper.startDiscovery(deviceName)
                    Log.d(TAG, "✓ NSD discovery re-started")
                } catch (e: Exception) {
                    Log.d(TAG, "NSD re-discovery error: ${e.message}")
                }
            }
        }

        isRunning.value = true
        Log.d(TAG, "Service started as $deviceName - continuous background sync active")
    }

    override fun onDestroy() {
        Log.d(TAG, "⚠️ onDestroy called - cleaning up service...")
        scope.cancel()
        tcpServer.stop()
        nsdHelper.tearDown()
        isRunning.value = false
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        Log.d(TAG, "✓ Service destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed by system/user - requesting service restart")

        val restartIntent = Intent(applicationContext, ClipboardSyncService::class.java)
        val restartPendingIntent = PendingIntent.getService(
            applicationContext,
            2,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(AlarmManager::class.java)
        alarmManager?.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1_000,
            restartPendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_NOW -> {
                Log.d(TAG, "Manual action received: SYNC_NOW")
                scope.launch {
                    val text = clipManager.primaryClip
                        ?.getItemAt(0)?.text?.toString()
                        ?.trim()
                        .orEmpty()

                    if (text.isBlank()) {
                        Log.d(TAG, "SYNC_NOW skipped: clipboard is empty")
                        return@launch
                    }

                    syncOutgoingText(text, "manual clipboard sync")
                }
            }
            ACTION_SYNC_TEXT -> {
                val sharedText = intent.getStringExtra(EXTRA_SYNC_TEXT)?.trim().orEmpty()
                Log.d(TAG, "Manual action received: SYNC_TEXT (${sharedText.length} chars)")
                scope.launch {
                    if (sharedText.isBlank()) {
                        Log.d(TAG, "SYNC_TEXT skipped: no shared text")
                        return@launch
                    }

                    syncOutgoingText(sharedText, "shared text")
                }
            }
        }

        Log.d(TAG, "onStartCommand called (service already initialized or being restarted)")
        // START_STICKY: If service is killed, OS will restart it with null intent
        return START_STICKY
    }

    // ── Peer management ───────────────────────────────────────────────────────

    private fun addPeerInternal(hostName: String, ipAddress: String) {
        try {
            val addr = InetSocketAddress(ipAddress, 35647)
            peers[hostName] = Pair(addr, hostName)
            if (hostName !in deviceList) {
                deviceList.add(hostName)
                if (hostName !in deviceInfo) deviceInfo[hostName] = DeviceInfo(hostName)
                deviceInfo[hostName]?.lastSeen = LocalDateTime.now()
            }
            updateDeviceStatus(hostName)
            Log.d(TAG, "✓ Manual peer added: $hostName @ $ipAddress (peers count: ${peers.size})")
            
            // Send peer announcement after adding peer
            scope.launch { sendPeerAnnouncement(ipAddress) }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to add manual peer: ${e.message}")
        }
    }

    private fun addPeer(info: NsdServiceInfo) {
        val host = info.host ?: return
        val addr = InetSocketAddress(host, info.port)
        peers[info.serviceName] = Pair(addr, info.serviceName)
        if (info.serviceName !in deviceList) {
            deviceList.add(info.serviceName)
            if (info.serviceName !in deviceInfo) deviceInfo[info.serviceName] = DeviceInfo(info.serviceName)
            deviceInfo[info.serviceName]?.lastSeen = LocalDateTime.now()
        }
        updateDeviceStatus(info.serviceName)
        Log.d(TAG, "Peer added: ${info.serviceName} @ $addr")
        
        // Send peer announcement after adding peer
        scope.launch { sendPeerAnnouncement(host.hostAddress) }
    }

    private fun updateDeviceStatus(name: String) {
        if (peers.containsKey(name)) {
            if (!connectedDevices.contains(name)) {
                connectedDevices.add(name)
                disconnectedDevices.remove(name)
                deviceInfo[name]?.addLog("Connected")
            }
        }
    }

    private fun removePeer(name: String) {
        peers.remove(name)
        if (deviceList.contains(name)) {
            connectedDevices.remove(name)
            if (!disconnectedDevices.contains(name)) {
                disconnectedDevices.add(name)
            }
            deviceInfo[name]?.addLog("Disconnected")
        }
    }

    // ── Clipboard I/O ─────────────────────────────────────────────────────────

    private fun handleIncomingClip(msg: ClipMessage) {
        Log.d(TAG, "📨 handleIncomingClip() - type=${msg.type}, source=${msg.source}, content='${msg.content.take(30)}...'")
        
        // Skip peer announcements (those are for device discovery, not clipboard)
        if (msg.type == "peer_announce") {
            Log.d(TAG, "📌 Peer announcement from ${msg.source}: ${msg.content}")
            // Extract and register peer if needed
            val parts = msg.content.split(":")
            if (parts.size >= 2) {
                val ipAddress = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: Protocol.PORT
                val addr = InetSocketAddress(ipAddress, port)
                
                // Add peer to internal map
                if (msg.source !in peers) {
                    peers[msg.source] = Pair(addr, msg.source)
                    if (msg.source !in deviceList) {
                        deviceList.add(msg.source)
                        if (msg.source !in deviceInfo) deviceInfo[msg.source] = DeviceInfo(msg.source)
                        deviceInfo[msg.source]?.lastSeen = LocalDateTime.now()
                    }
                    updateDeviceStatus(msg.source)
                    Log.d(TAG, "✓ Registered peer ${msg.source} at ${ipAddress}:${port}")
                } else {
                    Log.d(TAG, "⊘ Peer ${msg.source} already known")
                }
            }
            return
        }
        
        // Only process clipboard messages (type == "clipboard")
        if (msg.type != "clipboard") {
            Log.w(TAG, "⊘ Ignoring message with unknown type: ${msg.type}")
            return
        }
        
        if (msg.source == deviceName) {
            Log.d(TAG, "⊘ Skipped (echo prevention)")
            return
        }
        
        if (msg.content == lastSentContent) {
            Log.d(TAG, "⊘ Skipped (dedup - we already sent this)")
            return
        }
        
        lastSentContent = msg.content
        val preview = msg.content.take(40)
        Log.d(TAG, "📋 Processing incoming clipboard from ${msg.source}: '$preview...'")
        addToHistory(msg.content)
        deviceInfo[msg.source]?.addLog("📥 Clipboard received: $preview")
        
        Handler(Looper.getMainLooper()).post {
            try {
                clipManager.setPrimaryClip(ClipData.newPlainText("crossflow", msg.content))
                Log.d(TAG, "✓ Clipboard set from ${msg.source}")
                deviceInfo[msg.source]?.addLog("✓ Applied to system clipboard")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to set clipboard: ${e.message}", e)
                deviceInfo[msg.source]?.addLog("✗ Failed: ${e.message}")
            }
        }
    }

    private suspend fun syncOutgoingText(text: String, reason: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        Log.d(TAG, "Syncing outgoing text from $reason: '${normalized.take(30)}...'")
        lastSentContent = normalized
        lastPolledClipboard = normalized
        addToHistory(normalized)
        broadcastClipboard(normalized)
    }

    private suspend fun broadcastClipboard(text: String) {
        Log.d(TAG, "📤 broadcastClipboard() entry - text.length=${text.length}, peers.size=${peers.size}")
        val preview = text.take(30)
        
        if (peers.isEmpty()) {
            Log.w(TAG, "❌ broadcastClipboard FAILED: No peers discovered yet!")
            return
        }
        
        val msg = Protocol.encode(ClipMessage(content = text, source = deviceName))
        Log.d(TAG, "Encoded message length: ${msg.length}, first 80 chars: ${msg.take(80)}")
        val deadPeers = mutableListOf<String>()
        var successCount = 0
        
        peers.forEach { (name, pair) ->
            val addr = pair.first
            Log.d(TAG, "  → Sending to: $name at ${addr.hostName}:${addr.port}")
            try {
                withTimeout(5_000) {
                    Socket().use { socket ->
                        Log.d(TAG, "    Connecting...")
                        socket.connect(addr, 5000)
                        Log.d(TAG, "    Connected! Writing message...")
                        PrintWriter(socket.getOutputStream(), true).use { writer ->
                            writer.println(msg)
                            writer.flush()
                            Log.d(TAG, "    Flushed!")
                        }
                    }
                }
                successCount++
                Log.d(TAG, "  ✓ Successfully sent to $name: '$preview...'")
                deviceInfo[name]?.addLog("📤 Sent: $preview")
            } catch (e: Exception) {
                Log.e(TAG, "  ✗ FAILED to send to $name: ${e.javaClass.simpleName}: ${e.message}", e)
                deviceInfo[name]?.addLog("❌ Failed: ${e.message ?: e.javaClass.simpleName}")
                deadPeers.add(name)
            }
        }
        
        Log.d(TAG, "📤 broadcastClipboard() COMPLETE: $successCount/${peers.size} peers succeeded")
        
        if (deadPeers.isNotEmpty()) {
            Log.d(TAG, "Removing ${deadPeers.size} unreachable peers: $deadPeers")
            deadPeers.forEach { removePeer(it) }
        }
    }

    private suspend fun sendPeerAnnouncement(targetIp: String) {
        try {
            val localIp = getLocalIpAddress()
            if (localIp == null) {
                Log.w(TAG, "Cannot get local IP for peer announcement")
                return
            }
            val announcementContent = "$localIp:${Protocol.PORT}"
            val msg = Protocol.encode(ClipMessage(
                type = "peer_announce",
                content = announcementContent,
                source = deviceName
            ))
            Log.d(TAG, "Sending peer announcement to $targetIp: $announcementContent")
            withTimeout(3_000) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIp, Protocol.PORT), 3000)
                    PrintWriter(socket.getOutputStream(), true).println(msg)
                }
            }
            Log.d(TAG, "✓ Peer announcement sent to $targetIp")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to send peer announcement: ${e.message}")
        }
    }

    private fun addToHistory(text: String) {
        if (clipHistory.lastOrNull() != text) {
            clipHistory.add(0, text)
            if (clipHistory.size > 20) clipHistory.removeAt(clipHistory.lastIndex)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val channelId = "crossflow_fg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ch = NotificationChannel(
                    channelId, "CrossFlow Sync",
                    NotificationManager.IMPORTANCE_DEFAULT  // Changed from IMPORTANCE_LOW for better visibility
                ).apply { description = "Passive clipboard sharing - syncs without app open" }
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(ch)
                Log.d(TAG, "✓ Notification channel created: $channelId")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel: ${e.message}", e)
            }
        }

        return try {
            val intent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val syncNowIntent = Intent(this, ClipboardSyncService::class.java).apply {
                action = ACTION_SYNC_NOW
            }
            val syncNowPendingIntent = PendingIntent.getService(
                this,
                1,
                syncNowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = Notification.Builder(this, channelId)
                .setContentTitle("CrossFlow Sync Active")
                .setContentText("Use notification, tile, or Share to send text to Windows")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(intent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)

            // Add Sync Now action
            try {
                builder.addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_menu_share,
                        "Sync now",
                        syncNowPendingIntent
                    ).build()
                )
                Log.d(TAG, "✓ Sync action added to notification")
            } catch (e: Exception) {
                Log.w(TAG, "Warning: Could not add action to notification: ${e.message}")
            }

            val notification = builder.build()
            Log.d(TAG, "✓ Notification built successfully")
            notification
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical error building notification: ${e.message}", e)
            throw e
        }
    }
}
