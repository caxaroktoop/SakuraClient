package com.sakura.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/** Живой фон приложения: плывущие розово-фиолетовые свечения и мерцающие звёзды. */
class LiveBackground(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var base: LinearGradient? = null
    private val blobs = arrayOf(
        Blob(0.25f, 0.2f, 0.9f, Color.rgb(224, 69, 123), 0.035f, 0.05f, 0f),
        Blob(0.8f, 0.5f, 1.0f, Color.rgb(124, 58, 176), 0.04f, 0.03f, 2f),
        Blob(0.3f, 0.85f, 0.85f, Color.rgb(255, 143, 184), 0.03f, 0.045f, 4f)
    )
    private val stars = Array(36) {
        floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat() * 6f, 0.5f + Math.random().toFloat())
    }
    private var shaders: Array<RadialGradient>? = null
    private var time = 0f
    private var running = false
    private var last = 0L

    private class Blob(val x: Float, val y: Float, val r: Float, val color: Int, val sx: Float, val sy: Float, val phase: Float)

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (!running) return
            if (last == 0L) last = t
            val dt = (t - last) / 1e9f
            if (dt >= 0.033f) {
                last = t
                time += dt.coerceAtMost(0.1f)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        last = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        base = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(18, 11, 22), Color.rgb(30, 14, 40), Shader.TileMode.CLAMP)
        val m = maxOf(w, h).toFloat()
        shaders = Array(blobs.size) { i ->
            val b = blobs[i]
            val rad = m * 0.55f * b.r
            RadialGradient(0f, 0f, rad, Color.argb(95, Color.red(b.color), Color.green(b.color), Color.blue(b.color)),
                Color.argb(0, Color.red(b.color), Color.green(b.color), Color.blue(b.color)), Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = base
        c.drawRect(0f, 0f, w, h, paint)
        val sh = shaders ?: return
        val m = maxOf(w, h)
        for (i in blobs.indices) {
            val b = blobs[i]
            val cx = w * (b.x + 0.18f * sin(time * b.sx * 2f * PI.toFloat() * 3f + b.phase))
            val cy = h * (b.y + 0.12f * sin(time * b.sy * 2f * PI.toFloat() * 3f + b.phase * 1.7f))
            c.save()
            c.translate(cx, cy)
            paint.shader = sh[i]
            c.drawCircle(0f, 0f, m * 0.55f * b.r, paint)
            c.restore()
        }
        paint.shader = null
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(time * s[3] * 1.6f + s[2])
            paint.color = Color.argb((30 + 150 * tw).toInt(), 255, 220, 235)
            c.drawCircle(s[0] * w, s[1] * h, (0.7f + tw * 0.9f) * d, paint)
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
