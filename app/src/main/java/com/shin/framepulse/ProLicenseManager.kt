package com.shin.framepulse

import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class LicenseState {
    FREE,
    TRIAL_ACTIVE,
    PRO_ACTIVE,
    EXPIRED
}

data class ProStatus(
    val state: LicenseState,
    val isPro: Boolean,
    val expiryLabel: String = "",
    val tierLabel: String = "",
    val canStartTrial: Boolean = false
)

data class ProPlanOption(
    val id: String,
    val priceIdr: String,
    val priceUsd: String,
    val amountIdr: Int,
    val badgeId: String,
    val badgeEn: String
)

object ProLicenseManager {

    const val CLOUD_API_BASE = "https://framepulse-api.senoyasin726.workers.dev"
    const val DONATE_URL_ID = "https://tako.id/Lazyperson"
    const val DONATE_URL_GLOBAL = ""

    // ECDSA P-256 Public Key (SPKI) — safe for public repositories (verification only)
    private const val PUBLIC_KEY_SPKI_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYJliXvlThDik1RmOd8rYtbix18UCXEVTBzrsxnYZMOAvvkhBHJgqS9h+Nn+fvg4SaPDbVdrVpkKMd5zoHi5sEQ=="

    private const val TRIAL_DURATION_MS = 3L * 24L * 60L * 60L * 1000L

    private val OWNER_EMAIL_HASHES = setOf(
        "2b62710c0372adb002ffd2ce7004db2e41785da7bf8c7cf5df2d9908e68a5dc8",
        "1d9e68197b96aafc6eba18df3d5cb9763f8ec1f04691db9ea656fa46eb792ae4"
    )

    val plans = listOf(
        ProPlanOption("1M", "Rp 10.000", "$1.99", 10000, "COBA", "FLEX"),
        ProPlanOption("6M", "Rp 39.000", "$5.99", 39000, "HEMAT 35%", "SAVE 50%"),
        ProPlanOption("1Y", "Rp 59.000", "$9.99", 59000, "TERLARIS", "BEST VALUE"),
        ProPlanOption("LIFETIME", "Rp 149.000", "$19.99", 149000, "1X BAYAR", "ONE-TIME")
    )

