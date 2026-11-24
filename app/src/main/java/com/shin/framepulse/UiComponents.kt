package com.shin.framepulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val BgObsidian = Color(0xFF050811)
val TopBarSurface = Color(0xFF080E1B)
val CardSurface = Color(0xFF0D1526)
val CardSurfaceHigh = Color(0xFF131E35)
val CardBorder = Color(0xFF1D2C47)
val CyberCyan = Color(0xFF38BDF8)
val EmeraldNeon = Color(0xFF10B981)
val CrimsonAccent = Color(0xFFF43F5E)
val AmberVolt = Color(0xFFFBBF24)
val VioletHyper = Color(0xFFA855F7)
val PurpleGpu = Color(0xFFC084FC)
val PinkRam = Color(0xFFF472B6)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val FramePulseTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun FramePulseLogoMark(accent: Color, modifier: Modifier = Modifier.size(34.dp)) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF11203D), Color(0xFF091122))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val hex = Path().apply {
                moveTo(w * 0.50f, h * 0.08f)
                lineTo(w * 0.88f, h * 0.29f)
                lineTo(w * 0.88f, h * 0.71f)
                lineTo(w * 0.50f, h * 0.92f)
                lineTo(w * 0.12f, h * 0.71f)
                lineTo(w * 0.12f, h * 0.29f)
                close()
            }
            drawPath(
                path = hex,
                brush = Brush.linearGradient(listOf(accent, EmeraldNeon)),
                style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round)
            )
            val pulse = Path().apply {
                moveTo(w * 0.18f, h * 0.54f)
                lineTo(w * 0.34f, h * 0.54f)
                lineTo(w * 0.44f, h * 0.28f)
                lineTo(w * 0.55f, h * 0.76f)
                lineTo(w * 0.65f, h * 0.42f)
                lineTo(w * 0.72f, h * 0.54f)
                lineTo(w * 0.84f, h * 0.54f)
            }
            drawPath(
                path = pulse,
                brush = Brush.horizontalGradient(listOf(accent, EmeraldNeon)),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    accentGlow: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (accentGlow != null) {
                        Brush.verticalGradient(
                            listOf(accentGlow.copy(alpha = 0.09f), Color.Transparent)
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFF121D33).copy(alpha = 0.45f), Color.Transparent)
                        )
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(15.dp),
                content = content
            )
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    badge: String? = null,
    accent: Color = CyberCyan,
    iconType: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconType != null) {
                MiniVectorIcon(type = iconType, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = badge,
                    color = accent,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
fun StatusDotPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
fun ProTagPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AmberVolt.copy(alpha = 0.18f))
            .border(1.dp, AmberVolt.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 0.5.dp)
    ) {
        Text(
            text = "SUP",
            color = AmberVolt,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    accent: Color = CyberCyan,
    swatchColor: Color? = null,
    locked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) accent.copy(alpha = 0.18f) else Color(0xFF080E1B)
    val border = if (selected) accent else if (locked) AmberVolt.copy(alpha = 0.25f) else CardBorder
    val txtColor = if (selected) accent else TextSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (swatchColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = txtColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        if (locked) {
            Spacer(Modifier.width(4.dp))
            ProTagPill()
        }
    }
}

