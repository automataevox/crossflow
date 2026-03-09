package dev.crossflow.windows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/** Wraps JmDNS for mDNS service advertising and peer discovery, with UDP broadcast fallback. */
class MdnsHelper(
    private val scope: CoroutineScope,
    private val ownName: String,
    private val onPeerFound: (name: String, address: InetSocketAddress) -> Unit,
    private val onPeerLost: (name: String) -> Unit
) {
    private var jmdns: JmDNS? = null
    private var udpDiscoverySocket: DatagramSocket? = null
    private var mdnsWorking = false

    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                val localAddr = findLanAddress()
                
                println("[MdnsHelper] Found LAN address: ${localAddr?.hostAddress ?: "none"}")
                
                // Try multiple strategies to bind JmDNS
                jmdns = when {
                    // Strategy 1: Try to bind to specific LAN address
                    localAddr != null -> {
                        println("[MdnsHelper] Attempting to create JmDNS on ${localAddr.hostAddress}...")
                        try {
                            JmDNS.create(localAddr)
                        } catch (e: Exception) {
                            println("[MdnsHelper] ⚠️ Failed to bind to specific address: ${e.message}")
                            // Strategy 2: Try binding to 0.0.0.0 (all interfaces)
                            println("[MdnsHelper] Trying to bind to 0.0.0.0 (all interfaces)...")
                            try {
                                JmDNS.create(InetAddress.getByName("0.0.0.0"))
                            } catch (e2: Exception) {
                                println("[MdnsHelper] ⚠️ Failed to bind to 0.0.0.0: ${e2.message}")
                                // Strategy 3: Use default with custom port configuration
                                println("[MdnsHelper] Using default multicast configuration...")
                                JmDNS.create()
                            }
                        }
                    }
                    // No LAN address found, use default
                    else -> {
                        println("[MdnsHelper] No LAN address found, using default...")
                        JmDNS.create()
                    }
                }
                
                println("[MdnsHelper] ✓ JmDNS created successfully")

                // Advertise our service
                val info = ServiceInfo.create(
                    Protocol.SERVICE_TYPE,
                    ownName,
                    Protocol.PORT,
                    "CrossFlow clipboard sync"
                )
                println("[MdnsHelper] Registering service: $ownName on ${Protocol.SERVICE_TYPE} port ${Protocol.PORT}")
                jmdns!!.registerService(info)
                println("[MdnsHelper] ✓ Service registered on mDNS")
                mdnsWorking = true

                // Discover peers
                println("[MdnsHelper] Starting discovery for ${Protocol.SERVICE_TYPE}...")
                jmdns!!.addServiceListener(Protocol.SERVICE_TYPE, object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        println("[MdnsHelper] 🔍 Service detected: ${event.name}")
                        // Request full resolution
                        jmdns!!.requestServiceInfo(event.type, event.name, 2000)
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info
                        val name = info.name
                        println("[MdnsHelper] ✓ Service resolved: $name")
                        if (name == ownName) {
                            println("[MdnsHelper] Skipped (self)")
                            return
                        }
                        val addr = info.inet4Addresses.firstOrNull()
                            ?: info.inet6Addresses.firstOrNull()
                        if (addr == null) {
                            println("[MdnsHelper] ✗ No IP found for $name")
                            return
                        }
                        val socketAddr = InetSocketAddress(addr, info.port)
                        println("[MdnsHelper] ✓✓ PEER FOUND: $name @ ${addr.hostAddress}:${info.port}")
                        onPeerFound(name, socketAddr)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        println("[MdnsHelper] ✗ Peer lost: ${event.name}")
                        onPeerLost(event.name)
                    }
                })
                println("[MdnsHelper] ✓ Discovery listener active - waiting for peers...")
                
                // Start UDP broadcast discovery as fallback
                startUdpBroadcastDiscovery()
            } catch (e: Exception) {
                println("[MdnsHelper] ✗ Fatal error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /** UDP broadcast-based discovery - works when mDNS fails on Windows */
    private fun startUdpBroadcastDiscovery() {
        scope.launch(Dispatchers.IO) {
            try {
                // Try to bind to UDP 35647 like Android does, but allow reuseaddr for shared sockets
                try {
                    udpDiscoverySocket = DatagramSocket(Protocol.PORT)  
                    udpDiscoverySocket!!.reuseAddress = true
                    udpDiscoverySocket!!.broadcast = true
                    println("[MdnsHelper] UDP broadcast socket bound to port ${Protocol.PORT}")
                } catch (e: Exception) {
                    // If port 35647 is in use, try different port
                    println("[MdnsHelper] ⚠️ Cannot bind UDP to ${Protocol.PORT} (${e.message}), trying different port...")
                    udpDiscoverySocket = DatagramSocket(null)
                    udpDiscoverySocket!!.reuseAddress = true
                    udpDiscoverySocket!!.bind(InetSocketAddress("0.0.0.0", 0))
                    udpDiscoverySocket!!.broadcast = true
                    println("[MdnsHelper] UDP broadcast socket bound to port ${udpDiscoverySocket!!.localPort}")
                }
                
                // Send periodic announcement broadcasts
                scope.launch {
                    while (true) {
                        try {
                            kotlinx.coroutines.delay(5000) // Every 5 seconds
                            val message = "CROSSFLOW:$ownName:${Protocol.PORT}".toByteArray()
                            val broadcastTargets = findBroadcastAddresses()
                            for (broadcastAddr in broadcastTargets) {
                                val packet = DatagramPacket(message, message.size, broadcastAddr, Protocol.PORT)
                                udpDiscoverySocket?.send(packet)
                                println("[MdnsHelper] 📡 Broadcast to ${broadcastAddr.hostAddress}:${Protocol.PORT}: $ownName:${Protocol.PORT}")
                            }
                        } catch (e: Exception) {
                            println("[MdnsHelper] ⚠️ Broadcast send failed: ${e.message}")
                        }
                    }
                }
                
                // Listen for peer announcements from Android
                scope.launch {
                    val buffer = ByteArray(512)
                    while (true) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            udpDiscoverySocket?.receive(packet)
                            val message = String(packet.data, 0, packet.length)
                            
                            if (message.startsWith("CROSSFLOW:")) {
                                val parts = message.split(":")
                                if (parts.size >= 3) {
                                    val peerName = parts[1]
                                    val portStr = parts[2]
                                    
                                    if (peerName != ownName && portStr.toIntOrNull() != null) {
                                        val peerAddr = packet.address
                                        val socketAddr = InetSocketAddress(peerAddr, portStr.toInt())
                                        println("[MdnsHelper] 📡 UDP discovery: $peerName @ ${peerAddr.hostAddress}")
                                        onPeerFound(peerName, socketAddr)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if ((e.message?.contains("socket closed") != true)) {
                                println("[MdnsHelper] ⚠️ UDP receive error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("[MdnsHelper] ⚠️ UDP discovery setup failed: ${e.message}")
            }
        }
    }

    private fun findLanAddress(): InetAddress? {
        println("[MdnsHelper] Scanning network interfaces...")
        for (iface in NetworkInterface.getNetworkInterfaces()) {
            if (iface.isLoopback || !iface.isUp) continue
            println("[MdnsHelper]  - ${iface.displayName} (${iface.name})")
            
            for (addr in iface.inetAddresses) {
                val hostAddr = addr.hostAddress
                println("[MdnsHelper]    IP: $hostAddr")
                if (addr is Inet4Address && addr.isSiteLocalAddress) {
                    println("[MdnsHelper]    ✓ Found LAN interface!")
                    return addr
                }
            }
        }

        for (iface in NetworkInterface.getNetworkInterfaces()) {
            if (iface.isLoopback || !iface.isUp) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address) {
                    println("[MdnsHelper]    ✓ Falling back to IPv4 interface ${addr.hostAddress}")
                    return addr
                }
            }
        }

        println("[MdnsHelper] No active IPv4 interface found, using localhost")
        return InetAddress.getLocalHost()
    }

    private fun findBroadcastAddresses(): List<InetAddress> {
        val broadcasts = linkedSetOf<InetAddress>()

        for (iface in NetworkInterface.getNetworkInterfaces()) {
            if (iface.isLoopback || !iface.isUp) continue

            for (interfaceAddress in iface.interfaceAddresses) {
                val address = interfaceAddress.address
                val broadcast = interfaceAddress.broadcast
                if (address is Inet4Address && address.isSiteLocalAddress && broadcast != null) {
                    broadcasts.add(broadcast)
                }
            }
        }

        if (broadcasts.isEmpty()) {
            broadcasts.add(InetAddress.getByName("255.255.255.255"))
        }

        return broadcasts.toList()
    }

    fun stop() {
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        runCatching { udpDiscoverySocket?.close() }
    }
}
