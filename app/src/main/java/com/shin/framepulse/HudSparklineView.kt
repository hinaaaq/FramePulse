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

class HudSparklineView(context: Context) : View(context) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#38BDF8")
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
        strokeWidth = 1.1f
        color = Color.argb(55, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }

    private val strokePath = Path()
    private val fillPath = Path()
    private var cachedShaderHeight: Float = -1f
    private var cachedShaderColor: Int = 0

    private var points: List<Int> = emptyList()
    private var accentColor: Int = Color.parseColor("#38BDF8")
    private var maxScaleFps: Int = 90

    fun updateData(newPoints: List<Int>, themeColor: Int, scaleMaxFps: Int = 90) {
        points = newPoints
        accentColor = themeColor
        maxScaleFps = scaleMaxFps.coerceIn(60, 144)
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

        val padY = 4f
        val refFps = if (maxScaleFps <= 65) 30f else 60f
        val yRef = h - ((refFps / maxScaleFps.toFloat()) * (h - padY * 2f)) - padY
        canvas.drawLine(0f, yRef, w, yRef, refLinePaint)

        if (points.size < 2) return

        strokePath.reset()
        fillPath.reset()
        val stepX = (w - 6f) / (points.size - 1).coerceAtLeast(1)

        var prevX = 0f
        var prevY = h
        points.forEachIndexed { idx, fpsVal ->
            val clamped = fpsVal.coerceIn(0, maxScaleFps)
            val x = idx * stepX + 1f
            val y = h - ((clamped / maxScaleFps.toFloat()) * (h - padY * 2f)) - padY
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
        canvas.drawCircle(prevX, prevY, 5.5f, dotHaloPaint)
        canvas.drawCircle(prevX, prevY, 2.8f, dotPaint)
    }
}
