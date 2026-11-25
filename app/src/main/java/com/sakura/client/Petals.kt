package com.sakura.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Choreographer
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos

/** Один лепесток сакуры (с вырезом на конце), примерно 24 x 27 единиц, центр около (0, 0). */
object PetalShape {
    val path: Path = Path().apply {
        moveTo(0f, 13f)
        cubicTo(-9f, 7f, -14f, -4f, -10f, -11f)
        cubicTo(-8.5f, -13.8f, -3.5f, -14.2f, 0f, -10f)
        cubicTo(3.5f, -14.2f, 8.5f, -13.8f, 10f, -11f)
        cubicTo(14f, -4f, 9f, 7f, 0f, 13f)
        close()
    }
}

/** Падающие лепестки: покачиваются на ветру, вращаются и «переворачиваются» в воздухе. */
class PetalField(private val density: Float) {
    private class Petal {
        var x = 0f; var y = 0f; var s = 1f
        var vy = 0f; var vx = 0f
        var swayAmp = 0f; var swayFreq = 0f; var phase = 0f
        var rot = 0f; var vr = 0f
        var flip = 0f; var flipFreq = 0f
        var color = 0; var alpha = 255
    }

    private val petals = ArrayList<Petal>()
    private val rnd = Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var w = 0f
    private var h = 0f
    private var time = 0f
    var target = 18
    var alphaMul = 1f

    private val colors = intArrayOf(
        Color.rgb(255, 183, 209), Color.rgb(255, 143, 184), Color.rgb(255, 214, 230)
    )

    fun setSize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
    }

    private fun spawn(p: Petal, initial: Boolean) {
        p.s = 0.4f + rnd.nextFloat() * 0.6f
        p.vy = (28f + 55f * p.s + rnd.nextFloat() * 14f) * density
        p.vx = (6f + rnd.nextFloat() * 16f) * density
        p.swayAmp = (24f + rnd.nextFloat() * 40f) * density
        p.swayFreq = 0.7f + rnd.nextFloat() * 1.3f
        p.phase = rnd.nextFloat() * 6.28f
        p.rot = rnd.nextFloat() * 360f
        p.vr = (rnd.nextFloat() - 0.5f) * 120f
        p.flip = rnd.nextFloat() * 6.28f
        p.flipFreq = 1.5f + rnd.nextFloat() * 2.5f
        p.color = colors[rnd.nextInt(colors.size)]
        p.alpha = 150 + rnd.nextInt(90)
        val margin = 30f * density
        p.x = rnd.nextFloat() * (w + margin * 2) - margin
        p.y = if (initial) rnd.nextFloat() * h else -margin * p.s - rnd.nextFloat() * h * 0.2f
    }

    fun update(dt: Float) {
        if (w <= 0f || h <= 0f) return
        time += dt
        while (petals.size < target) petals.add(Petal().also { spawn(it, true) })
        while (petals.size > target) petals.removeAt(petals.size - 1)
        val margin = 40f * density
        for (p in petals) {
            p.y += p.vy * dt
            p.x += p.vx * dt + cos(time * p.swayFreq + p.phase) * p.swayAmp * dt
            p.rot += p.vr * dt
            p.flip += p.flipFreq * dt
            if (p.y > h + margin || p.x > w + margin * 2 || p.x < -margin * 2) spawn(p, false)
        }
    }

    fun draw(canvas: Canvas) {
        for (p in petals) {
            paint.color = p.color
            paint.alpha = (p.alpha * alphaMul).toInt()
            var fx = cos(p.flip)
            if (abs(fx) < 0.15f) fx = if (fx < 0f) -0.15f else 0.15f
            val sc = p.s * density
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rot)
            canvas.scale(sc * fx, sc)
            canvas.drawPath(PetalShape.path, paint)
            canvas.restore()
        }
    }
}

/** Фоновая анимация для приложения. Касания пропускает насквозь. */
class PetalsView(ctx: Context) : View(ctx) {
    private val field = PetalField(resources.displayMetrics.density)
    private var running = false
    private var last = 0L

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (last == 0L) last = frameTimeNanos
            val dt = ((frameTimeNanos - last) / 1e9f).coerceIn(0f, 0.05f)
            last = frameTimeNanos
            field.update(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init { field.target = 16 }

    fun configure(count: Int, alpha: Float) {
        field.target = count
        field.alphaMul = alpha
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
        field.setSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        field.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
