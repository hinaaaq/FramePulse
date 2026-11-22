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
    OBSIDIAN_MINIMAL,
    STEAM_SLATE,
    CRIMSON_ESPORTS,
    MIDNIGHT_VIOLET,
    ARCTIC_CYAN,
    SOLAR_GOLD,
    SAKURA_PASTEL_DEV,
    COTTON_CANDY_DEV
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
            subtitle = "Standar FramePulse • Gratis",
            bgDark = Color(0xFF090D16),
            cardBg = Color(0xFF111827),
            accent = Color(0xFF10B981),
            accentSecondary = Color(0xFF38BDF8),
            iconGlyph = "FP",
            isProOnly = false
        ),
        AppThemeSpec(
            id = AppThemeId.OBSIDIAN_MINIMAL,
            name = "Obsidian Pro",
            subtitle = "Minimalis bersih ala Apple/iOS • Gratis",
            bgDark = Color(0xFF0A0A0C),
            cardBg = Color(0xFF16161A),
            accent = Color(0xFFE5E7EB),
            accentSecondary = Color(0xFF9CA3AF),
            iconGlyph = "●",
            isProOnly = false
        ),
        AppThemeSpec(
            id = AppThemeId.STEAM_SLATE,
            name = "Steam Slate",
            subtitle = "Biru slate modern ala Steam Deck • Pro",
            bgDark = Color(0xFF0E141B),
            cardBg = Color(0xFF1B2838),
            accent = Color(0xFF66C0F4),
            accentSecondary = Color(0xFF1A9FFF),
            iconGlyph = "◈",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.CRIMSON_ESPORTS,
            name = "Crimson Esports",
            subtitle = "Merah-oranye kompetitif • Pro",
            bgDark = Color(0xFF13090C),
            cardBg = Color(0xFF211116),
            accent = Color(0xFFF43F5E),
            accentSecondary = Color(0xFFFB923C),
            iconGlyph = "⚡",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.MIDNIGHT_VIOLET,
            name = "Midnight Violet",
            subtitle = "Ungu neon futuristik • Pro",
            bgDark = Color(0xFF0D0A19),
            cardBg = Color(0xFF1A1430),
            accent = Color(0xFFA855F7),
            accentSecondary = Color(0xFF38BDF8),
            iconGlyph = "◆",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.ARCTIC_CYAN,
            name = "Arctic Cyan",
            subtitle = "Biru es dingin & tajam • Pro",
            bgDark = Color(0xFF07131A),
            cardBg = Color(0xFF0E222E),
            accent = Color(0xFF06B6D4),
            accentSecondary = Color(0xFF60A5FA),
            iconGlyph = "❄",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.SOLAR_GOLD,
            name = "Solar Gold",
            subtitle = "Emas mewah edisi premium • Pro",
            bgDark = Color(0xFF120F07),
            cardBg = Color(0xFF211B0D),
            accent = Color(0xFFFACC15),
            accentSecondary = Color(0xFFF59E0B),
            iconGlyph = "★",
            isProOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.SAKURA_PASTEL_DEV,
            name = "Sakura Pastel ♡",
            subtitle = "Tema Rahasia Dev • Soft Pink & Baby Blue",
            bgDark = Color(0xFF1B101A),
            cardBg = Color(0xFF281727),
            accent = Color(0xFFFF85C0),
            accentSecondary = Color(0xFF85D7FF),
            iconGlyph = "♡",
            isProOnly = false,
            isOwnerOnly = true
        ),
        AppThemeSpec(
            id = AppThemeId.COTTON_CANDY_DEV,
            name = "Cotton Candy ♡",
            subtitle = "Tema Rahasia Dev • Lavender & Bubblegum",
            bgDark = Color(0xFF141124),
            cardBg = Color(0xFF211C38),
            accent = Color(0xFFFF9EE2),
            accentSecondary = Color(0xFF7DD3FC),
            iconGlyph = "✦",
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
    val visibleThemes = remember(isOwner) {
        AppThemeManager.allThemes.filter { !it.isOwnerOnly || isOwner }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.bgDark)
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, currentTheme.accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text("Tema Global Aplikasi", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    if (isOwner) "Mode Owner: seluruh tema Pro + 2 tema rahasia terbuka."
                    else "Mengubah warna seluruh layar, bar navigasi & aksen grafik.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
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
                                    Toast.makeText(context, "Tema ${theme.name} diterapkan!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(theme.accent, theme.accentSecondary))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(theme.iconGlyph, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(theme.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (theme.isOwnerOnly) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Brush.horizontalGradient(listOf(theme.accent, theme.accentSecondary)))
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
    // Desain bersih tanpa header dobel (ala Apple Control Center / Steam Deck)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.cardBg)
                    .border(1.dp, theme.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { onOpenPresets() }
                    .padding(12.dp)
            ) {
                Text("Preset Game", color = theme.accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text("Optimasi 1-tap Valorant, MLBB, PUBG", color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.cardBg)
                    .border(1.dp, theme.accentSecondary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { onOpenLeaderboard() }
                    .padding(12.dp)
            ) {
                Text("Leaderboard", color = theme.accentSecondary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text("Rank Global & Simpan Kartu Pamer", color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.cardBg)
                    .border(0.8.dp, theme.accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable { onOpenThemes() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tema: ${theme.name}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1)
                Text(theme.iconGlyph, color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.cardBg)
                    .border(0.8.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .clickable { onOpenAbout() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("About", color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}
