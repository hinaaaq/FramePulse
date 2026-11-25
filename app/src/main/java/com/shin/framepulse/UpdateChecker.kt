package com.shin.framepulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.util.Locale

data class AppUpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val changelog: String = "",
    val apkSizeLabel: String = "",
    val publishedDate: String = ""
)

object UpdateChecker {
    private const val RELEASES_API = "https://api.github.com/repos/hinaaaq/FramePulse/releases/latest"
    private const val DEFAULT_RELEASE_URL = "https://github.com/hinaaaq/FramePulse/releases/latest"
    private val TRUSTED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    fun isTrustedReleaseUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        return host in TRUSTED_HOSTS
    }

    suspend fun checkLatest(
        context: Context,
        ignoreSkipped: Boolean = false
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        runCatching {
            val currentVer = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Throwable) {
                "1.0.0"
            }

            val c = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FramePulse-Android/$currentVer")
            }
            conn = c
            if (c.responseCode !in 200..299) return@runCatching null

            val body = c.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                return@runCatching null
            }
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) return@runCatching null

            val latestClean = tagName.removePrefix("v").removePrefix("V")
            val currentClean = currentVer.removePrefix("v").removePrefix("V")
            if (!isNewerVersion(latestClean, currentClean)) return@runCatching null

            val latestLabel = "v$latestClean"
            if (!ignoreSkipped && Prefs.skippedUpdateVersion.equals(latestLabel, ignoreCase = true)) {
                return@runCatching null
            }

            val htmlCandidate = json.optString("html_url", DEFAULT_RELEASE_URL)
            var apkUrl = if (isTrustedReleaseUrl(htmlCandidate)) htmlCandidate else DEFAULT_RELEASE_URL
            var apkSizeLabel = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val dl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && isTrustedReleaseUrl(dl)) {
                        apkUrl = dl
                        val sizeBytes = asset.optLong("size", 0L)
                        if (sizeBytes > 0L) {
                            val mb = sizeBytes / (1024.0 * 1024.0)
                            apkSizeLabel = String.format(Locale.US, "%.1f MB", mb)
                        }
                        break
                    }
                }
            }

            val publishedRaw = json.optString("published_at", "").trim()
            val publishedDate = if (publishedRaw.length >= 10) publishedRaw.substring(0, 10) else ""

            val isId = AppStrings.resolveLangCode() == "id"
            val fallbackNotes = if (isId) {
                "Perbaikan performa, stabilitas, dan dukungan perangkat terbaru."
            } else {
                "Performance improvements, stability fixes, and latest device support."
            }
            val rawNotes = json.optString("body", "").trim()
            val cleanedNotes = cleanMarkdown(rawNotes).ifBlank { fallbackNotes }

            AppUpdateInfo(
                latestVersion = latestLabel,
                currentVersion = "v$currentClean",
                downloadUrl = apkUrl,
                changelog = cleanedNotes,
                apkSizeLabel = apkSizeLabel,
                publishedDate = publishedDate
            )
        }.also {
            runCatching { conn?.disconnect() }
        }.getOrNull()
    }

    private fun cleanMarkdown(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lines()
            .map { line ->
                var t = line.trim()
                t = t.replace(Regex("^#{1,6}\\s*"), "")
                t = t.replace(Regex("^[-*]\\s+"), "• ")
                t = t.replace("**", "").replace("__", "").replace("`", "")
                t
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestCore = latest.substringBefore("-").substringBefore("+")
        val currentCore = current.substringBefore("-").substringBefore("+")
        val lParts = latestCore.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = currentCore.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
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
    val isId = AppStrings.resolveLangCode() == "id"
    val metaParts = buildList {
        add("${info.currentVersion} → ${info.latestVersion}")
        if (info.apkSizeLabel.isNotBlank()) add(info.apkSizeLabel)
        if (info.publishedDate.isNotBlank()) add(info.publishedDate)
    }

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
                        text = if (isId) "Update Tersedia" else "Update Available",
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = info.latestVersion,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metaParts.joinToString(" • "),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (isId) "Apa yang baru:" else "What's new:",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = info.changelog,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (isId) "Lewati versi ${info.latestVersion} (jangan ingatkan lagi)" else "Skip ${info.latestVersion} (don't remind again)",
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            Prefs.skippedUpdateVersion = info.latestVersion
                            Toast.makeText(
                                context,
                                if (isId) "Notifikasi ${info.latestVersion} dilewati" else "Skipped ${info.latestVersion} update",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                        .padding(vertical = 2.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isId) "Nanti" else "Later",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeUrl = if (UpdateChecker.isTrustedReleaseUrl(info.downloadUrl)) {
                        info.downloadUrl
                    } else {
                        "https://github.com/hinaaaq/FramePulse/releases/latest"
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
                    }.onFailure {
                        Toast.makeText(
                            context,
                            if (isId) "Gagal membuka browser" else "Failed to open browser",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    text = "Download APK",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    )
}
