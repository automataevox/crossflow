package dev.crossflow.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class NsdHelper(private val context: Context, private val coroutineScope: CoroutineScope? = null) {

    private val TAG = "NsdHelper"
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var udpDiscoverySocket: DatagramSocket? = null

    /** Emits fully-resolved NsdServiceInfo objects for discovered peers. */
    val resolvedPeers = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    /** Services that have been lost (by name). */
    val lostPeers = Channel<String>(Channel.UNLIMITED)

    fun register(deviceName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = Protocol.SERVICE_TYPE
            port = Protocol.PORT
        }
        Log.d(TAG, "📢 Registering NSD: $deviceName on ${Protocol.SERVICE_TYPE} port ${Protocol.PORT}")
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "✓ NSD registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "✗ NSD registration failed: error code=$code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "NSD unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Unregistration failed: $code")
            }
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener!!)
    }

    fun startDiscovery(ownName: String) {
        Log.d(TAG, "🔍 Starting discovery for: ${Protocol.SERVICE_TYPE}")
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                Log.d(TAG, "✓ Discovery started for $type")
            }
            override fun onDiscoveryStopped(type: String) {
                Log.d(TAG, "⏹️ Discovery stopped for $type")
            }
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.e(TAG, "✗ Failed to start discovery: code=$code type=$type")
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {
                Log.e(TAG, "Failed to stop discovery: $code")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                val rawType = service.serviceType ?: "null"
                Log.d(TAG, "🔵 Service found: ${service.serviceName} type=$rawType")
                
                // Normalize service type comparison
                val normalizedFound = service.serviceType?.trim() ?: ""
                val normalizedExpected = Protocol.SERVICE_TYPE.trim()
                
                if (normalizedFound != normalizedExpected) {
                    Log.d(TAG, "   Skipped (type mismatch: '$normalizedFound' vs expected '$normalizedExpected')")
                    return
                }
                
                if (service.serviceName == ownName) {
                    Log.d(TAG, "   Skipped (is self)")
                    return
                }
                
                Log.d(TAG, "   ✓ Valid peer found, resolving: ${service.serviceName}…")
                resolveWithRetry(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "❌ Lost: ${service.serviceName}")
                lostPeers.trySend(service.serviceName)
            }
        }
        nsdManager.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
        
        // Also start UDP broadcast discovery as fallback
        startUdpBroadcastDiscovery(ownName)
    }

    private fun resolveWithRetry(service: NsdServiceInfo, attempt: Int = 0) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "⚠️ Resolve attempt $attempt failed: code=$code for ${info.serviceName}")
                if (attempt < 4) {
                    android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed({ resolveWithRetry(service, attempt + 1) }, 500L * (attempt + 1))
                } else {
                    Log.e(TAG, "✗ Gave up resolving ${service.serviceName} after ${attempt + 1} attempts")
                }
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                Log.d(TAG, "✓ Resolved: ${info.serviceName} @ ${info.host}:${info.port}")
                resolvedPeers.trySend(info)
            }
        })
    }

    /** Listen for UDP broadcast announcements from peers (fallback when mDNS fails) */
    private fun startUdpBroadcastDiscovery(ownName: String) {
        if (coroutineScope == null) {
            Log.d(TAG, "No coroutine scope, skipping UDP discovery")
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                udpDiscoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(Protocol.PORT))
                }
                udpDiscoverySocket!!.broadcast = true
                Log.d(TAG, "📡 UDP broadcast listener started on port ${Protocol.PORT}")

                launch {
                    while (true) {
                        try {
                            delay(5_000)
                            val message = "CROSSFLOW:$ownName:${Protocol.PORT}".toByteArray()
                            val broadcastTargets = findBroadcastAddresses()
                            for (broadcastAddr in broadcastTargets) {
                                val packet = DatagramPacket(message, message.size, broadcastAddr, Protocol.PORT)
                                udpDiscoverySocket?.send(packet)
                                Log.d(TAG, "📡 Broadcast to ${broadcastAddr.hostAddress}:${Protocol.PORT}: $ownName:${Protocol.PORT}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "UDP broadcast send failed: ${e.message}")
                        }
                    }
                }
                
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
                                    Log.d(TAG, "📡 UDP discovery: $peerName @ ${peerAddr.hostAddress}")
                                    
                                    // Create a synthetic NsdServiceInfo
                                    val info = NsdServiceInfo().apply {
                                        serviceName = peerName
                                        host = peerAddr
                                        port = portStr.toInt()
                                        serviceType = Protocol.SERVICE_TYPE
                                    }
                                    resolvedPeers.trySend(info)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if ((e.message?.contains("socket closed") != true) && (e.message?.contains("SocketException") != true)) {
                            Log.w(TAG, "UDP receive error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "UDP discovery setup failed: ${e.message}")
            }
        }
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

    fun tearDown() {
        runCatching { registrationListener?.let { nsdManager.unregisterService(it) } }
        runCatching { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } }
        runCatching { udpDiscoverySocket?.close() }
        resolvedPeers.close()
        lostPeers.close()
    }
}
