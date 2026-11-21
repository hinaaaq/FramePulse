package com.shin.framepulse

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class FramePulseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        if (OverlayService.isRunning) {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            })
            qsTile?.state = Tile.STATE_INACTIVE
        } else {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            })
            qsTile?.state = Tile.STATE_ACTIVE
        }
        qsTile?.updateTile()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        tile.state = if (OverlayService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (OverlayService.isRunning) "Aktif" else "Mati"
        tile.updateTile()
    }
}
