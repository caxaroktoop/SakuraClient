package com.sakura.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.widget.ScrollView

/** «Стеклянный» фон панели: градиент, розовое свечение, градиентная рамка и еле заметные лепестки. */
class GlassDrawable(private val ui: Ui, var opacity: Int) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val petal = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    private fun a(base: Int) = (base * opacity / 100).coerceIn(0, 255)

    fun setPanelOpacity(v: Int) {
        opacity = v
        invalidateSelf()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val sw = ui.fdp(1.3f)
        rect.set(b.left + sw / 2, b.top + sw / 2, b.right - sw / 2, b.bottom - sw / 2)
        val r = ui.fdp(24f)

        fill.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            Color.argb(a(250), 46, 23, 58), Color.argb(a(250), 17, 9, 25), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, fill)

        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        glow.shader = RadialGradient(
            rect.right, rect.top, rect.width() * 0.95f,
            Color.argb(a(95), 255, 143, 184), Color.argb(0, 255, 143, 184), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, glow)
        glow.shader = RadialGradient(
            rect.left, rect.bottom, rect.width() * 0.8f,
            Color.argb(a(60), 140, 70, 200), Color.argb(0, 140, 70, 200), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, glow)

        petal.color = Color.argb(a(40), 255, 183, 209)
        drawPetal(c, rect.right - ui.fdp(20f), rect.top + ui.fdp(60f), 1.7f, 28f)
        drawPetal(c, rect.right - ui.fdp(58f), rect.top + ui.fdp(22f), 1.0f, -32f)
        drawPetal(c, rect.left + ui.fdp(30f), rect.bottom - ui.fdp(70f), 1.3f, 150f)
        c.restore()

        border.strokeWidth = sw
        border.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            Color.argb(215, 255, 143, 184), Color.argb(150, 124, 58, 176), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, border)
    }

    private fun drawPetal(c: Canvas, x: Float, y: Float, s: Float, deg: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        val k = ui.fdp(1f) * s
        c.scale(k, k)
        c.drawPath(PetalShape.path, petal)
        c.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** ScrollView, который не вырастает выше заданной высоты (чтобы панель помещалась на экран). */
class MaxHeightScrollView(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}
