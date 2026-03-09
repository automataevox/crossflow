package dev.crossflow.windows

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the clipboard sync service in the background without any UI.
 * This daemon continues syncing even when the main app is closed.
 * Auto-starts via Windows Task Scheduler or registry entry.
 */
class SyncDaemon {
    companion object {
        private val logFile = File(System.getProperty("user.home"), ".crossflow_sync.log")
        
        private fun log(msg: String) {
            val timestamp = java.time.LocalDateTime.now()
            println("$timestamp: $msg")
            try {
                logFile.appendText("$timestamp: $msg\n")
                if (logFile.length() > 1_000_000) logFile.writeText("")  // Rotate if too large
            } catch (_: Exception) {}
        }
    }

    private val manager = SyncManager()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun run() {
        try {
            log("🟢 CrossFlow Sync Daemon starting...")
            manager.start()
            log("✓ Sync manager started")

            // Keep daemon alive
            runBlocking {
                delay(Long.MAX_VALUE)
            }
        } catch (e: Exception) {
            log("✗ Fatal error: ${e.message}")
            e.printStackTrace(System.err)
        } finally {
            cleanup()
        }
    }

    fun cleanup() {
        try {
            log("Cleaning up daemon...")
            manager.stop()
            scope.cancel()
        } catch (_: Exception) {}
    }
}

fun main() {
    val daemon = SyncDaemon()
    
    // Handle graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Received shutdown signal, cleaning up...")
        daemon.cleanup()
    })

    daemon.run()
}
