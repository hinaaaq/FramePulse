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
import androidx.compose.ui.text.font.FontFamily
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
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_BENCHMARK_SECONDS = 15

data class LeaderboardEntry(
    val nickname: String,
    val device: String,
    val game: String,
    val avgFps: Int,
    val onePctLow: Int = 0,
    val tempC: Int,
    val score: Int,
    val source: String = "",
    val isPro: Boolean,
    val isOwner: Boolean
)

private fun computeLeaderboardProof(
    email: String,
    device: String,
    game: String,
    avgFps: Int,
    onePctLow: Int,
    tempC: Int,
    durationSec: Int,
    source: String
): String {
    val cleanEmail = email.trim().lowercase(Locale.US)
    val raw = "fp-ac-v1:$cleanEmail:$device:$game:$avgFps:$onePctLow:$tempC:$durationSec:$source"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun detectDefaultGameName(): String {
    val pkg = Prefs.lastGamePackage.lowercase(Locale.US)
    return when {
        pkg.contains("valorant") || pkg.contains("codev") -> "Valorant Mobile"
        pkg.contains("mobile.legends") -> "MLBB"
        pkg.contains("pubg") || pkg.contains("tencent.ig") -> "PUBG Mobile"
        pkg.contains("freefire") -> "Free Fire"
        pkg.contains("sgame") -> "HOK"
        pkg.contains("genshin") -> "Genshin"
        else -> "Valorant Mobile"
    }
}

suspend fun fetchLeaderboardTop(): Result<List<LeaderboardEntry>> = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("${ProLicenseManager.CLOUD_API_BASE.trimEnd('/')}/api/leaderboard/top")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
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
                        onePctLow = o.optInt("onePctLow", 0),
                        tempC = o.optInt("tempC", 38),
                        score = o.optInt("score", 600),
                        source = o.optString("source", ""),
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
    benchmark: BenchmarkSummary,
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
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            .replace(Regex("[^a-zA-Z0-9 _.-]"), "")
            .trim()
            .take(24)
            .ifBlank { "Android" }
        val avgFps = benchmark.avgFps.roundToInt().coerceIn(10, 144)
        val onePctLow = benchmark.onePercentLowFps.coerceIn(1, avgFps)
        val tempC = benchmark.peakTempC.roundToInt().coerceIn(20, 65)
        val durationSec = benchmark.durationSec.coerceAtLeast(1)
        val source = if (benchmark.sourceLabel == "SF") "SF" else "STD"
        val proof = computeLeaderboardProof(
            email = email,
            device = deviceName,
            game = game,
            avgFps = avgFps,
            onePctLow = onePctLow,
            tempC = tempC,
            durationSec = durationSec,
            source = source
        )

        val payload = JSONObject()
            .put("email", email)
            .put("nickname", nickname)
            .put("device", deviceName)
            .put("game", game)
            .put("avgFps", avgFps)
            .put("onePctLow", onePctLow)
            .put("tempC", tempC)
            .put("durationSec", durationSec)
            .put("source", source)
            .put("proof", proof)
            .put("isPro", isPro)
            .toString()

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val json = JSONObject(text.ifBlank { "{}" })
        if (code !in 200..299 || !json.optBoolean("ok", false)) {
            error(json.optString("error", "HTTP $code"))
        }

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
                        onePctLow = o.optInt("onePctLow", 0),
                        tempC = o.optInt("tempC", 38),
                        score = o.optInt("score", 600),
                        source = o.optString("source", ""),
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
    onePctLow: Int,
    tempC: Int,
    durationSec: Int,
    sourceLabel: String,
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
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOwner) 0xFFFACC15.toInt() else 0xFF34D399.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBD5E1.toInt()
            textSize = 28f
        }
        val bigStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOwner) 0xFFFDE047.toInt() else 0xFF38BDF8.toInt()
            textSize = 92f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val headerText = if (isOwner) "FRAMEPULSE // FOUNDER & DEV EDITION" else "FRAMEPULSE // VERIFIED BENCHMARK"
        canvas.drawText(headerText, 54f, 84f, accentPaint)
        val displayNick = if (isOwner) "${nickname.ifBlank { "Shin" }} [OWNER]" else nickname.ifBlank { "Player" }
        canvas.drawText(displayNick, 54f, 160f, titlePaint)
        canvas.drawText("Game: $game  •  Device: ${Build.MODEL}", 54f, 210f, subPaint)

        canvas.drawText("$avgFps FPS", 54f, 355f, bigStatPaint)
        canvas.drawText("1% Low: $onePctLow FPS  •  Peak: ${tempC}°C  •  ${durationSec}s ($sourceLabel)", 54f, 428f, subPaint)
        val rankText = when {
            isOwner && rank > 0 -> "OWNER PRIVILEGE • GLOBAL RANK #$rank"
            isOwner -> "VERIFIED DEVELOPER / OWNER"
            rank > 0 -> "GLOBAL RANK #$rank"
            isPro -> "SUPPORTER"
            else -> "VERIFIED RECORDING"
        }
        canvas.drawText(rankText, 54f, 500f, accentPaint)
        canvas.drawText("github.com/hinaaaq/FramePulse", 54f, 568f, subPaint)

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
    var selectedGame by remember { mutableStateOf(detectDefaultGameName()) }

    val validSessions = remember {
        TelemetryHub.loadHistory().filter {
            it.durationSec >= MIN_BENCHMARK_SECONDS && it.avgFps >= 10f && it.peakTempC >= 20f
        }
    }
    var selectedSessionIdx by remember { mutableIntStateOf(0) }
    val activeBenchmark = validSessions.getOrNull(selectedSessionIdx)

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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("VERIFIED", color = Color(0xFF34D399), fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                    }
                    if (isOwner) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFF59E0B))))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("DEV", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                        }
                    }
                }
                Text(
                    "Anti-Cheat aktif: FPS & suhu dikunci otomatis dari hasil Benchmark Recorder.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 18) nickname = it },
                    label = { Text("Nickname", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                if (activeBenchmark == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1B2E))
                            .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "Belum ada rekaman Benchmark valid",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Rekam sesi game minimal ${MIN_BENCHMARK_SECONDS} detik lewat Benchmark Recorder di tab Monitor agar skor terverifikasi.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A1424))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LOCKED BENCHMARK • ${activeBenchmark.timestamp}",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (validSessions.size > 1) {
                                TextButton(
                                    onClick = { selectedSessionIdx = (selectedSessionIdx + 1) % validSessions.size },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        "Sesi ${selectedSessionIdx + 1}/${validSessions.size} ↻",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricStatBox(
                                "AVG FPS",
                                String.format(Locale.US, "%.1f", activeBenchmark.avgFps),
                                Color(0xFF34D399),
                                Modifier.weight(1f)
                            )
                            MetricStatBox(
                                "1% LOW",
                                "${activeBenchmark.onePercentLowFps}",
                                Color(0xFFFBBF24),
                                Modifier.weight(1f)
                            )
                            MetricStatBox(
                                "PEAK °C",
                                String.format(Locale.US, "%.1f°", activeBenchmark.peakTempC),
                                Color(0xFF38BDF8),
                                Modifier.weight(1f)
                            )
                            MetricStatBox(
                                "SRC",
                                "${activeBenchmark.durationSec}s ${activeBenchmark.sourceLabel}",
                                Color(0xFFC084FC),
                                Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
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
                        enabled = activeBenchmark != null && !submitting,
                        onClick = {
                            val bench = activeBenchmark ?: return@Button
                            submitting = true
                            scope.launch {
                                submitLeaderboardScore(email, nickname, selectedGame, bench, isPro || isOwner)
                                    .onSuccess { (rank, list) ->
                                        myRank = rank
                                        entries = list
                                        Toast.makeText(context, "Skor terverifikasi masuk! Rank #$rank", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { err ->
                                        Toast.makeText(context, "Ditolak / gagal: ${err.message}", Toast.LENGTH_LONG).show()
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
                    enabled = activeBenchmark != null,
                    onClick = {
                        val bench = activeBenchmark ?: return@Button
                        val ok = saveFlexCardToGallery(
                            context = context,
                            nickname = nickname,
                            game = selectedGame,
                            avgFps = bench.avgFps.roundToInt(),
                            onePctLow = bench.onePercentLowFps,
                            tempC = bench.peakTempC.roundToInt(),
                            durationSec = bench.durationSec,
                            sourceLabel = bench.sourceLabel,
                            rank = myRank,
                            isPro = isPro,
                            isOwner = isOwner
                        )
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
                        modifier = Modifier.heightIn(max = 200.dp),
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
                                                Text("SUPPORTER", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    val srcTag = if (item.source.isNotBlank()) " • ${item.source}" else ""
                                    Text("${item.game} • ${item.device}$srcTag", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val lowTxt = if (item.onePctLow > 0) " (${item.onePctLow})" else ""
                                    Text("${item.avgFps} FPS$lowTxt", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    Text("Shizuku Telemetry & Thermal Engine", color = Color(0xFF34D399), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
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
