package com.sakura.client

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View

/**
 * Один полноэкранный слой поверх игры (касания не перехватывает):
 * затемнение, падающие лепестки и прицел. Все настройки берёт из SharedPreferences.
 */
class FxView(ctx: Context, private val prefs: SharedPreferences) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val field = PetalField(d)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private var dimOn = false
    private var dimLevel = 25
    private var warm = false
    private var petalsOn = false
    private var crossOn = false
    private var style = 0
    private var sizeDp = 20
    private var colorIdx = 0

    private var running = false
    private var attached = false
    private var last = 0L

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (!running) return
            if (last == 0L) last = t
            val dt = (t - last) / 1e9f
            if (dt >= 0.033f) { // ~30 кадров/с, чтобы не отнимать FPS у игры
                last = t
                field.update(dt.coerceAtMost(0.1f))
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init { reload() }

    fun reload() {
        dimOn = prefs.getBoolean("dim_on", false)
        dimLevel = prefs.getInt("dim_level", 25).coerceIn(0, 60)
        warm = prefs.getBoolean("dim_warm", false)
        petalsOn = prefs.getBoolean("petals_on", false)
        field.target = prefs.getInt("petals_count", 18).coerceIn(5, 40)
        crossOn = prefs.getBoolean("cross_on", false)
        style = prefs.getInt("cross_style", 0)
        sizeDp = prefs.getInt("cross_size", 20).coerceIn(8, 48)
        colorIdx = prefs.getInt("cross_color", 0).coerceIn(0, COLORS.size - 1)
        updateLoop()
        invalidate()
    }

    private fun updateLoop() {
        if (petalsOn && attached) {
            if (!running) {
                running = true
                last = 0L
                Choreographer.getInstance().postFrameCallback(callback)
            }
        } else {
            running = false
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateLoop()
    }

    override fun onDetachedFromWindow() {
        attached = false
        updateLoop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        field.setSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (dimOn && dimLevel > 0) {
            val a = dimLevel * 255 / 100
            canvas.drawColor(if (warm) Color.argb(a, 255, 110, 20) else Color.argb(a, 0, 0, 0))
        }
        if (petalsOn) field.draw(canvas)
        if (crossOn) drawCross(canvas)
    }

    private fun drawCross(c: Canvas) {
        val x = width / 2f
        val y = height / 2f
        val arm = sizeDp * d
        val gap = 4f * d
        val w = 2.5f * d
        val col = COLORS[colorIdx]
        for (pass in 0..1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (pass == 0) w + 2f * d else w
            paint.color = if (pass == 0) Color.argb(150, 0, 0, 0) else col
            when (style) {
                1 -> { // точка
                    paint.style = Paint.Style.FILL
                    c.drawCircle(x, y, maxOf(2.5f * d, arm * 0.16f) + if (pass == 0) d else 0f, paint)
                }
                2 -> { // круг
                    c.drawCircle(x, y, arm * 0.6f, paint)
                    paint.style = Paint.Style.FILL
                    c.drawCircle(x, y, 1.8f * d + if (pass == 0) d else 0f, paint)
                }
                3 -> { // T-образный
                    c.drawLine(x - arm, y, x - gap, y, paint)
                    c.drawLine(x + gap, y, x + arm, y, paint)
                    c.drawLine(x, y + gap, x, y + arm, paint)
                    paint.style = Paint.Style.FILL
                    c.drawCircle(x, y, 1.8f * d + if (pass == 0) d else 0f, paint)
                }
                else -> { // крест
                    c.drawLine(x - arm, y, x - gap, y, paint)
                    c.drawLine(x + gap, y, x + arm, y, paint)
                    c.drawLine(x, y - arm, x, y - gap, paint)
                    c.drawLine(x, y + gap, x, y + arm, paint)
                    paint.style = Paint.Style.FILL
                    c.drawCircle(x, y, 1.8f * d + if (pass == 0) d else 0f, paint)
                }
            }
        }
    }

    companion object {
        val COLORS = intArrayOf(
            Color.rgb(255, 105, 180), Color.WHITE, Color.rgb(120, 220, 255),
            Color.rgb(180, 255, 150), Color.rgb(255, 224, 102), Color.rgb(255, 90, 90)
        )
    }
}
