package com.shin.framepulse

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku

data class DeviceCapabilities(
    val androidApiLevel: Int,
    val androidApiReady: Boolean,
    val overlayReady: Boolean,
    val shizukuBinderAlive: Boolean,
    val shizukuGranted: Boolean,
    val surfaceFlingerReady: Boolean,
    val gameModeReady: Boolean
) {
    val tierLabel: String
        get() = if (shizukuGranted) "TIER 2 • SHIZUKU" else "TIER 1 • STANDARD"
}

object CapabilityEngine {
    fun inspect(context: Context, telemetry: TelemetryData = TelemetryHub.latest): DeviceCapabilities {
        val api = Build.VERSION.SDK_INT
        val overlay = try {
            Settings.canDrawOverlays(context)
        } catch (_: Throwable) {
            false
        }
        val binder = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        val shizukuOk = try {
            binder && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        val sfReady = shizukuOk || telemetry.isTrueGameFps
        val gameModeOk = shizukuOk && api >= Build.VERSION_CODES.S

        return DeviceCapabilities(
            androidApiLevel = api,
            androidApiReady = api >= Build.VERSION_CODES.O,
            overlayReady = overlay,
            shizukuBinderAlive = binder,
            shizukuGranted = shizukuOk,
            surfaceFlingerReady = sfReady,
            gameModeReady = gameModeOk
        )
    }
}

@Composable
fun CapabilityStatusCard(
    caps: DeviceCapabilities,
    accent: Color,
    onOpenSetup: () -> Unit
) {
    val tierColor = if (caps.shizukuGranted) EmeraldNeon else accent
    CyberCard(
        borderColor = tierColor.copy(alpha = 0.35f),
        accentGlow = tierColor
    ) {
        SectionTitle(
            title = "DEVICE CAPABILITIES",
            badge = caps.tierLabel,
            accent = tierColor,
            iconType = "cpu"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityBadgeItem(
                label = "Android API ${caps.androidApiLevel}",
                active = caps.androidApiReady,
                modifier = Modifier.weight(1f)
            )
            CapabilityBadgeItem(
                label = "Shizuku",
                active = caps.shizukuGranted,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityBadgeItem(
                label = "SurfaceFlinger",
                active = caps.surfaceFlingerReady,
                modifier = Modifier.weight(1f)
            )
            CapabilityBadgeItem(
                label = "Game Mode",
                active = caps.gameModeReady,
                modifier = Modifier.weight(1f)
            )
        }

        if (!caps.shizukuGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Hubungkan Shizuku di tab Setup untuk mengaktifkan Tier 2 (SurfaceFlinger & Game Mode) →",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSetup() }
            )
        }
    }
}

@Composable
private fun CapabilityBadgeItem(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (active) EmeraldNeon else TextMuted
    val symbol = if (active) "✓" else "—"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) EmeraldNeon.copy(alpha = 0.10f) else Color(0xFF080E1B))
            .border(
                width = 1.dp,
                color = if (active) EmeraldNeon.copy(alpha = 0.35f) else CardBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Text(
            text = symbol,
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
