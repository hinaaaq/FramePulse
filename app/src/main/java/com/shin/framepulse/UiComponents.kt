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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val BgObsidian = Color(0xFF060A12)
val TopBarSurface = Color(0xFF0A111F)
val CardSurface = Color(0xFF0F172A)
val CardBorder = Color(0xFF1E2D45)
val CyberCyan = Color(0xFF38BDF8)
val EmeraldNeon = Color(0xFF10B981)
val CrimsonAccent = Color(0xFFF43F5E)
val AmberVolt = Color(0xFFFBBF24)
val PurpleGpu = Color(0xFFC084FC)
val PinkRam = Color(0xFFF472B6)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun SectionTitle(title: String, badge: String? = null, accent: Color = CyberCyan) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
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
            .background(color.copy(alpha = 0.14f))
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
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    accent: Color = CyberCyan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) accent.copy(alpha = 0.18f) else Color(0xFF0A111F)
    val border = if (selected) accent else CardBorder
    val txtColor = if (selected) accent else TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = txtColor,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun ComposeSparkline(
    points: List<Int>,
    accent: Color,
    maxScaleFps: Int = 90,
    graphHeight: Dp = 72.dp,
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

            val yRef = h - ((refFps / safeMax.toFloat()) * (h - 10f)) - 5f
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
            val stepX = (w - 8f) / (points.size - 1).coerceAtLeast(1)
            var lastX = 0f
            var lastY = h

            points.forEachIndexed { i, raw ->
                val clamped = raw.coerceIn(0, safeMax)
                val x = i * stepX
                val y = h - ((clamped / safeMax.toFloat()) * (h - 10f)) - 5f
                if (i == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                lastX = x
                lastY = y
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
                color = accent,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawCircle(
                color = accent.copy(alpha = 0.3f),
                radius = 6.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = accent,
                radius = 3.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }

        if (showLabels) {
            Text(
                text = "${safeMax}Hz",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Text(
                text = "$refFps",
                color = TextSecondary.copy(alpha = 0.55f),
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
        Prefs.STYLE_MINIMAL -> 0.dp
        else -> 10.dp
    }
    val hPad = when (hudStyle) {
        Prefs.STYLE_PILL -> 14.dp
        Prefs.STYLE_MINIMAL -> 4.dp
        else -> 10.dp
    }
    val vPad = when (hudStyle) {
        Prefs.STYLE_MINIMAL -> 2.dp
        else -> 6.dp
    }

    val fpsColor = when {
        telemetry.fps >= 50 -> Color(0xFF22C55E)
        telemetry.fps >= 30 -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    val entries = buildList {
        if (layoutMode == Prefs.LAYOUT_MINI) {
            if (sFps) add("${telemetry.fps}FPS" to fpsColor)
            if (sTemp) add(String.format(Locale.US, "%.1f°", telemetry.batteryTempC) to accent)
            if (sCpu) add("C:${telemetry.cpuLoadPercent}%" to TextPrimary)
            if (sGpu) add("G:${telemetry.gpuLoadPercent ?: 0}%" to PurpleGpu)
            if (sBatt) add("${telemetry.batteryPercent}%" to EmeraldNeon)
        } else {
            if (sFps) {
                val ft = if (sFrameTime && telemetry.frameTimeMs > 0f) String.format(Locale.US, " %.1fms", telemetry.frameTimeMs) else ""
                add("FPS ${telemetry.fps}$ft" to fpsColor)
            }
            if (sCpu) add("CPU ${telemetry.cpuLoadPercent}%" to TextPrimary)
            if (sClock) add(String.format(Locale.US, "CLK %.2fG", telemetry.cpuMaxGhz) to Color(0xFFCBD5E1))
            if (sGpu) add("GPU ${telemetry.gpuLoadPercent?.let { "$it%" } ?: "--"}" to PurpleGpu)
            if (sRam) add(String.format(Locale.US, "RAM %.1fG", telemetry.ramUsedGb) to PinkRam)
            if (sTemp) add(String.format(Locale.US, "%.1f°C", telemetry.batteryTempC) to accent)
            if (sBatt) add("BAT ${telemetry.batteryPercent}%" to EmeraldNeon)
            if (sWatt) add(String.format(Locale.US, "%.1fW", telemetry.powerWatts) to AmberVolt)
            if (sNet) add("↓${telemetry.netDownKbps.toInt()}K" to EmeraldNeon)
        }
    }.ifEmpty { listOf("FPS ${telemetry.fps}" to fpsColor) }

    val separator = if (layoutMode == Prefs.LAYOUT_VERTICAL) "\n" else " │ "
    val annotated = buildAnnotatedString {
        entries.forEachIndexed { idx, (txt, col) ->
            val textShadow = if (effectiveAlpha <= 0.08f) Shadow(Color.Black, Offset(1.5f, 1.5f), 4f) else null
            withStyle(SpanStyle(color = col, fontWeight = FontWeight.Bold, shadow = textShadow)) {
                append(txt)
            }
            if (idx < entries.lastIndex) {
                withStyle(SpanStyle(color = TextSecondary.copy(alpha = 0.55f))) {
                    append(separator)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF07111F), Color(0xFF101B2E), Color(0xFF080E18))
                )
            )
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(corner))
                .background(Color(0xFF090E1A).copy(alpha = effectiveAlpha))
                .border(
                    width = if (effectiveAlpha > 0.08f) 1.dp else 0.dp,
                    color = if (effectiveAlpha > 0.08f) accent.copy(alpha = effectiveAlpha * 0.65f) else Color.Transparent,
                    shape = RoundedCornerShape(corner)
                )
                .padding(horizontal = hPad, vertical = vPad)
        ) {
            Text(
                text = annotated,
                fontSize = textSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (textSizeSp * 1.25f).sp
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
                    modifier = Modifier.width(150.dp)
                )
            }
        }
    }
}

@Composable
fun NavVectorIcon(index: Int, tint: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.dp.toPx()
        when (index) {
            0 -> {
                // Pulse Wave (Monitor)
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
                    drawLine(tint, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
                    drawCircle(tint, radius = w * 0.11f, center = Offset(w * knobs[idx], y))
                }
            }
            2 -> {
                // CPU Chip (Sensors)
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
