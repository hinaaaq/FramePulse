package com.shin.framepulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.max

class HudSparklineView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF38BDF8.toInt()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val refLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
        color = Color.argb(55, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(dp(2.5f), dp(2.5f)), 0f)
    }

    private val strokePath = Path()
    private val fillPath = Path()
    private var cachedShaderHeight: Float = -1f
    private var cachedShaderColor: Int = 0

    private var points: List<Int> = emptyList()
    private var accentColor: Int = 0xFF38BDF8.toInt()
    private var maxScaleFps: Int = 90

    fun updateData(newPoints: List<Int>, themeColor: Int, scaleMaxFps: Int = 90) {
        points = newPoints
        accentColor = themeColor
        maxScaleFps = scaleMaxFps.coerceIn(30, 240)
        linePaint.color = themeColor
        glowPaint.color = Color.argb(65, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
        dotPaint.color = themeColor
        dotHaloPaint.color = Color.argb(80, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val haloRadius = dp(2.6f)
        val dotRadius = dp(1.4f)
        val padY = max(haloRadius, glowPaint.strokeWidth * 0.5f) + dp(1f)
        val padX = haloRadius + dp(1f)
        val usableH = (h - padY * 2f).coerceAtLeast(1f)
        val usableW = (w - padX * 2f).coerceAtLeast(1f)

        val refFps = if (maxScaleFps <= 65) 30f else 60f
        val yRef = h - ((refFps / maxScaleFps.toFloat()) * usableH) - padY
        canvas.drawLine(0f, yRef, w, yRef, refLinePaint)

        if (points.size < 2) return

        strokePath.reset()
        fillPath.reset()
        val stepX = usableW / (points.size - 1).coerceAtLeast(1)

        var prevX = padX
        var prevY = h
        points.forEachIndexed { idx, fpsVal ->
            val clamped = fpsVal.coerceIn(0, maxScaleFps)
            val x = padX + idx * stepX
            val y = h - ((clamped / maxScaleFps.toFloat()) * usableH) - padY
            if (idx == 0) {
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
            if (idx == points.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        if (cachedShaderHeight != h || cachedShaderColor != accentColor) {
            cachedShaderHeight = h
            cachedShaderColor = accentColor
            fillPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.argb(105, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(strokePath, glowPaint)
        canvas.drawPath(strokePath, linePaint)
        canvas.drawCircle(prevX, prevY, haloRadius, dotHaloPaint)
        canvas.drawCircle(prevX, prevY, dotRadius, dotPaint)
    }
}
