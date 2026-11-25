package com.sakura.client

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Строка «название — значение» и ползунок под ней. */
fun addSliderRow(
    c: Context, ui: Ui, parent: LinearLayout, label: String,
    min: Int, max: Int, cur: Int, unit: String,
    onChange: (Int) -> Unit, onDone: (Int) -> Unit
) {
    val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
    row.addView(ui.text(label, 12f, Palette.muted), lp(0, WRAP, 1f))
    val value = ui.text("$cur$unit", 12f, Palette.text, true)
    row.addView(value, lp(WRAP, WRAP))
    parent.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
    parent.addView(
        SliderView(c, ui, min, max, cur, { v -> value.text = "$v$unit"; onChange(v) }, onDone),
        lp(MATCH, WRAP)
    )
}

/** Ряд «пилюль» одинаковой ширины. Несколько рядов, если элементов больше perRow. */
fun addChips(
    c: Context, ui: Ui, parent: LinearLayout, labels: List<String>, perRow: Int,
    selected: (Int) -> Boolean, onClick: (Int) -> Unit
) {
    labels.chunked(perRow).forEachIndexed { r, group ->
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        group.forEachIndexed { i, label ->
            val idx = r * perRow + i
            val sel = selected(idx)
            val chip = ui.text(label, 11.5f, if (sel) Palette.onPink else Palette.muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(8), 0, ui.dp(8))
                background = if (sel) ui.gradient(Palette.pink, Palette.pinkDeep, 11f, GradientDrawable.Orientation.LEFT_RIGHT)
                else ui.rounded(Palette.surface, 11f, Palette.stroke)
                isClickable = true
                setOnClickListener { onClick(idx) }
            }
            row.addView(chip, lp(0, WRAP, 1f).apply { if (i > 0) leftMargin = ui.dp(6) })
        }
        repeat(perRow - group.size) {
            row.addView(View(c), lp(0, 1, 1f).apply { leftMargin = ui.dp(6) })
        }
        parent.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
    }
}

/**
 * Настройки включённых модулей: добавляет под плитками блоки с ползунками и выбором стиля.
 * rerender вызывается, когда нужно перерисовать блок (например, после выбора стиля).
 */
fun addModuleSettings(
    c: Context, ui: Ui, prefs: SharedPreferences, parent: LinearLayout,
    onEditHud: () -> Unit, rerender: () -> Unit
) {
    fun section(title: String): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12))
            background = ui.rounded(Color.argb(110, 42, 27, 52), 16f, Palette.stroke)
        }
        box.addView(ui.text(title, 10.5f, Palette.pink, true).apply { letterSpacing = 0.12f }, lp(WRAP, WRAP))
        parent.addView(box, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
        return box
    }

    if (prefs.getBoolean("petals_on", false)) {
        val s = section("ЛЕПЕСТКИ")
        addSliderRow(c, ui, s, "Количество", 5, 40, prefs.getInt("petals_count", 18), "",
            { prefs.edit().putInt("petals_count", it).apply() }, {})
    }

    if (prefs.getBoolean("cross_on", false)) {
        val s = section("ПРИЦЕЛ")
        addChips(c, ui, s, listOf("Крест", "Точка", "Круг", "Т-образ"), 4,
            { it == prefs.getInt("cross_style", 0) },
            { prefs.edit().putInt("cross_style", it).apply(); rerender() })
        addSliderRow(c, ui, s, "Размер", 8, 48, prefs.getInt("cross_size", 20), "",
            { prefs.edit().putInt("cross_size", it).apply() }, {})
        val sel = prefs.getInt("cross_color", 0)
        val colors = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        FxView.COLORS.forEachIndexed { i, col ->
            val dot = View(c).apply {
                background = ui.circle(col).apply { if (i == sel) setStroke(maxOf(1, ui.dp(2)), Color.WHITE) }
                setOnClickListener {
                    prefs.edit().putInt("cross_color", i).apply()
                    rerender()
                }
            }
            colors.addView(dot, lp(ui.dp(26), ui.dp(26)).apply { rightMargin = ui.dp(10) })
        }
        s.addView(colors, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
    }

    if (prefs.getBoolean("hud_on", false)) {
        val s = section("HUD")
        addChips(c, ui, s, HudKeys.labels, 4,
            { prefs.getBoolean("hud_" + HudKeys.keys[it], HudKeys.defaults[it]) },
            { i ->
                val k = "hud_" + HudKeys.keys[i]
                prefs.edit().putBoolean(k, !prefs.getBoolean(k, HudKeys.defaults[i])).apply()
                rerender()
            })
        s.addView(
            ui.text(
                "CPS считает касания экрана (экспериментально, на некоторых телефонах не работает). " +
                    "Пинг и сеть — замеры сети, а не данные игры.", 11f, Palette.muted
            ).apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
        )
        s.addView(
            ui.pill("Расположить виджеты", STYLE_PRIMARY, R.drawable.ic_grid) { onEditHud() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(12) }
        )
    }

    if (prefs.getBoolean("dim_on", false)) {
        val s = section("НОЧНОЙ РЕЖИМ")
        addSliderRow(c, ui, s, "Затемнение", 0, 60, prefs.getInt("dim_level", 25), "%",
            { prefs.edit().putInt("dim_level", it).apply() }, {})
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ui.text("Тёплый оттенок", 12f, Palette.muted), lp(0, WRAP, 1f))
        row.addView(
            ToggleView(c, ui, prefs.getBoolean("dim_warm", false)) { on ->
                prefs.edit().putBoolean("dim_warm", on).apply()
            }, lp(WRAP, WRAP)
        )
        s.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
    }
}
