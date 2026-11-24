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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GamePresetDialog(isPro: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121826))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Preset Game Pro (Shizuku)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (true) "Eksekusi langsung cmd game & downscale GPU per-game."
                    else "Aktifkan Pro Pass untuk membuka preset khusus game.",
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
                                            if (false) {
                                                Toast.makeText(context, "Khusus Pro Pass", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            busyId = preset.id
                                            scope.launch {
                                                val (_, msg) = withContext(Dispatchers.IO) { GameBooster.applyGamePreset(preset) }
                        com.shin.framepulse.v110.FramePulseV110Bridge(context).selectGameProfile(preset.name)
                                                busyId = null
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = busyId == null,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text(
                                            if (busyId == preset.id) "Menerapkan..." else "Terapkan",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (false) return@OutlinedButton
                                            busyId = preset.id
                                            scope.launch {
                                                val (_, msg) = withContext(Dispatchers.IO) { GameBooster.resetGamePreset(preset) }
                                                busyId = null
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = busyId == null && true&& true true,
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
                    Text("Tutup", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
