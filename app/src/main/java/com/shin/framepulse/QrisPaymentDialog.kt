package com.shin.framepulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.net.HttpURLConnection
import java.net.URL

data class QrisOrder(
    val orderId: String,
    val amount: Int,
    val qrUrl: String,
    val expiryTime: String
)

suspend fun createMidtransQris(email: String, amountIdr: Int): Result<QrisOrder> = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("${ProLicenseManager.CLOUD_API_BASE.trimEnd('/')}/api/qris/create")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val payload = JSONObject().put("email", email).put("amount", amountIdr).toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        if (!json.optBoolean("ok", false)) error(json.optString("error", "Gagal membuat QRIS"))
        QrisOrder(
            orderId = json.getString("orderId"),
            amount = json.getInt("amount"),
            qrUrl = json.getString("qrUrl"),
            expiryTime = json.optString("expiryTime", "")
        )
    }
}

suspend fun downloadQrBitmap(qrUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(qrUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

fun saveQrToGallery(context: Context, bitmap: Bitmap, orderId: String): Boolean {
    return runCatching {
        val filename = "QRIS_FramePulse_$orderId.png"
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
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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
    var loading by remember { mutableStateOf(true) }
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
            qrBitmap = downloadQrBitmap(ord.qrUrl)
            if (qrBitmap == null) errorMsg = "Gagal memuat gambar QRIS"
        }.onFailure {
            errorMsg = it.message ?: "Gagal menghubungi server QRIS"
        }
        loading = false
    }

    LaunchedEffect(order) {
        if (order != null) {
            repeat(60) {
                delay(5000)
                if (onCheckPaid()) {
                    Toast.makeText(context, "Pembayaran diterima! Pro Pass aktif.", Toast.LENGTH_LONG).show()
                    onDismiss()
                    return@LaunchedEffect
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
                Text("Bayar QRIS Otomatis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("$planLabel • Rp $amountIdr", color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(email, color = Color(0xFF94A3B8), fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))

                when {
                    loading -> {
                        CircularProgressIndicator(color = Color(0xFF34D399))
                        Spacer(Modifier.height(8.dp))
                        Text("Membuat QRIS...", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                    errorMsg != null -> {
                        Text(errorMsg!!, color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                    qrBitmap != null -> {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "QRIS FramePulse Pro",
                                modifier = Modifier.size(210.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Simpan QRIS lalu scan di DANA / GoPay / OVO / m-Banking. Pro otomatis aktif setelah lunas.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val ok = saveQrToGallery(context, qrBitmap!!, order?.orderId ?: "FP")
                                Toast.makeText(
                                    context,
                                    if (ok) "QRIS disimpan ke Galeri (Pictures/FramePulse)" else "Gagal menyimpan QRIS",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Simpan QRIS ke Galeri", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (checking) return@Button
                                checking = true
                                scope.launch {
                                    val paid = onCheckPaid()
                                    checking = false
                                    if (paid) {
                                        Toast.makeText(context, "Pembayaran diterima! Pro Pass aktif.", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Belum lunas. Coba lagi beberapa detik setelah bayar.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Text(if (checking) "Mengecek..." else "Cek Status Pembayaran", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}
