package com.sakura.client

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

object Palette {
    val bg = Color.parseColor("#120B16")
    val surface = Color.parseColor("#1D1325")
    val surface2 = Color.parseColor("#2A1B34")
    val stroke = Color.parseColor("#3C2849")
    val pink = Color.parseColor("#FF8FB8")
    val pinkDeep = Color.parseColor("#E0457B")
    val onPink = Color.parseColor("#2A0F22")
    val text = Color.parseColor("#F8EEF5")
    val muted = Color.parseColor("#B5A2BC")
    val ok = Color.parseColor("#7BE0A5")
    val warn = Color.parseColor("#FFB86B")
    val danger = Color.parseColor("#FF6B8A")
}

const val STYLE_PRIMARY = 0
const val STYLE_WHITE = 1
const val STYLE_GHOST = 2
const val STYLE_DANGER = 3

/** Маленький набор помощников, чтобы рисовать красивый интерфейс без XML и библиотек. */
class Ui(val ctx: Context, val scale: Float = 1f) {
    private val density = ctx.resources.displayMetrics.density

    fun fdp(v: Float): Float = v * density * scale
    fun dp(v: Float): Int = fdp(v).roundToInt()
    fun dp(v: Int): Int = dp(v.toFloat())

    fun rounded(fill: Int, radiusDp: Float, stroke: Int = Color.TRANSPARENT, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = fdp(radiusDp)
            if (stroke != Color.TRANSPARENT) setStroke(maxOf(1, dp(strokeDp)), stroke)
        }

    fun circle(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    fun gradient(
        c1: Int, c2: Int, radiusDp: Float,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR
    ): GradientDrawable = GradientDrawable(orientation, intArrayOf(c1, c2)).apply { cornerRadius = fdp(radiusDp) }

    fun ripple(base: Drawable?, radiusDp: Float = 12f, oval: Boolean = false): RippleDrawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = fdp(radiusDp)
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), base, mask)
    }

    fun text(t: String, sizeDp: Float, color: Int, bold: Boolean = false): TextView = TextView(ctx).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_PX, fdp(sizeDp))
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    /** Картинка без собственных LayoutParams: размер задаёт тот, кто её добавляет. */
    fun icon(res: Int, tint: Int? = null): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        if (tint != null) setColorFilter(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun iconButton(
        res: Int, sizeDp: Float, iconDp: Float, tint: Int,
        bg: Drawable? = null, onClick: () -> Unit
    ): ImageView = icon(res, tint).apply {
        val pad = dp((sizeDp - iconDp) / 2f)
        setPadding(pad, pad, pad, pad)
        background = ripple(bg, sizeDp / 2f, true)
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun pill(label: String, style: Int, iconRes: Int = 0, onClick: () -> Unit): LinearLayout {
        val bgD: Drawable
        val fg: Int
        when (style) {
            STYLE_PRIMARY -> {
                bgD = gradient(Palette.pink, Palette.pinkDeep, 16f, GradientDrawable.Orientation.LEFT_RIGHT)
                fg = Palette.onPink
            }
            STYLE_WHITE -> {
                bgD = rounded(Color.WHITE, 16f)
                fg = Palette.onPink
            }
            STYLE_DANGER -> {
                bgD = rounded(Palette.surface2, 16f, Palette.danger)
                fg = Palette.danger
            }
            else -> {
                bgD = rounded(Palette.surface2, 16f, Palette.stroke)
                fg = Palette.text
            }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = ripple(bgD, 16f)
            isClickable = true
            if (iconRes != 0) addView(icon(iconRes, fg), lp(dp(18), dp(18)).apply { rightMargin = dp(8) })
            addView(text(label, 14f, fg, true))
            setOnClickListener { onClick() }
        }
    }

    fun pillLabel(pill: LinearLayout): TextView = pill.getChildAt(pill.childCount - 1) as TextView
}

/** Тонкая полоска прогресса трека. */
class ThinProgress(ctx: Context) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.stroke }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.pink }
    var fraction = 0f
        set(v) {
            val n = v.coerceIn(0f, 1f)
            if (n != field) { field = n; invalidate() }
        }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val r = h / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, track)
        if (fraction > 0f) canvas.drawRoundRect(0f, 0f, maxOf(h, width * fraction), h, r, r, fill)
    }
}

/** Свой ползунок: не зависит от темы окна, одинаково выглядит в сервисе и в приложении. */
class SliderView(
    ctx: Context, private val ui: Ui,
    private val min: Int, private val max: Int, start: Int,
    private val onChange: (Int) -> Unit, private val onDone: (Int) -> Unit
) : View(ctx) {
    var value = start.coerceIn(min, max)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.stroke }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.pink }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pad = ui.fdp(10f)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), ui.dp(30))
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val half = ui.fdp(4f) / 2f
        val right = width - pad
        val f = (value - min).toFloat() / (max - min).coerceAtLeast(1)
        val kx = pad + (right - pad) * f
        canvas.drawRoundRect(pad, cy - half, right, cy + half, half, half, track)
        canvas.drawRoundRect(pad, cy - half, kx, cy + half, half, half, fill)
        canvas.drawCircle(kx, cy, ui.fdp(8f), knob)
        canvas.drawCircle(kx, cy, ui.fdp(4f), fill)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> { update(e.x); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { onDone(value); return true }
        }
        return super.onTouchEvent(e)
    }

    private fun update(x: Float) {
        val f = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        val v = (min + f * (max - min)).roundToInt()
        if (v != value) { value = v; invalidate(); onChange(v) }
    }
}

/** Переключатель в виде пилюли. */
class ToggleView(
    ctx: Context, private val ui: Ui, var checked: Boolean,
    private val onChange: (Boolean) -> Unit
) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    init {
        setOnClickListener {
            checked = !checked
            invalidate()
            onChange(checked)
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(ui.dp(44), ui.dp(26))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = if (checked) Palette.pink else Palette.stroke
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, track)
        val r = h / 2f - ui.fdp(3f)
        val cx = if (checked) w - h / 2f else h / 2f
        knob.color = if (checked) Palette.onPink else Color.WHITE
        canvas.drawCircle(cx, h / 2f, r, knob)
    }
}
