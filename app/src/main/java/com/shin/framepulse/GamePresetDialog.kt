package com.shin.framepulse

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shin.framepulse.v110.FramePulseV110Bridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GamePresetDialog(
    isPro: Boolean,
    onApplied: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isId = AppStrings.resolveLangCode() == "id"
    var busyId by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121826))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (isId) "Preset Per-Game (Shizuku)" else "Per-Game Presets (Shizuku)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    if (isId) "Eksekusi langsung Android Game Mode & GPU downscale untuk game yang terpasang."
                    else "Applies Android Game Mode & GPU resolution scaling directly to installed games.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GameProfileEngine.presets) { preset ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(preset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(preset.summary, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val prompt = ShizukuShell.ensureReadyOrPrompt(context)
                                            if (prompt != null) {
                                                Toast.makeText(context, prompt, Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            busyId = preset.id
                                            scope.launch {
                                                val (ok, msg) = withContext(Dispatchers.IO) {
                                                    val res = GameBooster.applyGamePreset(preset)
                                                    if (res.first) {
                                                        runCatching { FramePulseV110Bridge(context).selectGameProfile(preset.name) }
                                                    }
                                                    res
                                                }
                                                busyId = null
                                                if (ok) onApplied()
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = busyId == null,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text(
                                            if (busyId == preset.id) {
                                                if (isId) "Menerapkan..." else "Applying..."
                                            } else {
                                                if (isId) "Terapkan" else "Apply"
                                            },
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val prompt = ShizukuShell.ensureReadyOrPrompt(context)
                                            if (prompt != null) {
                                                Toast.makeText(context, prompt, Toast.LENGTH_SHORT).show()
                                                return@OutlinedButton
                                            }
                                            busyId = preset.id
                                            scope.launch {
                                                val (_, msg) = withContext(Dispatchers.IO) { GameBooster.resetGamePreset(preset) }
                                                busyId = null
                                                onApplied()
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = busyId == null,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Reset", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isId) "Tutup" else "Close", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
