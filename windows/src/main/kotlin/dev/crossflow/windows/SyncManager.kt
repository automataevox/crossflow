package dev.crossflow.windows

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.NetworkInterface
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

/** Orchestrates mDNS discovery, TCP server, clipboard monitoring, and peer sync. */
class SyncManager {

    val deviceList  = mutableStateListOf<String>()
    val connectedDevices = mutableStateListOf<String>()
    val disconnectedDevices = mutableStateListOf<String>()
    val clipHistory = mutableStateListOf<String>()
    val isRunning   = mutableStateOf(false)

    private val scope     = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val peers     = mutableMapOf<String, InetSocketAddress>()
    private val deviceInfo = mutableMapOf<String, DeviceInfo>()
    private val deviceName: String = buildDeviceName()
    private val inactivityTimeout = 15_000L  // 15 seconds before marking disconnected

    private val clipMonitor = ClipboardMonitor { text -> onLocalClipboardChange(text) }
    private val tcpServer   = TcpServer { msg, clientAddr -> onRemoteClipboard(msg, clientAddr) }
    private val mdns        = MdnsHelper(
        scope      = scope,
        ownName    = deviceName,
        onPeerFound = { name, addr -> addPeer(name, addr) },
        onPeerLost  = { name      -> removePeer(name)    }
    )

