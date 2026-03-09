package dev.crossflow.windows

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.PopupMenu
import java.awt.MenuItem
import java.awt.image.BufferedImage
import kotlin.concurrent.thread

fun main() = application {
    println("[CrossFlow] ═══════════════════════════════════════════════════════")
    println("[CrossFlow] CrossFlow Clipboard Sync - Starting...")
    println("[CrossFlow] ═══════════════════════════════════════════════════════")
    
    // Initialize sync manager IMMEDIATELY (before window creation)
    val manager = remember { 
        SyncManager().also { 
            println("[CrossFlow] 🟢 Initializing sync manager...")
        }
    }
    
    // Start sync service IMMEDIATELY in background
    LaunchedEffect(Unit) { 
        println("[CrossFlow] Starting sync service...")
        manager.start()
        println("[CrossFlow] ✓ Sync service started - running indefinitely in background")
        println("[CrossFlow] ✓ Now listening for clipboard changes and incoming messages")
    }
    
    var showWindow by remember { mutableStateOf(true) }
    var shouldExit by remember { mutableStateOf(false) }

    // ── System tray ───────────────────────────────────────────────────────────
    if (SystemTray.isSupported()) {
        val tray = SystemTray.getSystemTray()
        var trayIcon: TrayIcon? = null
        
        val popup = PopupMenu().apply {
            add(MenuItem("Show / Restore Window").apply {
                addActionListener { 
                    showWindow = true
                    println("[CrossFlow] Window restored from tray")
                }
            })
            addSeparator()
            add(MenuItem("✓ Syncing Active (${manager.connectedDevices.size} devices)").apply {
                isEnabled = false
            })
            addSeparator()
            add(MenuItem("Exit CrossFlow (Stop Sync)").apply {
                addActionListener {
                    println("[CrossFlow] User requested exit, stopping sync...")
                    manager.stop()
                    shouldExit = true
                    if (trayIcon != null) try { tray.remove(trayIcon) } catch (_: Exception) {}
                    Thread.sleep(500)  // Brief delay for cleanup
                    exitApplication()
                }
            })
        }
        
        trayIcon = TrayIcon(buildTrayImage(), "CrossFlow — Background Sync Active", popup).apply {
            isImageAutoSize = true
            // Left-click also opens window
            addActionListener {
                println("[CrossFlow] Tray icon clicked - showing window")
                showWindow = true
            }
        }
        
        DisposableEffect(Unit) {
            try {
                tray.add(trayIcon)
                println("[CrossFlow] ✓ System tray icon active - app will run in background when closed")
            } catch (e: Exception) {
                println("[CrossFlow] ⚠️ Failed to add tray icon: ${e.message}")
            }
            onDispose { 
                try { tray.remove(trayIcon) } catch (_: Exception) {}
            }
        }
    } else {
        println("[CrossFlow] ⚠️ System tray not supported; keeping window visible")
    }

    // ── Hidden keeper window (keeps app alive when main window is hidden) ─────
    var showKeeperWindow by remember { mutableStateOf(true) }
    if (showKeeperWindow && !showWindow) {
        Window(
            onCloseRequest = { },  // Prevent closing
            title = "",
            state = rememberWindowState(width = 1.dp, height = 1.dp),
            transparent = true,
            undecorated = true,
            alwaysOnTop = false
        ) {
            // Empty - just exists to keep app alive
        }
    }

    // ── Main window (can be closed to minimize to tray) ─────────────────────────
    if (showWindow) {
        Window(
            onCloseRequest = { 
                showWindow = false
                println("[CrossFlow] Window hidden — syncing continues in background (check tray icon)")
            },
            title = "CrossFlow — Clipboard Sync",
            state = rememberWindowState(size = DpSize(420.dp, 560.dp))
        ) {
            CrossFlowApp(manager)
        }
    }
}

/** Generates a simple tray icon programmatically (replace with real .ico in production). */
private fun buildTrayImage(): java.awt.Image {
    val size = 16
    val img  = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g    = img.createGraphics()
    g.color  = java.awt.Color(0, 103, 192)
    g.fillOval(0, 0, size, size)
    g.color = java.awt.Color.WHITE
    // Simple arrows shape
    g.fillRect(4, 6, 8, 2)
    g.fillRect(4, 9, 8, 2)
    g.dispose()
    return img
}
