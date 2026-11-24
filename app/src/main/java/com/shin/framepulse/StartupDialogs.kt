package com.shin.framepulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-launch language selector. Always displayed in English before the user picks a language.
 */
@Composable
fun FirstLaunchLanguageScreen(
    onSelectLanguage: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FRAMEPULSE",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Choose Your Language",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Select your preferred language for FramePulse. You can change this anytime in Setup.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = { onSelectLanguage("en") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text(
                    text = "English",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onSelectLanguage("id") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Bahasa Indonesia",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Shown only when updating from an older version (not on fresh install).
 */
@Composable
fun WhatsNewUpdateDialog(
    versionName: String,
    isId: Boolean,
    onDismiss: () -> Unit
) {
    val title = if (isId) "Baru di v$versionName" else "What's New in v$versionName"
    val subtitle = if (isId) {
        "FramePulse berhasil diperbarui! Berikut perubahan & fitur terbaru:"
    } else {
        "FramePulse has been updated! Here are the latest changes & features:"
    }
    val items = if (isId) {
        listOf(
            "• Shizuku Pro Tweaks: Smart Standby Bucket, Lock Refresh Rate (60/90Hz), Gaming Visual Mode (Blur OFF / 0.5x), dan Deep ART Compile.",
            "• Thermal Warning HUD: Peringatan otomatis di layar saat suhu baterai >= 42.0°C.",
            "• Quick Collapse HUD: Ketuk 2x pada HUD melayang untuk langsung mengecil ke mode Mini.",
            "• Auto-Show HUD: Otomatis sembunyi di Home screen dan muncul saat masuk game.",
            "• Benchmark Pro: Metrik Frametime (ms), Stutter Counter, Export CSV, dan Simpan Card PNG ke Galeri.",
            "• Update Checker Pro: Popup changelog otomatis, info ukuran APK, dan tombol Lewati Versi."
        )
    } else {
        listOf(
            "• Shizuku Pro Tweaks: Smart Standby Bucket, Refresh Rate Lock (60/90Hz), Gaming Visual Mode (Blur OFF / 0.5x), and Deep ART Compile.",
            "• HUD Thermal Warning: Automatic visual alert when battery temperature reaches >= 42.0°C.",
            "• Quick Collapse HUD: Double-tap the floating HUD to toggle Mini mode instantly.",
            "• Auto-Show HUD: Automatically hides on Home screen and appears when launching games.",
            "• Benchmark Pro: Frametime (ms), Stutter Counter, CSV Export, and PNG Result Card saved to Gallery.",
            "• Pro Update Checker: Auto changelog popup, APK size info, and Skip Version option."
        )
    }
    val btnLabel = if (isId) "Mantap, Lanjut" else "Got It"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { line ->
                    Text(
                        text = line,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
            ) {
                Text(
                    text = btnLabel,
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    )
}