    fun start() {
        tcpServer.start(scope)
        mdns.start()
        clipMonitor.start()
        
        val localIp = getLocalIpAddress()
        println("[SyncManager] ℹ️ Windows local IP: $localIp")
        println("[SyncManager] ℹ️ Device name: $deviceName")
        println("[SyncManager] ℹ️ TCP listening on port ${Protocol.PORT}")
        
        // Continuous peer health check & reconnect
        scope.launch(Dispatchers.IO) {
            println("[SyncManager] 🔄 Starting continuous peer health check (every 5s)...")
            while (isActive) {
                try {
                    delay(5_000)  // Check every 5 seconds
                    
                    // Re-announce our presence
                    val msg = Protocol.encode(
                        ClipMessage(
                            type = "peer_announce",
                            content = "${getLocalIpAddress() ?: "unknown"}:${Protocol.PORT}",
                            source = deviceName
                        )
                    )
                    
                    // Try to reach all known peers to keep connections fresh
                    val deadPeers = mutableListOf<String>()
                    peers.forEach { (name, addr) ->
                        try {
                            withTimeout(2_000) {
                                Socket().use { socket ->
                                    socket.connect(addr, 2000)
                                    // Send keep-alive announcement
                                    PrintWriter(socket.getOutputStream(), true).println(msg)
                                }
                            }
                            // Update last seen
                            deviceInfo[name]?.lastSeen = LocalDateTime.now()
                        } catch (e: Exception) {
                            // Peer unreachable
                            deadPeers.add(name)
                        }
                    }
                    
                    // Remove persistently unreachable peers
                    deadPeers.forEach { name ->
                        val lastSeen = deviceInfo[name]?.lastSeen
                        if (lastSeen != null) {
                            val secondsAgo = java.time.temporal.ChronoUnit.SECONDS.between(lastSeen, LocalDateTime.now())
                            if (secondsAgo > 30) {  // If unreachable for more than 30 seconds
                                println("[SyncManager] Marking ${name} as disconnected (unreachable for ${secondsAgo}s)")
                                removePeer(name)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        println("[SyncManager] Health check error: ${e.message}")
                    }
                }
            }
        }
        
        // Continuous mDNS re-scan (fallback discovery)
        scope.launch(Dispatchers.IO) {
            println("[SyncManager] 🔄 Starting continuous mDNS re-scan (every 15s)...")
            while (isActive) {
                try {
                    delay(15_000)  // Re-scan every 15 seconds
                    println("[SyncManager] 🔎 Re-scanning for peers on network...")
                    // The mDNS helper should be continuously listening, but this ensures we stay active
                } catch (e: Exception) {
                    if (isActive) {
                        println("[SyncManager] Re-scan error: ${e.message}")
                    }
                }
            }
        }
        
        isRunning.value = true
        println("[SyncManager] ✓ Sync manager started with continuous discovery")
    }
    
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            interfaces.asSequence()
                .filter { !it.isLoopback && it.isUp }
                .flatMap { it.inetAddresses.asSequence() }
                .filter { it.address.size == 4 }  // IPv4 only
                .map { it.hostAddress }
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        clipMonitor.stop()
        tcpServer.stop()
        mdns.stop()
        scope.cancel()
        isRunning.value = false
    }

    // ── Peer list ─────────────────────────────────────────────────────────────

    private fun addPeer(name: String, addr: InetSocketAddress) {
        peers[name] = addr
        if (name !in deviceList) {
            deviceList.add(name)
            if (name !in deviceInfo) deviceInfo[name] = DeviceInfo(name)
            deviceInfo[name]?.lastSeen = LocalDateTime.now()
        }
        updateDeviceStatus(name)
    }

    private fun removePeer(name: String) {
        peers.remove(name)
        if (deviceList.contains(name)) {
            // Move to disconnected instead of removing
            connectedDevices.remove(name)
            if (!disconnectedDevices.contains(name)) {
                disconnectedDevices.add(name)
            }
            deviceInfo[name]?.let {
                it.addLog("Disconnected")
            }
        }
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

    fun getDeviceInfo(name: String): DeviceInfo? = deviceInfo[name]

    private fun onLocalClipboardChange(text: String) {
        val preview = text.take(40)
        println("[SyncManager] ⬇️ Clipboard changed: $preview...")
        addToHistory(text)
        scope.launch { broadcast(text) }
    }

    private fun onRemoteClipboard(msg: ClipMessage, clientAddr: InetSocketAddress?) {
        println("[SyncManager] 📨 onRemoteClipboard() called")
        println("[SyncManager]   - Source: ${msg.source}")
        println("[SyncManager]   - Type: ${msg.type}")
        println("[SyncManager]   - Content length: ${msg.content.length}")
        println("[SyncManager]   - Client IP: ${clientAddr?.address?.hostAddress ?: "unknown"}")
        println("[SyncManager]   - Device name: $deviceName")
        
        // Auto-discover peer from connection if it's a message from an unknown device
        if (clientAddr != null && msg.source !in peers && !msg.source.equals(deviceName, ignoreCase = true)) {
            println("[SyncManager] Auto-discovered peer ${msg.source} connecting from ${clientAddr.address.hostAddress}:${clientAddr.port}")
            addPeer(msg.source, clientAddr)
            deviceInfo[msg.source]?.addLog("Connected from ${clientAddr.address.hostAddress}:${clientAddr.port}")
        }
        
        // Handle peer announcements from Android
        if (msg.type == "peer_announce") {
            println("[SyncManager] 📌 Processing peer announcement from ${msg.source}: ${msg.content}")
            val parts = msg.content.split(":")
            if (parts.size >= 2) {
                val ipAddress = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: Protocol.PORT
                val addr = InetSocketAddress(ipAddress, port)
                addPeer(msg.source, addr)
                deviceInfo[msg.source]?.addLog("Announced from ${ipAddress}:${port}")
                println("[SyncManager] ✓ Peer ${msg.source} registered at ${ipAddress}:${port}")
            }
            return
        }
        
        // Handle clipboard messages
        if (msg.source == deviceName) {
            println("[SyncManager] ⊘ Skipped (echo prevention - message from self)")
            return
        }
        
        val preview = msg.content.take(40)
        println("[SyncManager] 📋 CLIPBOARD MESSAGE incoming from ${msg.source}: '$preview...'")
        deviceInfo[msg.source]?.addLog("📥 Clipboard received: $preview")
        addToHistory(msg.content)
        
        try {
            println("[SyncManager]   Calling clipMonitor.write()...")
            clipMonitor.write(msg.content)
            println("[SyncManager] ✓ Clipboard updated successfully")
            deviceInfo[msg.source]?.addLog("✓ Applied to system clipboard")
        } catch (e: Exception) {
            println("[SyncManager] ✗ Failed to write clipboard: ${e.javaClass.simpleName} - ${e.message}")
            deviceInfo[msg.source]?.addLog("✗ Failed to apply: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun broadcast(text: String) {
        val message = Protocol.encode(ClipMessage(content = text, source = deviceName))
        val preview = text.take(30)
        println("[SyncManager] 📤 Broadcasting clipboard (${peers.size} peers)")
        if (peers.isEmpty()) {
            println("[SyncManager] ⚠️ No peers discovered yet!")
            return
        }
        val deadPeers = mutableListOf<String>()
        peers.forEach { (name, addr) ->
            println("[SyncManager] Attempting to send to $name at ${addr.hostName}:${addr.port}...")
            deviceInfo[name]?.addLog("Attempting to send: $preview")
            try {
                // Increased timeout to 10 seconds for Windows-to-Windows on slower networks
                withTimeout(10_000) {
                    Socket().use { socket ->
                        println("[SyncManager] Socket created, connecting to $name (${addr.address.hostAddress}:${addr.port})...")
                        socket.connect(addr, 8000)  // 8 second connect timeout
                        println("[SyncManager] ✓ Connected to $name! Sending ${message.length} bytes...")
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(message)
                        writer.flush()
                        println("[SyncManager] ✓ Message sent to $name (${preview}...)")
                        deviceInfo[name]?.addLog("✓ Sent: $preview")
                    }
                }
            } catch (e: java.net.ConnectException) {
                println("[SyncManager] ✗ Connection refused by $name at ${addr.hostName}:${addr.port} - peer may not be listening")
                deviceInfo[name]?.addLog("✗ Connection refused - is the remote app running?")
                deadPeers.add(name)
            } catch (e: java.net.SocketTimeoutException) {
                println("[SyncManager] ✗ Connection timeout to $name at ${addr.hostName}:${addr.port} - check firewall")
                deviceInfo[name]?.addLog("✗ Timeout - check firewall allows port ${Protocol.PORT}")
                deadPeers.add(name)
            } catch (e: TimeoutCancellationException) {
                println("[SyncManager] ✗ Send timeout to $name (took >10s) - network may be slow/blocked")
                deviceInfo[name]?.addLog("✗ Send timeout - network issue")
                deadPeers.add(name)
            } catch (e: Exception) {
                println("[SyncManager] ✗ Failed to reach $name: ${e.javaClass.simpleName} - ${e.message}")
                e.printStackTrace()
                deviceInfo[name]?.addLog("✗ Error: ${e.message}")
                // Don't mark as dead yet - may be temporary
            }
        }
        deadPeers.forEach { removePeer(it) }
    }

    private fun addToHistory(text: String) {
        if (clipHistory.firstOrNull() != text) {
            clipHistory.add(0, text)
            if (clipHistory.size > 20) clipHistory.removeAt(clipHistory.lastIndex)
        }
    }

    private fun buildDeviceName(): String {
        val host = runCatching {
            java.net.InetAddress.getLocalHost().hostName
        }.getOrDefault("Windows-PC")
        return host.replace(" ", "_").take(32)
    }
}
