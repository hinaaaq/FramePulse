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

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#38BDF8")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val refLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.argb(65, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
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
        dotPaint.color = themeColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val refFps = if (maxScaleFps <= 65) 30f else 60f
        val yRef = h - ((refFps / maxScaleFps.toFloat()) * (h - 6f)) - 3f
        canvas.drawLine(0f, yRef, w, yRef, refLinePaint)

        if (points.size < 2) return

        strokePath.reset()
        fillPath.reset()
        val stepX = (w - 4f) / (points.size - 1).coerceAtLeast(1)
        var lastX = 0f
        var lastY = h

        points.forEachIndexed { idx, fpsVal ->
            val clamped = fpsVal.coerceIn(0, maxScaleFps)
            val x = idx * stepX
            val y = h - ((clamped / maxScaleFps.toFloat()) * (h - 6f)) - 3f
            if (idx == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
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
                Color.argb(95, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(strokePath, linePaint)
        canvas.drawCircle(lastX, lastY, 3.2f, dotPaint)
    }
}
