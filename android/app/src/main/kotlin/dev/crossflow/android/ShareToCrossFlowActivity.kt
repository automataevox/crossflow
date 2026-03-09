package dev.crossflow.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity

class ShareToCrossFlowActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = extractSharedText(intent)
        if (sharedText.isNotBlank()) {
            val serviceIntent = Intent(this, ClipboardSyncService::class.java).apply {
                action = ClipboardSyncService.ACTION_SYNC_TEXT
                putExtra(ClipboardSyncService.EXTRA_SYNC_TEXT, sharedText)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        finish()
    }

    private fun extractSharedText(intent: Intent?): String {
        if (intent == null) return ""

        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
            else -> ""
        }.trim()
    }
}