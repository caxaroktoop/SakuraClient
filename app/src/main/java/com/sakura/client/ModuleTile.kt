package com.sakura.client

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Плитка модуля: нажал, и модуль включился или выключился. Подсвечивается розовым, когда активен. */
class ModuleTile(
    c: Context,
    private val ui: Ui,
    private val iconRes: Int,
    title: String,
    sub: String?,
    private val key: String,
    private val prefs: SharedPreferences,
    private val compact: Boolean = false
) : LinearLayout(c) {
    private val icon: ImageView = ui.icon(iconRes)
    private val state = ui.text("", 10f, Palette.muted, true)
    private val circleDp = if (compact) 32f else 38f

    init {
        orientation = VERTICAL
        val pad = if (compact) 10 else 14
        setPadding(ui.dp(pad), ui.dp(pad), ui.dp(pad), ui.dp(pad))
        isClickable = true

        val top = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ip = ui.dp(circleDp * 0.24f)
        icon.setPadding(ip, ip, ip, ip)
        top.addView(icon, lp(ui.dp(circleDp), ui.dp(circleDp)))
        top.addView(android.view.View(c), lp(0, 1, 1f))
        top.addView(state, lp(WRAP, WRAP))
        addView(top, lp(MATCH, WRAP))

        addView(ui.text(title, if (compact) 12.5f else 14f, Palette.text, true),
            lp(MATCH, WRAP).apply { topMargin = ui.dp(if (compact) 8 else 12) })
        if (sub != null && !compact) {
            addView(ui.text(sub, 11f, Palette.muted).apply { setLineSpacing(0f, 1.1f) },
                lp(MATCH, WRAP).apply { topMargin = ui.dp(3) })
        }

        setOnClickListener {
            prefs.edit().putBoolean(key, !isOn()).apply()
            sync()
        }
        sync()
    }

    private fun isOn() = prefs.getBoolean(key, false)

    /** Перерисовывает вид по текущему значению настройки. */
    fun sync() {
        val on = isOn()
        val bg = if (on) {
            ui.gradient(Color.argb(80, 255, 143, 184), Color.argb(80, 124, 58, 176), 18f).apply {
                setStroke(maxOf(1, ui.dp(1.5f)), Palette.pink)
            }
        } else ui.rounded(Palette.surface2, 18f, Palette.stroke)
        background = ui.ripple(bg, 18f)
        icon.background = ui.circle(if (on) Palette.pink else Palette.surface)
        icon.setColorFilter(if (on) Palette.onPink else Palette.pink)
        state.text = if (on) "ВКЛ" else "ВЫКЛ"
        state.setTextColor(if (on) Palette.pink else Palette.muted)
    }
}
