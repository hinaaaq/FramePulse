package com.shin.framepulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String
)

object UpdateChecker {
    private const val RELEASES_API = "https://api.github.com/repos/hinaaaq/FramePulse/releases/latest"

    suspend fun checkLatest(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val currentVer = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Throwable) {
                "1.0.0"
            }

            val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FramePulse-Android/$currentVer")
            }
            if (conn.responseCode !in 200..299) return@runCatching null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) return@runCatching null

            val latestClean = tagName.removePrefix("v").removePrefix("V")
            val currentClean = currentVer.removePrefix("v").removePrefix("V")
            if (!isNewerVersion(latestClean, currentClean)) return@runCatching null

            var apkUrl = json.optString("html_url", "https://github.com/hinaaaq/FramePulse/releases/latest")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val dl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && dl.startsWith("https://")) {
                        apkUrl = dl
                        break
                    }
                }
            }

            AppUpdateInfo(
                latestVersion = "v$latestClean",
                currentVersion = "v$currentClean",
                downloadUrl = apkUrl
            )
        }.getOrNull()
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val lParts = latest.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

@Composable
fun UpdateAvailableBanner(
    info: AppUpdateInfo,
    accent: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Update Tersedia: ${info.latestVersion}",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.5.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Terpasang ${info.currentVersion} • Install langsung tanpa uninstall",
                color = TextSecondary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nanti",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                    }.onFailure {
                        Toast.makeText(context, "Gagal membuka browser", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    text = "Update",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
