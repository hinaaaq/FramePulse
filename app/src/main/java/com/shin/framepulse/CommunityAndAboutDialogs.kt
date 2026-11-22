package com.shin.framepulse

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LeaderboardEntry(
    val nickname: String,
    val device: String,
    val game: String,
    val avgFps: Int,
    val tempC: Int,
    val score: Int,
    val isPro: Boolean,
    val isOwner: Boolean
)

suspend fun fetchLeaderboardTop(): Result<List<LeaderboardEntry>> = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("${ProLicenseManager.CLOUD_API_BASE.trimEnd('/')}/api/leaderboard/top")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val arr = json.optJSONArray("entries")
        val out = mutableListOf<LeaderboardEntry>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    LeaderboardEntry(
                        nickname = o.optString("nickname", "Player"),
                        device = o.optString("device", "Android"),
                        game = o.optString("game", "Valorant Mobile"),
                        avgFps = o.optInt("avgFps", 60),
                        tempC = o.optInt("tempC", 38),
                        score = o.optInt("score", 600),
                        isPro = o.optBoolean("isPro", false),
                        isOwner = o.optBoolean("isOwner", false)
                    )
                )
            }
        }
        out
    }
}

suspend fun submitLeaderboardScore(
    email: String,
    nickname: String,
    game: String,
    avgFps: Int,
    tempC: Int,
    isPro: Boolean
): Result<Pair<Int, List<LeaderboardEntry>>> = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("${ProLicenseManager.CLOUD_API_BASE.trimEnd('/')}/api/leaderboard/submit")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val payload = JSONObject()
            .put("email", email)
            .put("nickname", nickname)
            .put("device", deviceName)
            .put("game", game)
            .put("avgFps", avgFps)
            .put("tempC", tempC)
            .put("isPro", isPro)
            .toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val rank = json.optInt("rank", 0)
        val arr = json.optJSONArray("entries")
        val out = mutableListOf<LeaderboardEntry>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    LeaderboardEntry(
                        nickname = o.optString("nickname", "Player"),
                        device = o.optString("device", "Android"),
                        game = o.optString("game", "Valorant Mobile"),
                        avgFps = o.optInt("avgFps", 60),
                        tempC = o.optInt("tempC", 38),
                        score = o.optInt("score", 600),
                        isPro = o.optBoolean("isPro", false),
                        isOwner = o.optBoolean("isOwner", false)
                    )
                )
            }
        }
        rank to out
    }
}

fun saveFlexCardToGallery(
    context: Context,
    nickname: String,
    game: String,
    avgFps: Int,
    tempC: Int,
    rank: Int,
    isPro: Boolean,
    isOwner: Boolean
): Boolean {
    return runCatching {
        val w = 1080
        val h = 620
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColors = if (isOwner) {
            intArrayOf(0xFF1A0B12.toInt(), 0xFF2B1218.toInt(), 0xFF1F1708.toInt())
        } else {
            intArrayOf(0xFF090D16.toInt(), 0xFF111C33.toInt(), 0xFF0B2528.toInt())
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), bgColors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 36f, 36f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isOwner) 8f else 6f
            color = when {
                isOwner -> 0xFFFACC15.toInt()
                isPro -> 0xFF10B981.toInt()
                else -> 0xFF38BDF8.toInt()
            }
        }
        canvas.drawRoundRect(RectF(12f, 12f, w - 12f, h - 12f), 32f, 32f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOwner) 0xFFFACC15.toInt() else 0xFF34D399.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBD5E1.toInt()
            textSize = 30f
        }
        val bigStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOwner) 0xFFFDE047.toInt() else 0xFF38BDF8.toInt()
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val headerText = if (isOwner) "FRAMEPULSE // FOUNDER & DEV EDITION" else "FRAMEPULSE PRO // BENCHMARK CARD"
        canvas.drawText(headerText, 54f, 88f, accentPaint)
        val displayNick = if (isOwner) "${nickname.ifBlank { "Shin" }} [OWNER]" else nickname.ifBlank { "Player" }
        canvas.drawText(displayNick, 54f, 165f, titlePaint)
        canvas.drawText("Game: $game  •  Device: ${Build.MODEL}", 54f, 215f, subPaint)

        canvas.drawText("$avgFps FPS", 54f, 365f, bigStatPaint)
        canvas.drawText("Suhu: ${tempC}°C", 54f, 435f, titlePaint)
        val rankText = when {
            isOwner && rank > 0 -> "OWNER PRIVILEGE • GLOBAL RANK #$rank"
            isOwner -> "VERIFIED DEVELOPER / OWNER"
            rank > 0 -> "GLOBAL RANK #$rank"
            isPro -> "PRO VERIFIED"
            else -> "COMMUNITY TIER"
        }
        canvas.drawText(rankText, 54f, 505f, accentPaint)
        canvas.drawText("github.com/hinaaaq/FramePulse", 54f, 570f, subPaint)

        val filename = "FramePulse_Flex_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FramePulse")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)
}