@Composable
fun ComposeSparkline(
    points: List<Int>,
    accent: Color,
    maxScaleFps: Int = 90,
    graphHeight: Dp = 74.dp,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val safeMax = maxScaleFps.coerceIn(60, 144)
    val refFps = if (safeMax <= 65) 30 else 60

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(graphHeight)) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val padY = 6f
            val yRef = h - ((refFps / safeMax.toFloat()) * (h - padY * 2f)) - padY
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(0f, yRef),
                end = Offset(w, yRef),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            if (points.size < 2) return@Canvas

            val strokePath = Path()
            val fillPath = Path()
            val stepX = (w - 10f) / (points.size - 1).coerceAtLeast(1)
            var prevX = 0f
            var prevY = h

            points.forEachIndexed { i, raw ->
                val clamped = raw.coerceIn(0, safeMax)
                val x = i * stepX + 2f
                val y = h - ((clamped / safeMax.toFloat()) * (h - padY * 2f)) - padY
                if (i == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    val midX = (prevX + x) * 0.5f
                    strokePath.cubicTo(midX, prevY, midX, y, x, y)
                    fillPath.cubicTo(midX, prevY, midX, y, x, y)
                }
                prevX = x
                prevY = y
                if (i == points.lastIndex) {
                    fillPath.lineTo(x, h)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.34f), Color.Transparent)
                )
            )
            drawPath(
                path = strokePath,
                color = accent.copy(alpha = 0.28f),
                style = Stroke(
                    width = 5.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = strokePath,
                color = accent,
                style = Stroke(
                    width = 2.3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawCircle(
                color = accent.copy(alpha = 0.35f),
                radius = 6.dp.toPx(),
                center = Offset(prevX, prevY)
            )
            drawCircle(
                color = accent,
                radius = 3.dp.toPx(),
                center = Offset(prevX, prevY)
            )
        }

        if (showLabels) {
            Text(
                text = "${safeMax}Hz",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Text(
                text = "$refFps",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
}

@Composable
fun HudLivePreviewBox(
    telemetry: TelemetryData,
    fpsHistory: List<Int>,
    layoutMode: String,
    hudStyle: String,
    fontStyle: String,
    accent: Color,
    bgAlpha: Int,
    textSizeSp: Float,
    showHudGraph: Boolean,
    sFps: Boolean,
    sFrameTime: Boolean,
    sCpu: Boolean,
    sClock: Boolean,
    sGpu: Boolean,
    sRam: Boolean,
    sTemp: Boolean,
    sBatt: Boolean,
    sWatt: Boolean,
    sNet: Boolean,
    maxScaleFps: Int
) {
    val effectiveAlpha = if (hudStyle == Prefs.STYLE_MINIMAL) 0f else bgAlpha / 255f
    val corner = when (hudStyle) {
        Prefs.STYLE_PILL -> 99.dp
        Prefs.STYLE_DECK -> 12.dp
        Prefs.STYLE_MINIMAL -> 0.dp
        else -> 11.dp
    }
    val hPad = when (hudStyle) {
        Prefs.STYLE_PILL -> 12.dp
        Prefs.STYLE_DECK -> 10.dp
        Prefs.STYLE_MINIMAL -> 4.dp
        else -> 9.dp
    }
    val vPad = when (hudStyle) {
        Prefs.STYLE_MINIMAL -> 2.dp
        Prefs.STYLE_DECK -> 7.dp
        else -> 6.dp
    }

    val previewFontFamily = when (fontStyle) {
        Prefs.FONT_SPORT, Prefs.FONT_CLEAN -> FontFamily.SansSerif
        else -> FontFamily.Monospace
    }
    val previewWeight = if (fontStyle == Prefs.FONT_TECH) FontWeight.Normal else FontWeight.Bold

    val fpsColor = when {
        telemetry.fps >= 50 -> Color(0xFF22C55E)
        telemetry.fps >= 30 -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    val entries = buildList {
        if (layoutMode == Prefs.LAYOUT_MINI) {
            if (sFps) add(Triple("", "${telemetry.fps}FPS", fpsColor))
            if (sTemp) add(Triple("", String.format(Locale.US, "%.1f°", telemetry.batteryTempC), accent))
            if (sCpu) add(Triple("C ", "${telemetry.cpuLoadPercent}%", TextPrimary))
            if (sGpu) add(Triple("G ", "${telemetry.gpuLoadPercent ?: 62}%", PurpleGpu))
            if (sBatt) add(Triple("", "${telemetry.batteryPercent}%", EmeraldNeon))
        } else {
            if (sFps) {
                val ft = if (sFrameTime && telemetry.frameTimeMs > 0f) String.format(Locale.US, " %.1fms", telemetry.frameTimeMs) else ""
                add(Triple("FPS ", "${telemetry.fps}$ft", fpsColor))
            }
            if (sCpu) add(Triple("CPU ", "${telemetry.cpuLoadPercent}%", TextPrimary))
            if (sClock) add(Triple("CLK ", String.format(Locale.US, "%.2fG", telemetry.cpuMaxGhz), Color(0xFFCBD5E1)))
            if (sGpu) add(Triple("GPU ", telemetry.gpuLoadPercent?.let { "$it%" } ?: "62%", PurpleGpu))
            if (sRam) add(Triple("RAM ", String.format(Locale.US, "%.1fG", telemetry.ramUsedGb), PinkRam))
            if (sTemp) add(Triple("TMP ", String.format(Locale.US, "%.1f°C", telemetry.batteryTempC), accent))
            if (sBatt) add(Triple("BAT ", "${telemetry.batteryPercent}%", EmeraldNeon))
            if (sWatt) add(Triple("PWR ", String.format(Locale.US, "-%.1fW", telemetry.powerWatts), AmberVolt))
            if (sNet) add(Triple("NET ", "↓${telemetry.netDownKbps.toInt()}K", EmeraldNeon))
        }
    }.ifEmpty { listOf(Triple("FPS ", "${telemetry.fps}", fpsColor)) }

    val separator = if (layoutMode == Prefs.LAYOUT_VERTICAL) "\n" else "  ·  "
    val textShadow = if (effectiveAlpha <= 0.08f) Shadow(Color.Black, Offset(1.5f, 1.5f), 4.5f) else null

    val annotated = buildAnnotatedString {
        entries.forEachIndexed { idx, (lbl, txt, col) ->
            if (lbl.isNotEmpty()) {
                withStyle(SpanStyle(color = TextSecondary, fontSize = (textSizeSp * 0.84f).sp, shadow = textShadow)) {
                    append(lbl)
                }
            }
            withStyle(SpanStyle(color = col, fontWeight = previewWeight, shadow = textShadow)) {
                append(txt)
            }
            if (idx < entries.lastIndex) {
                withStyle(SpanStyle(color = TextSecondary.copy(alpha = 0.45f))) {
                    append(separator)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A1426), Color(0xFF132238), Color(0xFF080F1E))
                )
            )
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
    ) {
        // Subtle simulated tactical game-backdrop grid
        Canvas(modifier = Modifier.matchParentSize()) {
            val step = 22.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(corner))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0B1222).copy(alpha = effectiveAlpha),
                                Color(0xFF050912).copy(alpha = effectiveAlpha)
                            )
                        )
                    )
                    .border(
                        width = if (effectiveAlpha > 0.08f) 1.dp else 0.dp,
                        color = if (effectiveAlpha > 0.08f) accent.copy(alpha = effectiveAlpha * 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(corner)
                    )
                    .padding(horizontal = hPad, vertical = vPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if ((hudStyle == Prefs.STYLE_GLASS || hudStyle == Prefs.STYLE_DECK) && effectiveAlpha > 0.08f) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                if (hudStyle == Prefs.STYLE_PILL) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldNeon)
                    )
                    Spacer(Modifier.width(6.dp))
                }

                Column {
                    if (hudStyle == Prefs.STYLE_DECK) {
                        Text(
                            text = "FP PRO • SURFACEFLINGER",
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(1.dp)
                                .background(TextSecondary.copy(alpha = 0.22f))
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(
                        text = annotated,
                        fontSize = textSizeSp.sp,
                        fontFamily = previewFontFamily,
                        lineHeight = (textSizeSp * 1.28f).sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    if (showHudGraph && layoutMode != Prefs.LAYOUT_MINI && hudStyle != Prefs.STYLE_PILL) {
                        Spacer(Modifier.height(4.dp))
                        val previewPts = if (fpsHistory.size >= 2) fpsHistory else listOf(58, 60, 59, 60, 57, 60, 60, 59)
                        ComposeSparkline(
                            points = previewPts,
                            accent = accent,
                            maxScaleFps = maxScaleFps,
                            graphHeight = 24.dp,
                            showLabels = false,
                            modifier = Modifier.width(156.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniVectorIcon(type: String, tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        when (type) {
            "bolt" -> {
                val p = Path().apply {
                    moveTo(w * 0.56f, h * 0.08f)
                    lineTo(w * 0.20f, h * 0.56f)
                    lineTo(w * 0.50f, h * 0.56f)
                    lineTo(w * 0.44f, h * 0.92f)
                    lineTo(w * 0.80f, h * 0.44f)
                    lineTo(w * 0.50f, h * 0.44f)
                    close()
                }
                drawPath(p, color = tint)
            }
            "pulse" -> {
                val p = Path().apply {
                    moveTo(w * 0.06f, h * 0.55f)
                    lineTo(w * 0.28f, h * 0.55f)
                    lineTo(w * 0.42f, h * 0.16f)
                    lineTo(w * 0.58f, h * 0.84f)
                    lineTo(w * 0.72f, h * 0.42f)
                    lineTo(w * 0.84f, h * 0.55f)
                    lineTo(w * 0.94f, h * 0.55f)
                }
                drawPath(p, color = tint, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "record" -> {
                drawCircle(tint.copy(alpha = 0.3f), radius = w * 0.44f)
                drawCircle(tint, radius = w * 0.24f)
            }
            "cpu" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
                    style = Stroke(sw)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.40f, h * 0.40f),
                    size = Size(w * 0.20f, h * 0.20f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                )
            }
            "temp" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.64f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.20f, center = Offset(w * 0.5f, h * 0.74f))
            }
            "ram" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.28f),
                    size = Size(w * 0.76f, h * 0.44f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = Stroke(sw)
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.42f), Offset(w * 0.34f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.52f, h * 0.42f), Offset(w * 0.52f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.42f), Offset(w * 0.68f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "net" -> {
                val up = Path().apply {
                    moveTo(w * 0.32f, h * 0.78f)
                    lineTo(w * 0.32f, h * 0.22f)
                    moveTo(w * 0.16f, h * 0.38f)
                    lineTo(w * 0.32f, h * 0.22f)
                    lineTo(w * 0.48f, h * 0.38f)
                }
                val dn = Path().apply {
                    moveTo(w * 0.68f, h * 0.22f)
                    lineTo(w * 0.68f, h * 0.78f)
                    moveTo(w * 0.52f, h * 0.62f)
                    lineTo(w * 0.68f, h * 0.78f)
                    lineTo(w * 0.84f, h * 0.62f)
                }
                drawPath(up, color = tint.copy(alpha = 0.65f), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(dn, color = tint, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            else -> {
                // shield / studio default
                drawCircle(tint, radius = w * 0.34f, style = Stroke(sw))
                drawCircle(tint, radius = w * 0.12f)
            }
        }
    }
}

@Composable
fun NavVectorIcon(index: Int, tint: Color, selected: Boolean = false, modifier: Modifier = Modifier.size(21.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = (if (selected) 2.2f else 1.8f).dp.toPx()
        when (index) {
            0 -> {
                // Pulse Wave (Monitor)
                if (selected) {
                    drawCircle(tint.copy(alpha = 0.16f), radius = w * 0.48f)
                }
                val p = Path().apply {
                    moveTo(w * 0.08f, h * 0.55f)
                    lineTo(w * 0.28f, h * 0.55f)
                    lineTo(w * 0.42f, h * 0.18f)
                    lineTo(w * 0.58f, h * 0.84f)
                    lineTo(w * 0.72f, h * 0.42f)
                    lineTo(w * 0.82f, h * 0.55f)
                    lineTo(w * 0.94f, h * 0.55f)
                }
                drawPath(p, color = tint, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            1 -> {
                // HUD Sliders (Studio)
                val ys = listOf(0.26f, 0.52f, 0.78f)
                val knobs = listOf(0.35f, 0.70f, 0.45f)
                ys.forEachIndexed { idx, yf ->
                    val y = h * yf
                    drawLine(tint.copy(alpha = if (selected) 0.55f else 0.7f), Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
                    drawCircle(tint, radius = w * (if (selected) 0.13f else 0.10f), center = Offset(w * knobs[idx], y))
                }
            }
            2 -> {
                // CPU Chip (Sensors)
                if (selected) {
                    drawRoundRect(
                        color = tint.copy(alpha = 0.22f),
                        topLeft = Offset(w * 0.24f, h * 0.24f),
                        size = Size(w * 0.52f, h * 0.52f),
                        cornerRadius = CornerRadius(w * 0.1f, w * 0.1f)
                    )
                }
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.24f, h * 0.24f),
                    size = Size(w * 0.52f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
                    style = Stroke(sw)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.40f, h * 0.40f),
                    size = Size(w * 0.20f, h * 0.20f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                )
                val pins = listOf(0.38f, 0.62f)
                pins.forEach { f ->
                    drawLine(tint, Offset(w * f, h * 0.08f), Offset(w * f, h * 0.22f), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * f, h * 0.78f), Offset(w * f, h * 0.92f), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.08f, h * f), Offset(w * 0.22f, h * f), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.78f, h * f), Offset(w * 0.92f, h * f), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
                }
            }
            else -> {
                // Shield / Diagnostics (Setup)
                val shield = Path().apply {
                    moveTo(w * 0.5f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.52f)
                    cubicTo(w * 0.84f, h * 0.74f, w * 0.66f, h * 0.86f, w * 0.5f, h * 0.92f)
                    cubicTo(w * 0.34f, h * 0.86f, w * 0.16f, h * 0.74f, w * 0.16f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.24f)
                    close()
                }
                if (selected) {
                    drawPath(shield, color = tint.copy(alpha = 0.2f))
                }
                drawPath(shield, color = tint, style = Stroke(sw, join = StrokeJoin.Round))
                val check = Path().apply {
                    moveTo(w * 0.36f, h * 0.52f)
                    lineTo(w * 0.47f, h * 0.63f)
                    lineTo(w * 0.66f, h * 0.40f)
                }
                drawPath(check, color = tint, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
