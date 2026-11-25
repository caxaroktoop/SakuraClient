package com.sakura.client

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
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

/** Состояние окна модулей, общее для приложения и панели в игре. */
object ModulesState {
    var cat = 0
    var expanded: String? = null
}

private val HUD_TITLES = listOf("Часы", "Таймер игры", "Батарея", "Пинг", "CPS (бета)", "ОЗУ", "Сеть")
private val HUD_DESCS = listOf(
    "Текущее время", "Сколько вы уже играете", "Заряд и температура",
    "Задержка сети до сервера", "Клики в секунду по экрану", "Занятая память", "Скорость загрузки"
)

/**
 * «Окно модулей»: фильтр по категориям и карточки. У каждого модуля переключатель и шестерёнка,
 * по шестерёнке карточка раскрывается и показывает свои настройки.
 * extra(key, box) позволяет докинуть свои элементы в настройки конкретного модуля.
 */
fun addModulesWindow(
    c: Context, ui: Ui, prefs: SharedPreferences, parent: LinearLayout,
    onEditHud: () -> Unit,
    extra: ((String, LinearLayout) -> Unit)?,
    rerender: () -> Unit
) {
    addChips(c, ui, parent, listOf("Все", "Визуал", "HUD"), 3,
        { it == ModulesState.cat }, { ModulesState.cat = it; rerender() })

    fun caption(t: String) {
        parent.addView(
            ui.text(t, 10.5f, Palette.pink, true).apply { letterSpacing = 0.12f; setPadding(ui.dp(4), 0, 0, 0) },
            lp(WRAP, WRAP).apply { topMargin = ui.dp(16) }
        )
    }

    fun card(key: String, title: String, desc: String, icon: Int, settings: ((LinearLayout) -> Unit)?) {
        val on = prefs.getBoolean(key, false)
        val open = settings != null && ModulesState.expanded == key
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
            background = if (on) ui.rounded(Color.argb(46, 255, 143, 184), 16f, Palette.pink, 1.5f)
            else ui.rounded(Palette.surface2, 16f, Palette.stroke)
        }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ic = ui.icon(icon, if (on) Palette.onPink else Palette.pink).apply {
            val p = ui.dp(8)
            setPadding(p, p, p, p)
            background = ui.circle(if (on) Palette.pink else Palette.surface)
        }
        head.addView(ic, lp(ui.dp(36), ui.dp(36)))
        val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(ui.text(title, 13.5f, Palette.text, true), lp(WRAP, WRAP))
        texts.addView(ui.text(desc, 11f, Palette.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(2) })
        head.addView(texts, lp(0, WRAP, 1f).apply { leftMargin = ui.dp(10); rightMargin = ui.dp(6) })
        if (settings != null) {
            head.addView(
                ui.iconButton(R.drawable.ic_tune, 34f, 17f, if (open) Palette.pink else Palette.muted) {
                    ModulesState.expanded = if (open) null else key
                    rerender()
                }, lp(ui.dp(34), ui.dp(34)).apply { rightMargin = ui.dp(4) }
            )
        }
        head.addView(
            ToggleView(c, ui, on) { v ->
                prefs.edit().putBoolean(key, v).apply()
                rerender()
            }, lp(WRAP, WRAP)
        )
        box.addView(head, lp(MATCH, WRAP))
        if (open && settings != null) {
            box.addView(View(c).apply { setBackgroundColor(Palette.stroke) },
                lp(MATCH, maxOf(1, ui.dp(1))).apply { topMargin = ui.dp(10) })
            val inner = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            settings(inner)
            box.addView(inner, lp(MATCH, WRAP))
        }
        parent.addView(box, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
    }

    val showVisual = ModulesState.cat == 0 || ModulesState.cat == 1
    val showHud = ModulesState.cat == 0 || ModulesState.cat == 2

    if (showVisual) {
        if (ModulesState.cat == 0) caption("ВИЗУАЛ")
        card("petals_on", "Сакура", "Лепестки падают поверх игры", R.drawable.ic_petal) { s ->
            addSliderRow(c, ui, s, "Количество", 5, 40, prefs.getInt("petals_count", 18), "",
                { prefs.edit().putInt("petals_count", it).apply() }, {})
        }
        card("cross_on", "Прицел", "Свой прицел по центру экрана", R.drawable.ic_crosshair) { s ->
            addChips(c, ui, s, listOf("Крест", "Точка", "Круг", "Т-образ"), 4,
                { it == prefs.getInt("cross_style", 0) },
                { prefs.edit().putInt("cross_style", it).apply(); rerender() })
            addSliderRow(c, ui, s, "Размер", 8, 48, prefs.getInt("cross_size", 20), "",
                { prefs.edit().putInt("cross_size", it).apply() }, {})
            val sel = prefs.getInt("cross_color", 0)
            val colors = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            FxView.COLORS.forEachIndexed { i, col ->
                val dot = View(c).apply {
                    background = ui.circle(col).apply { if (i == sel) setStroke(maxOf(1, ui.dp(2)), Color.WHITE) }
                    setOnClickListener { prefs.edit().putInt("cross_color", i).apply(); rerender() }
                }
                colors.addView(dot, lp(ui.dp(26), ui.dp(26)).apply { rightMargin = ui.dp(10) })
            }
            s.addView(colors, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
        }
        card("dim_on", "Ночной режим", "Затемнение и тёплый оттенок", R.drawable.ic_moon) { s ->
            addSliderRow(c, ui, s, "Затемнение", 0, 60, prefs.getInt("dim_level", 25), "%",
                { prefs.edit().putInt("dim_level", it).apply() }, {})
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(ui.text("Тёплый оттенок", 12f, Palette.muted), lp(0, WRAP, 1f))
            row.addView(
                ToggleView(c, ui, prefs.getBoolean("dim_warm", false)) { on ->
                    prefs.edit().putBoolean("dim_warm", on).apply()
                }, lp(WRAP, WRAP)
            )
            s.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
        }
    }

    if (showHud) {
        if (ModulesState.cat == 0) caption("HUD")
        val editBox = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            background = ui.rounded(Palette.surface, 16f, Palette.stroke)
        }
        editBox.addView(ui.text("Редактор HUD", 13.5f, Palette.text, true), lp(WRAP, WRAP))
        editBox.addView(
            ui.text("Перетащи включённые виджеты в нужное место на экране игры.", 11f, Palette.muted)
                .apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(3) }
        )
        editBox.addView(
            ui.pill("Расположить виджеты", STYLE_PRIMARY, R.drawable.ic_grid) { onEditHud() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
        )
        parent.addView(editBox, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })

        HudKeys.keys.forEachIndexed { i, k ->
            card("hud_$k", HUD_TITLES[i], HUD_DESCS[i], HudKeys.icons[i]) { s ->
                addSliderRow(c, ui, s, "Размер", 70, 160, prefs.getInt("hudscale_$k", 100), "%",
                    { prefs.edit().putInt("hudscale_$k", it).apply() },
                    { prefs.edit().putLong("hud_rev", SystemClock.elapsedRealtime()).apply() })
                extra?.invoke(k, s)
            }
        }
    }
}
