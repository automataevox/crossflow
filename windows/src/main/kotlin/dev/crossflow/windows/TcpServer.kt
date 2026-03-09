package dev.crossflow.windows

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.InetSocketAddress

/** Listens on Protocol.PORT; calls [onMessage] for every valid ClipMessage received. */
class TcpServer(private val onMessage: (ClipMessage, InetSocketAddress?) -> Unit) {

    private var serverSocket: ServerSocket? = null

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(Protocol.PORT)
                serverSocket = socket
                val hostName = java.net.InetAddress.getLocalHost().hostName
                val ip = java.net.InetAddress.getLocalHost().hostAddress
                println("[TcpServer] ✓ Listening on $hostName ($ip):${Protocol.PORT}")
                println("[TcpServer] Ready to receive messages from peers")
            } catch (e: Exception) {
                println("[TcpServer] ✗ FAILED TO BIND on port ${Protocol.PORT}: ${e.message}")
                println("[TcpServer] ⚠️ Is another app using this port? Check: netstat -ano | findstr :${Protocol.PORT}")
                return@launch
            }
            while (isActive) {
                try {
                    val client = serverSocket!!.accept()
                    launch(Dispatchers.IO) {
                        try {
                            val clientAddr = InetSocketAddress(client.inetAddress.hostAddress, client.port) as InetSocketAddress?
                            val clientInfo = "${client.inetAddress.hostAddress}:${client.port}"
                            println("[TcpServer] ✓ Connection from $clientInfo")
                            
                            val line = BufferedReader(InputStreamReader(client.getInputStream())).readLine()
                            println("[TcpServer] ← Received ${line?.length ?: 0} bytes from $clientInfo")
                            if (line == null) {
                                println("[TcpServer] ⚠️ Received null line (connection closed?)")
                                return@launch
                            }
                            
                            val msg = Protocol.decode(line)
                            if (msg != null) {
                                println("[TcpServer] ✓ Decoded: type=${msg.type}, source=${msg.source}, content='${msg.content.take(40)}...'")
                                println("[TcpServer] 📧 Passing message to handler...")
                                onMessage(msg, clientAddr)
                            } else {
                                println("[TcpServer] ✗ Failed to decode message: $line")
                            }
                        } catch (e: Exception) {
                            println("[TcpServer] ✗ Client error: ${e.javaClass.simpleName} - ${e.message}")
                            e.printStackTrace()
                        } finally {
                            runCatching { client.close() }
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    println("[TcpServer] ✗ Server error: ${e.message}")
                }
            }
        }
    }

    fun stop() { runCatching { serverSocket?.close() } }
}
