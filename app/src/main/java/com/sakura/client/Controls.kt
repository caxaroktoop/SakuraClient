package com.sakura.client

import android.content.Context
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
