package dev.crossflow.android

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class CrossFlowTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.tile_label)
            state = if (ClipboardSyncService.isRunning.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val serviceIntent = Intent(this, ClipboardSyncService::class.java).apply {
            action = ClipboardSyncService.ACTION_SYNC_NOW
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }
}