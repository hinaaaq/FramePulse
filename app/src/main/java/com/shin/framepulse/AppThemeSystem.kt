package com.shin.framepulse

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

enum class AppThemeId {
    CYBER_EMERALD,
    CRIMSON_ESPORTS,
    MIDNIGHT_VIOLET,
    ARCTIC_CYAN,
    SAKURA_PASTEL_DEV
}

data class AppThemeSpec(
    val id: AppThemeId,
    val name: String,
    val subtitle: String,
    val bgDark: Color,
    val cardBg: Color,
    val accent: Color,
    val accentSecondary: Color,
    val iconGlyph: String,
    val isProOnly: Boolean,
    val isOwnerOnly: Boolean = false
)

object AppThemeManager {
    private const val PREF_NAME = "framepulse_theme_prefs"
    private const val KEY_THEME_ID = "selected_theme_id"

    val allThemes = listOf(
        AppThemeSpec(
            id = AppThemeId.CYBER_EMERALD,
            name = "Cyber Emerald",
            subtitle = "Tema standar FramePulse • Gratis",
            bgDark = Color(0xFF090D16),
            cardBg = Color(0xFF131B2E),
            accent = Color(0xFF10B981),
            accentSecondary = Color(0xFF38BDF8),
            iconGlyph = "FP",
            isProOnly = false
        ),
        AppThemeSpec(
            id = AppThemeId.CRIMSON_ESPORTS,
            name = "Crimson Esports",
            subtitle = "Merah-oranye agresif • Pro Pass",
            bgDark = Color(0xFF14090C),
            cardBg = Color(0xFF231116),
            accent = Color(0xFFF43F5E),
            accentSecondary = Color(0xFFFB923C),
            iconGlyph = "⚡",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.MIDNIGHT_VIOLET,
            name = "Midnight Violet",
            subtitle = "Ungu neon futuristik • Pro Pass",
            bgDark = Color(0xFF0E0A1A),
            cardBg = Color(0xFF1B1430),
            accent = Color(0xFFA855F7),
            accentSecondary = Color(0xFF38BDF8),
            iconGlyph = "◆",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.ARCTIC_CYAN,
            name = "Arctic Cyan",
            subtitle = "Biru es dingin & bersih • Pro Pass",
            bgDark = Color(0xFF07131A),
            cardBg = Color(0xFF0E222E),
            accent = Color(0xFF06B6D4),
            accentSecondary = Color(0xFF60A5FA),
            iconGlyph = "❄",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.SAKURA_PASTEL_DEV,
            name = "Sakura Pastel ♡",
            subtitle = "Tema Rahasia Khusus Dev/Owner • Pastel Pink & Baby Blue",
            bgDark = Color(0xFF1E121D),
            cardBg = Color(0xFF2E1C2D),
            accent = Color(0xFFFF85C0),
            accentSecondary = Color(0xFF85D7FF),
            iconGlyph = "♡",
            isProOnly = false,
            isOwnerOnly = true
        )
    )

    fun loadTheme(context: Context, isPro: Boolean, isOwner: Boolean): AppThemeSpec {
        val saved = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_ID, AppThemeId.CYBER_EMERALD.name)
        val found = allThemes.firstOrNull { it.id.name == saved } ?: allThemes.first()
        if (found.isOwnerOnly && !isOwner) return allThemes.first()
        if (found.isProOnly && !isPro && !isOwner) return allThemes.first()
        return found
    }

    fun saveTheme(context: Context, id: AppThemeId) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_ID, id.name)
            .apply()
    }
}

@Composable
fun ThemePickerDialog(
    currentTheme: AppThemeSpec,
    isPro: Boolean,
    isOwner: Boolean,
    onSelect: (AppThemeSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Tema rahasia Dev/Owner 100% disembunyikan dari daftar jika bukan akun Owner
    val visibleThemes = remember(isOwner) {
        AppThemeManager.allThemes.filter { !it.isOwnerOnly || isOwner }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.bgDark)
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, currentTheme.accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text("Tema & Ikon Aplikasi", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    if (isOwner) "Mode Owner aktif: semua tema Pro + tema rahasia terbuka."
                    else "Ubah tampilan & ikon header FramePulse sesuai gaya kamu.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleThemes) { theme ->
                        val selected = theme.id == currentTheme.id
                        val locked = theme.isProOnly && !isPro && !isOwner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.cardBg)
                                .border(
                                    width = if (selected) 1.5.dp else 0.8.dp,
                                    color = if (selected) theme.accent else Color(0xFF334155),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (locked) {
                                        Toast.makeText(context, "Khusus Pro Pass", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    AppThemeManager.saveTheme(context, theme.id)
                                    onSelect(theme)
                                    Toast.makeText(context, "Tema ${theme.name} aktif!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(theme.accent, theme.accentSecondary))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(theme.iconGlyph, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(theme.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (theme.isOwnerOnly) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Brush.horizontalGradient(listOf(Color(0xFFFF85C0), Color(0xFF85D7FF))))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text("DEV SECRET", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 8.5.sp)
                                        }
                                    } else if (theme.isProOnly) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF10B981))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("PRO", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 8.5.sp)
                                        }
                                    }
                                }
                                Text(theme.subtitle, color = Color(0xFFCBD5E1), fontSize = 10.5.sp)
                            }
                            if (selected) {
                                Text("AKTIF", color = theme.accent, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                            } else if (locked) {
                                Text("LOCK", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 10.sp)
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

@Composable
fun ProHomeCommandHub(
    theme: AppThemeSpec,
    isPro: Boolean,
    isOwner: Boolean,
    onOpenPresets: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(theme.cardBg, theme.bgDark)
                )
            )
            .border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(theme.accent, theme.accentSecondary))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(theme.iconGlyph, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("FramePulse Hub", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    when {
                                        isOwner -> Brush.horizontalGradient(listOf(theme.accent, theme.accentSecondary))
                                        isPro -> Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF34D399)))
                                        else -> Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                when {
                                    isOwner -> "OWNER"
                                    isPro -> "PRO"
                                    else -> "FREE"
                                },
                                color = if (isOwner || isPro) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Text("Akses cepat Preset Game, Rank Global & Tema", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onOpenPresets,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Preset Game", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
            }
            Button(
                onClick = onOpenLeaderboard,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentSecondary)
            ) {
                Text("Leaderboard", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onOpenThemes,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Text("Tema: ${theme.name.take(10)}", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Button(
                onClick = onOpenAbout,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Text("About & GitHub", color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}
