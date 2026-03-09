package dev.crossflow.android

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket

class TcpServer(private val onMessage: (ClipMessage) -> Unit) {

    private val TAG = "TcpServer"
    private var serverSocket: ServerSocket? = null

    suspend fun start(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        serverSocket = ServerSocket(Protocol.PORT)
        Log.d(TAG, "🌐 TCP server listening on port ${Protocol.PORT}")
        while (isActive) {
            try {
                val client = serverSocket!!.accept()
                Log.d(TAG, "📩 Client connected from ${client.inetAddress.hostAddress}")
                scope.launch(Dispatchers.IO) {
                    try {
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val line = reader.readLine() ?: return@launch
                        Log.d(TAG, "📝 Received: ${line.take(50)}...")
                        Protocol.decode(line)?.let { msg ->
                            Log.d(TAG, "✓ Decoded from ${msg.source}")
                            onMessage(msg)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ Client error: ${e.message}")
                    } finally {
                        runCatching { client.close() }
                    }
                }
            } catch (e: Exception) {
                if (!isActive) break
                Log.w(TAG, "Accept error: ${e.message}")
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
    }
}
