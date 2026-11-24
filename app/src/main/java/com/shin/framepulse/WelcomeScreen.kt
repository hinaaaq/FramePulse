package com.shin.framepulse

import android.accounts.AccountManager
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private data class OnboardingPage(
    val badge: String,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
    val accent: Color
)

@Composable
fun WelcomeOnboardingScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageIndex by remember { mutableIntStateOf(0) }
    var isSigningIn by remember { mutableStateOf(false) }

    val pages = remember {
        listOf(
            OnboardingPage(
                badge = "FPS",
                title = "Selamat Datang di FramePulse",
                subtitle = "Monitor performa & True Game FPS real-time tanpa root langsung dari perangkat Android kamu.",
                bullets = listOf(
                    "Pembacaan True Game FPS akurat via Shizuku",
                    "Ringan, minim overhead, tanpa akses root",
                    "Mendukung Android 16 & layar high refresh rate"
                ),
                accent = Color(0xFF00E5FF)
            ),
            OnboardingPage(
                badge = "100%",
                title = "Semua Fitur Inti Gratis",
                subtitle = "Semua metrik HUD profesional dan optimasi game terbuka penuh tanpa paywall.",
                bullets = listOf(
                    "HUD lengkap: FPS, 1% Low, CPU, GPU, RAM, Watt & Net",
                    "Bebas pilih gaya HUD, grafik real-time & interval cepat",
                    "Preset per-game & Game Booster aktif untuk semua user"
                ),
                accent = Color(0xFF10B981)
            ),
            OnboardingPage(
                badge = "G",
                title = "Hubungkan Akun Google",
                subtitle = "Login dengan akun Google untuk pengalaman penuh dan sinkronisasi otomatis.",
                bullets = listOf(
                    "Kirim & simpan skor benchmark ke Global Leaderboard",
                    "Sinkronisasi otomatis status Supporter Pass (Donate Dev)",
                    "Backup identitas perangkat saat ganti atau reinstall app"
                ),
                accent = Color(0xFF60A5FA)
            )
        )
    }

    val googlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data
                ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.trim()
                .orEmpty()
            if (picked.isNotBlank()) {
                val normalized = picked.lowercase(Locale.US)
                Prefs.linkedGoogleEmail = normalized
                ProLicenseManager.getStatus(forceRefresh = true)
                if (ProLicenseManager.isCloudConfigured()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { ProLicenseManager.syncWithCloud(context) }
                    }
                }
                Toast.makeText(context, "Terhubung sebagai $normalized", Toast.LENGTH_SHORT).show()
                onFinish()
            }
        }
    }

    fun launchGoogleLogin() {
        try {
            isSigningIn = true
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            googlePickerLauncher.launch(intent)
        } catch (_: Throwable) {
            isSigningIn = false
            Toast.makeText(context, "Google Account Picker tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentPage = pages[pageIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF0D1526), Color(0xFF070B14))
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: step counter + skip to login
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FRAMEPULSE",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                if (pageIndex < pages.lastIndex) {
                    TextButton(onClick = { pageIndex = pages.lastIndex }) {
                        Text(
                            text = "Lewati",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            // Animated main content
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val slideIn = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) { width -> if (forward) width / 3 else -width / 3 } + fadeIn(tween(260))
                    val slideOut = slideOutHorizontally(
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) { width -> if (forward) -width / 3 else width / 3 } + fadeOut(tween(200))
                    slideIn togetherWith slideOut
                },
                label = "onboarding_slide"
            ) { idx ->
                val p = pages[idx]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated Hero Badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(96.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(p.accent.copy(alpha = 0.14f))
                            .border(1.5.dp, p.accent.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Text(
                            text = p.badge,
                            color = p.accent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = p.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = p.subtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Feature bullets card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF111C33).copy(alpha = 0.85f))
                            .border(1.dp, p.accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        p.bullets.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(p.accent)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Warning note on last page for guest mode
                    if (idx == pages.lastIndex) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Catatan: Masuk tanpa akun Google tidak direkomendasikan — skor Global Leaderboard dan status Supporter Pass tidak dapat tersinkronisasi.",
                                color = Color(0xFFFCD34D),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // Bottom indicator + actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { idx, p ->
                        val active = idx == pageIndex
                        val dotWidth by animateDpAsState(
                            targetValue = if (active) 26.dp else 8.dp,
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(if (active) p.accent else Color(0xFF334155))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                if (pageIndex < pages.lastIndex) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pageIndex > 0) {
                            OutlinedButton(
                                onClick = { pageIndex -= 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Kembali", color = Color(0xFFCBD5E1))
                            }
                        }
                        Button(
                            onClick = { pageIndex += 1 },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = currentPage.accent,
                                contentColor = Color(0xFF041019)
                            )
                        ) {
                            Text(
                                text = "Lanjut",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { launchGoogleLogin() },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (isSigningIn) "Membuka Akun Google..." else "Masuk dengan Google (Direkomendasikan)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { onFinish() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Lanjut Tanpa Login (Tidak Direkomendasikan)",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
