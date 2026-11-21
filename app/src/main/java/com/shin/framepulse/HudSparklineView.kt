package com.shin.framepulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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

    private val refLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(60, 255, 255, 255)
    }

    private var points: List<Int> = emptyList()
    private var accentColor: Int = Color.parseColor("#38BDF8")

    fun updateData(newPoints: List<Int>, themeColor: Int) {
        points = newPoints
        accentColor = themeColor
        linePaint.color = themeColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Reference line at 60 FPS (assuming max scale 90 FPS for Galaxy A24)
        val y60 = h * (1f - 60f / 90f)
        canvas.drawLine(0f, y60, w, y60, refLinePaint)

        if (points.size < 2) return

        val strokePath = Path()
        val fillPath = Path()
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        points.forEachIndexed { idx, fpsVal ->
            val clamped = fpsVal.coerceIn(0, 90)
            val x = idx * stepX
            val y = h - ((clamped / 90f) * (h - 6f)) - 3f
            if (idx == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == points.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(95, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(strokePath, linePaint)
    }
}