    fun isOwnerEmail(email: String): Boolean {
        val clean = email.trim().lowercase(Locale.US)
        if (clean.isBlank()) return false
        val digest = MessageDigest.getInstance("SHA-256").digest("fp-owner:$clean".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex in OWNER_EMAIL_HASHES
    }

    @Volatile private var cachedKeyStr: String = "__INIT__"
    @Volatile private var cachedEmailStr: String = "__INIT__"
    @Volatile private var cachedStatus: ProStatus? = null
    @Volatile private var cachedCheckDay: Int = -1

    fun isCloudConfigured(): Boolean {
        return CLOUD_API_BASE.startsWith("https://") && !CLOUD_API_BASE.contains("USERNAME_KAMU")
    }

    fun isProUnlocked(): Boolean = getStatus().isPro

    fun getStatus(forceRefresh: Boolean = false): ProStatus {
        val storedKey = try { Prefs.proLicenseKey } catch (_: Throwable) { "" }
        val linkedEmail = try { Prefs.linkedGoogleEmail } catch (_: Throwable) { "" }
        val today = currentYyyyMmDd()
        val cached = cachedStatus
        if (!forceRefresh && cached != null && cachedKeyStr == storedKey && cachedEmailStr == linkedEmail && cachedCheckDay == today && cached.state != LicenseState.TRIAL_ACTIVE) {
            return cached
        }

        val computed = computeStatus(storedKey, linkedEmail, today)
        cachedKeyStr = storedKey
        cachedEmailStr = linkedEmail
        cachedCheckDay = today
        cachedStatus = computed
        return computed
    }

    private fun computeStatus(storedKey: String, linkedEmail: String, todayYmd: Int): ProStatus {
        if (isOwnerEmail(linkedEmail)) {
            return ProStatus(LicenseState.PRO_ACTIVE, true, "Lifetime", "OWNER", false)
        }

        val trialStart = try { Prefs.proTrialStartMs } catch (_: Throwable) { 0L }
        val tierSaved = try { Prefs.proTierName } catch (_: Throwable) { "" }
        val canTrial = (trialStart == 0L)

        if (storedKey.isNotBlank()) {
            val expiryYmd = verifyAndExtractExpiryYmd(storedKey)
            if (expiryYmd != null) {
                return if (expiryYmd >= todayYmd) {
                    val isLife = expiryYmd >= 20990101
                    val label = if (isLife) "Lifetime" else formatYmd(expiryYmd)
                    val tier = if (isLife) "LIFETIME" else tierSaved.ifBlank { "PRO" }
                    ProStatus(LicenseState.PRO_ACTIVE, true, label, tier, false)
                } else {
                    ProStatus(LicenseState.EXPIRED, false, formatYmd(expiryYmd), tierSaved, canTrial)
                }
            }
        }

        if (trialStart > 0L) {
            val nowMs = System.currentTimeMillis()
            val elapsed = (nowMs - trialStart).coerceAtLeast(0L)
            if (elapsed < TRIAL_DURATION_MS) {
                val remHours = ((TRIAL_DURATION_MS - elapsed) / (60L * 60L * 1000L)).coerceAtLeast(1L)
                val label = if (remHours >= 24L) "${remHours / 24L}d ${remHours % 24L}h" else "${remHours}h"
                return ProStatus(LicenseState.TRIAL_ACTIVE, true, label, "TRIAL", false)
            }
        }

        return ProStatus(LicenseState.FREE, false, "", "", canTrial)
    }

    fun startFreeTrial(): Boolean {
        val trialStart = try { Prefs.proTrialStartMs } catch (_: Throwable) { -1L }
        if (trialStart != 0L) return false
        Prefs.proTrialStartMs = System.currentTimeMillis()
        getStatus(forceRefresh = true)
        return true
    }

    sealed class RedeemResult {
        data class Success(val expiryLabel: String) : RedeemResult()
        data class Expired(val expiryLabel: String) : RedeemResult()
        object Invalid : RedeemResult()
    }

    fun redeemKey(rawInput: String, tierHint: String = ""): RedeemResult {
        val cleaned = rawInput.trim().replace(Regex("\\s+"), "")
        val expiryYmd = verifyAndExtractExpiryYmd(cleaned) ?: return RedeemResult.Invalid
        val today = currentYyyyMmDd()
        val label = if (expiryYmd >= 20990101) "Lifetime" else formatYmd(expiryYmd)
        if (expiryYmd < today) {
            return RedeemResult.Expired(label)
        }
        Prefs.proLicenseKey = cleaned
        if (tierHint.isNotBlank()) Prefs.proTierName = tierHint
        getStatus(forceRefresh = true)
        return RedeemResult.Success(label)
    }

    sealed class CloudSyncResult {
        data class Activated(val expiryLabel: String, val tier: String) : CloudSyncResult()
        object NotFound : CloudSyncResult()
        object Expired : CloudSyncResult()
        object DeviceLimit : CloudSyncResult()
        object NoEmail : CloudSyncResult()
        object ServerNotConfigured : CloudSyncResult()
        data class Error(val detail: String) : CloudSyncResult()
    }

    fun getDeviceHash(context: Context): String {
        val raw = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest("fp-dev-$raw".toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun buildGlobalCheckoutUrl(email: String): String {
        if (DONATE_URL_GLOBAL.isBlank()) return ""
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) return DONATE_URL_GLOBAL
        val sep = if (DONATE_URL_GLOBAL.contains("?")) "&" else "?"
        val enc = URLEncoder.encode(cleanEmail, "UTF-8")
        return "$DONATE_URL_GLOBAL${sep}email=$enc"
    }

    fun syncWithCloud(context: Context, emailOverride: String = ""): CloudSyncResult {
        val email = (emailOverride.ifBlank { Prefs.linkedGoogleEmail }).trim().lowercase(Locale.US)
        if (email.isBlank() || !email.contains("@")) return CloudSyncResult.NoEmail
        if (isOwnerEmail(email)) {
            Prefs.linkedGoogleEmail = email
            getStatus(forceRefresh = true)
            return CloudSyncResult.Activated("Lifetime", "OWNER")
        }
        if (!isCloudConfigured()) return CloudSyncResult.ServerNotConfigured

        var conn: HttpURLConnection? = null
        return try {
            val encEmail = URLEncoder.encode(email, "UTF-8")
            val devHash = getDeviceHash(context)
            val endpoint = "${CLOUD_API_BASE.trimEnd('/')}/api/check?email=$encEmail&device=$devHash"
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FramePulse-Android/1.2")
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { s -> BufferedReader(InputStreamReader(s)).readText() } ?: ""
            if (body.isBlank()) return CloudSyncResult.Error("HTTP $code")

            val json = JSONObject(body)
            val active = json.optBoolean("active", false)
            val reason = json.optString("reason", "")

            if (active) {
                val token = json.optString("token", "")
                val tier = json.optString("tier", "PRO")
                when (val r = redeemKey(token, tier)) {
                    is RedeemResult.Success -> {
                        Prefs.lastCloudSyncMs = System.currentTimeMillis()
                        CloudSyncResult.Activated(r.expiryLabel, tier)
                    }
                    is RedeemResult.Expired -> CloudSyncResult.Expired
                    RedeemResult.Invalid -> CloudSyncResult.Error("Invalid server signature")
                }
            } else {
                when (reason) {
                    "device_limit" -> CloudSyncResult.DeviceLimit
                    "expired" -> CloudSyncResult.Expired
                    else -> CloudSyncResult.NotFound
                }
            }
        } catch (t: Throwable) {
            CloudSyncResult.Error(t.message ?: "network error")
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun verifyAndExtractExpiryYmd(token: String): Int? {
        return try {
            if (!token.startsWith("FP-")) return null
            val b64 = token.removePrefix("FP-")
            val raw = Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            if (raw.size != 70) return null

            val payload = raw.copyOfRange(0, 6)
            val sig64 = raw.copyOfRange(6, 70)
            val derSig = raw64ToDer(sig64) ?: return null

            val pubBytes = Base64.decode(PUBLIC_KEY_SPKI_B64, Base64.DEFAULT)
            val pubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pubBytes))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(payload)
            if (!verifier.verify(derSig)) return null

            val expiryYmd = ByteBuffer.wrap(payload, 0, 4).int
            if (expiryYmd < 20240101 || expiryYmd > 20991231) return null
            expiryYmd
        } catch (_: Throwable) {
            null
        }
    }

    private fun raw64ToDer(sig64: ByteArray): ByteArray? {
        if (sig64.size != 64) return null
        fun encodePositiveInt(slice: ByteArray): ByteArray {
            var offset = 0
            while (offset < slice.size - 1 && slice[offset].toInt() == 0) {
                offset++
            }
            val trimmed = slice.copyOfRange(offset, slice.size)
            val needsZero = (trimmed[0].toInt() and 0x80) != 0
            val out = ByteArray(2 + trimmed.size + if (needsZero) 1 else 0)
            out[0] = 0x02
            out[1] = (trimmed.size + if (needsZero) 1 else 0).toByte()
            var pos = 2
            if (needsZero) out[pos++] = 0x00
            System.arraycopy(trimmed, 0, out, pos, trimmed.size)
            return out
        }
        val rDer = encodePositiveInt(sig64.copyOfRange(0, 32))
        val sDer = encodePositiveInt(sig64.copyOfRange(32, 64))
        val seqLen = rDer.size + sDer.size
        val der = ByteArray(2 + seqLen)
        der[0] = 0x30
        der[1] = seqLen.toByte()
        System.arraycopy(rDer, 0, der, 2, rDer.size)
        System.arraycopy(sDer, 0, der, 2 + rDer.size, sDer.size)
        return der
    }

    private fun currentYyyyMmDd(): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun formatYmd(ymd: Int): String {
        return try {
            val utc = TimeZone.getTimeZone("UTC")
            val parser = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = utc }
            val parsed = parser.parse(ymd.toString())
            if (parsed != null) {
                SimpleDateFormat("dd MMM yyyy", Locale.US).apply { timeZone = utc }.format(parsed)
            } else {
                ymd.toString()
            }
        } catch (_: Throwable) {
            ymd.toString()
        }
    }
}
