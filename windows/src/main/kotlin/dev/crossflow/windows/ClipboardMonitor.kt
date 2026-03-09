package dev.crossflow.windows

import java.awt.Toolkit
import java.awt.datatransfer.*

/** Polls the system clipboard every 400 ms and calls [onChange] when content changes.
 *  Also provides [write] to push text onto the clipboard. */
class ClipboardMonitor(private val onChange: (String) -> Unit) : ClipboardOwner {

    private val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    private var lastContent = ""
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread({
            while (running) {
                try {
                    val text = readClipboard()
                    if (text != null && text != lastContent && text.isNotBlank()) {
                        lastContent = text
                        onChange(text)
                    }
                } catch (_: Exception) {}
                Thread.sleep(400)
            }
        }, "ClipboardMonitor").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }

    fun write(text: String) {
        println("[ClipboardMonitor] write() called with ${text.length} bytes: '${text.take(40)}...'")
        if (text == lastContent) {
            println("[ClipboardMonitor] Skipped write (same as lastContent)")
            return
        }
        lastContent = text
        try {
            clipboard.setContents(StringSelection(text), this)
            println("[ClipboardMonitor] ✓ Successfully wrote to system clipboard")
        } catch (e: Exception) {
            println("[ClipboardMonitor] ✗ write error: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun readClipboard(): String? {
        return try {
            val contents = clipboard.getContents(null) ?: return null
            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (_: Exception) { null }
    }

    override fun lostOwnership(clip: Clipboard, contents: Transferable) {}
}