@Composable
fun LeaderboardDialog(email: String, isPro: Boolean, isOwner: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var myRank by remember { mutableStateOf(0) }
    var nickname by remember { mutableStateOf(if (isOwner) "Shin" else Build.MODEL.take(14)) }
    var selectedGame by remember { mutableStateOf("Valorant Mobile") }
    var fpsInput by remember { mutableStateOf("60") }
    var tempInput by remember { mutableStateOf("38") }

    val games = listOf("Valorant Mobile", "MLBB", "PUBG Mobile", "Free Fire", "HOK", "Genshin")

    LaunchedEffect(Unit) {
        loading = true
        fetchLeaderboardTop().onSuccess { entries = it }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Leaderboard & Flex Card", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    if (isOwner) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFF59E0B))))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("DEV", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                        }
                    }
                }
                Text("Adu kestabilan FPS & suhu adem antar pengguna FramePulse.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 18) nickname = it },
                    label = { Text("Nickname", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fpsInput,
                        onValueChange = { fpsInput = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Avg FPS", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tempInput,
                        onValueChange = { tempInput = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Suhu °C", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val idx = (games.indexOf(selectedGame) + 1) % games.size
                            selectedGame = games[idx]
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("Game: $selectedGame", color = Color(0xFF38BDF8), fontSize = 11.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            if (submitting) return@Button
                            submitting = true
                            val fps = fpsInput.toIntOrNull() ?: 60
                            val temp = tempInput.toIntOrNull() ?: 38
                            scope.launch {
                                submitLeaderboardScore(email, nickname, selectedGame, fps, temp, isPro || isOwner)
                                    .onSuccess { (rank, list) ->
                                        myRank = rank
                                        entries = list
                                        Toast.makeText(context, "Skor masuk! Rank kamu #$rank", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        Toast.makeText(context, "Gagal kirim skor (cek internet / deploy Worker)", Toast.LENGTH_SHORT).show()
                                    }
                                submitting = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text(if (submitting) "..." else "Kirim Skor", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        val fps = fpsInput.toIntOrNull() ?: 60
                        val temp = tempInput.toIntOrNull() ?: 38
                        val ok = saveFlexCardToGallery(context, nickname, selectedGame, fps, temp, myRank, isPro, isOwner)
                        Toast.makeText(
                            context,
                            if (ok) "Kartu Pamer disimpan ke Galeri (Pictures/FramePulse)!" else "Gagal menyimpan kartu",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOwner) Color(0xFFD97706) else Color(0xFF0EA5E9)
                    )
                ) {
                    Text(
                        if (isOwner) "Simpan Kartu Pamer (FOUNDER GOLD)" else "Simpan Kartu Pamer (Share ke TikTok/WA)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("TOP RANKING GLOBAL", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF34D399))
                    }
                } else if (entries.isEmpty()) {
                    Text("Belum ada skor. Jadilah Rank #1 pertama!", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 210.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(entries) { idx, item ->
                            val rankColor = when (idx) {
                                0 -> Color(0xFFFACC15)
                                1 -> Color(0xFFE2E8F0)
                                2 -> Color(0xFFFB923C)
                                else -> Color(0xFF94A3B8)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (item.isOwner) Color(0xFF2B161B) else Color(0xFF1E293B))
                                    .border(
                                        width = if (item.isOwner) 1.dp else 0.dp,
                                        color = if (item.isOwner) Color(0xFFFACC15) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${idx + 1}", color = rankColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(item.nickname, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (item.isOwner) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFF59E0B))))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text("OWNER", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                                            }
                                        } else if (item.isPro) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF10B981))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("PRO", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    Text("${item.game} • ${item.device}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${item.avgFps} FPS", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${item.tempC}°C • ${item.score} pts", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Tutup", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
fun AboutAppDialog(isOwner: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            if (isOwner) listOf(Color(0xFF1F0E14), Color(0xFF0F172A))
                            else listOf(Color(0xFF0F172A), Color(0xFF0B1F2A))
                        )
                    )
                    .border(1.dp, if (isOwner) Color(0xFFFACC15) else Color(0xFF1E293B), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOwner) Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFFFACC15)))
                            else Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0EA5E9)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("FP", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("FramePulse Pro", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                if (isOwner) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFF59E0B))))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("VERIFIED OWNER / DEV PRIVILEGE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                } else {
                    Text("Real Shizuku FPS & Thermal Engine", color = Color(0xFF34D399), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Owner & Developer: Shin (hinaaaq)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    Text("Engine: Shizuku Shell • Midtrans QRIS • ECDSA", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hinaaaq/FramePulse"))
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "Gagal membuka browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Buka GitHub Owner (hinaaaq/FramePulse)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}
