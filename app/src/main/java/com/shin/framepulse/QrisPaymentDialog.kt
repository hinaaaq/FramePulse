package com.shin.framepulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class QrisOrder(
    val orderId: String,
    val amount: Int,
    val tier: String,
    val qrUrl: String,
    val expiryTime: String
)

private const val MAX_QR_BYTES = 2 * 1024 * 1024

private fun isTrustedQrUrl(rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase().orEmpty()
    return host == "api.midtrans.com" ||
        host == "api.sandbox.midtrans.com" ||
        host.endsWith(".midtrans.com") ||
        host.endsWith(".veritrans.co.id")
}

suspend fun createMidtransQris(email: String, amount: Int): Result<QrisOrder> = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("${ProLicenseManager.CLOUD_API_BASE.trimEnd('/')}/api/qris/create")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val payload = JSONObject().put("email", email).put("amount", amount).toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (text.isBlank()) error("HTTP $code")
            val json = JSONObject(text)
            if (code !in 200..299 || !json.optBoolean("ok", false)) {
                error(json.optString("error", "HTTP $code"))
            }
            QrisOrder(
                orderId = json.getString("orderId"),
                amount = json.getInt("amount"),
                tier = json.getString("tier"),
                qrUrl = json.getString("qrUrl"),
                expiryTime = json.optString("expiryTime", "")
            )
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

suspend fun downloadQrBitmap(qrUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    if (!isTrustedQrUrl(qrUrl)) return@withContext null
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(qrUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
        }
        if (conn.responseCode !in 200..299) return@withContext null
        val bytes = conn.inputStream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_QR_BYTES) return@withContext null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { conn?.disconnect() }
    }
}

fun saveQrToGallery(context: Context, bitmap: Bitmap, orderId: String): Boolean {
    return runCatching {
        val safeOrderId = orderId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(48).ifBlank { "FP" }
        val filename = "FramePulse_QRIS_${safeOrderId}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FramePulse")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val written = context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (!written) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return false
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
fun QrisPaymentDialog(
    email: String,
    planLabel: String,
    amountIdr: Int,
    onCheckPaid: suspend () -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isId = AppStrings.resolveLangCode() == "id"
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf<QrisOrder?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(email, amountIdr) {
        loading = true
        errorMsg = null
        val res = createMidtransQris(email, amountIdr)
        res.onSuccess { ord ->
            order = ord
            val downloaded = downloadQrBitmap(ord.qrUrl)
            qrBitmap = downloaded
            if (downloaded == null) {
                errorMsg = if (isId) "Gagal memuat gambar QRIS" else "Failed to load QRIS image"
            }
        }.onFailure {
            errorMsg = it.message ?: if (isId) "Gagal menghubungi server QRIS" else "Failed to contact QRIS server"
        }
        loading = false
    }

    LaunchedEffect(order) {
        if (order != null) {
            repeat(60) {
                delay(5000)
                if (!checking) {
                    checking = true
                    val paid = runCatching { onCheckPaid() }.getOrDefault(false)
                    checking = false
                    if (paid) {
                        Toast.makeText(
                            context,
                            if (isId) "Terima kasih! Supporter Pass aktif." else "Thank you! Supporter Pass is now active.",
                            Toast.LENGTH_LONG
                        ).show()
                        onDismiss()
                        return@LaunchedEffect
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121826))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isId) "Bayar QRIS Otomatis" else "Instant QRIS Payment",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text("$planLabel • Rp $amountIdr", color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(email, color = Color(0xFF94A3B8), fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))

                val bmp = qrBitmap
                when {
                    loading -> {
                        CircularProgressIndicator(color = Color(0xFF34D399))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isId) "Membuat QRIS..." else "Generating QRIS...",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                    errorMsg != null -> {
                        Text(errorMsg.orEmpty(), color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                    bmp != null -> {
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "QRIS FramePulse",
                                modifier = Modifier.size(210.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isId) {
                                "Simpan QRIS lalu scan di DANA / GoPay / OVO / m-Banking. Status aktif otomatis setelah lunas."
                            } else {
                                "Save QRIS and scan via DANA / GoPay / OVO / Mobile Banking. Unlocks automatically once paid."
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !saving,
                            onClick = {
                                saving = true
                                val ordId = order?.orderId ?: "FP"
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        saveQrToGallery(context, bmp, ordId)
                                    }
                                    saving = false
                                    Toast.makeText(
                                        context,
                                        if (ok) {
                                            if (isId) "QRIS disimpan ke Galeri (Pictures/FramePulse)" else "QRIS saved to Gallery (Pictures/FramePulse)"
                                        } else {
                                            if (isId) "Gagal menyimpan QRIS" else "Failed to save QRIS"
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text(
                                if (isId) "Simpan QRIS ke Galeri" else "Save QRIS to Gallery",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            enabled = !checking,
                            onClick = {
                                if (checking) return@Button
                                checking = true
                                scope.launch {
                                    val paid = runCatching { onCheckPaid() }.getOrDefault(false)
                                    checking = false
                                    if (paid) {
                                        Toast.makeText(
                                            context,
                                            if (isId) "Terima kasih! Supporter Pass aktif." else "Thank you! Supporter Pass is now active.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isId) "Belum lunas. Coba lagi beberapa detik setelah bayar." else "Payment not detected yet. Try again shortly after paying.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Text(
                                if (checking) {
                                    if (isId) "Mengecek..." else "Checking..."
                                } else {
                                    if (isId) "Saya Sudah Bayar (Cek Sekarang)" else "I Have Paid (Check Now)"
                                },
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(if (isId) "Tutup" else "Close", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
