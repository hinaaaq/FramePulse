package com.shin.framepulse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOG = "extra_crash_log"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val isId = AppStrings.resolveLangCode() == "id"
        val fallback = Prefs.lastCrashLog.ifBlank {
            if (isId) "Tidak ada detail error." else "No error details available."
        }
        val logText = intent.getStringExtra(EXTRA_LOG)?.takeIf { it.isNotBlank() } ?: fallback

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CyberCyan,
                    background = BgObsidian,
                    surface = CardSurface
                )
            ) {
                CrashScreen(
                    logText = logText,
                    isId = isId,
                    onCopy = {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("FramePulse Crash Log", logText))
                        Toast.makeText(
                            this,
                            if (isId) "Log crash berhasil disalin" else "Crash log copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onRestart = {
                        val restartIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restartIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun CrashScreen(
    logText: String,
    isId: Boolean,
    onCopy: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "FramePulse Crash Guard",
            color = CrimsonAccent,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isId) {
                "Aplikasi menangkap error tak terduga. Salin log di bawah ini untuk langsung diperbaiki."
            } else {
                "An unexpected error was caught. Copy the log below so it can be diagnosed."
            },
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF04070D))
                .border(1.dp, CrimsonAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = logText,
                color = Color(0xFFFCA5A5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text(if (isId) "SALIN LOG" else "COPY LOG", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = onRestart,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Text(if (isId) "BUKA ULANG" else "RESTART APP", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
