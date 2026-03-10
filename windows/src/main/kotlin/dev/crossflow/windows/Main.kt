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
import javax.imageio.ImageIO
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
            alwaysOnTop = false,
            resizable = false
        ) {
            // Empty - just exists to keep app alive (hidden from taskbar)
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

/** Loads tray icon from PNG or falls back to generated icon. */
private fun buildTrayImage(): java.awt.Image {
    try {
        // Try to load pre-generated tray_icon.png from resources
        val iconPath = "tray_icon.png"
        val iconStream = object {}.javaClass.classLoader.getResourceAsStream(iconPath)
        if (iconStream != null) {
            val bufferedImage = ImageIO.read(iconStream)
            if (bufferedImage != null) {
                println("[CrossFlow] ✓ Tray icon loaded from tray_icon.png")
                return bufferedImage
            }
        }
    } catch (e: Exception) {
        println("[CrossFlow] ⚠️ Could not load tray icon: ${e.message}")
    }
    
    // Fallback: generate simple tray icon (16x16)
    println("[CrossFlow] Using fallback generated tray icon")
    val size = 16
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    
    // Material 3 Blue background
    g.color = java.awt.Color(15, 95, 184)  // #0F5FB8
    g.fillOval(0, 0, size, size)
    
    // White dot in center
    g.color = java.awt.Color.WHITE
    g.fillOval(6, 6, 4, 4)
    
    g.dispose()
    return img
}
