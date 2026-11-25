package com.shin.framepulse

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class FramePulseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        Prefs.init(this)
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        Prefs.init(this)
        val str = AppStrings.forLang()
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        if (OverlayService.isRunning) {
            if (OverlayService.isTouchLocked) {
                // Convenient 1-tap unlock from Quick Settings if notification permission was denied!
                runCatching {
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_TOGGLE_LOCK
                    })
                }
                return
            }
            stopService(Intent(this, OverlayService::class.java))
            qsTile?.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile?.subtitle = str.tileOff
            }
        } else {
            val started = runCatching {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                })
                true
            }.getOrDefault(false)
            qsTile?.state = if (started) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile?.subtitle = if (started) str.tileActive else str.tileOff
            }
        }
        qsTile?.updateTile()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val str = AppStrings.forLang()
        tile.state = if (OverlayService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (OverlayService.isRunning) str.tileActive else str.tileOff
        }
        tile.updateTile()
    }
}
