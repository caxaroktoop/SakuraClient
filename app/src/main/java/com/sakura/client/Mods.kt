package com.sakura.client

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/** Цвета окна мод-меню: нейтральный тёмный фон и розовый акцент. */
object Gui {
    val bg = Color.rgb(24, 25, 29)
    val bar = Color.rgb(27, 28, 33)
    val card = Color.rgb(31, 32, 38)
    val cardStroke = Color.rgb(46, 47, 55)
    val border = Color.rgb(84, 66, 74)
    val side = Color.rgb(35, 36, 43)
    val field = Color.rgb(38, 39, 46)
    val text = Color.rgb(237, 233, 238)
    val muted = Color.rgb(124, 122, 132)
    val accent = Palette.pink
}

object GuiState {
    var cat = 0
}

fun clipRounded(v: View, ui: Ui, radiusDp: Float) {
    v.clipToOutline = true
    v.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, ui.fdp(radiusDp))
        }
    }
}

/** Круглый индикатор модуля: залитый розовый, если включён, и тонкое кольцо, если выключен. */
class DotView(ctx: Context, private val ui: Ui, var on: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(ui.dp(18), ui.dp(18))
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (on) {
            p.style = Paint.Style.FILL
            p.color = Color.argb(70, 255, 143, 184)
            c.drawCircle(cx, cy, ui.fdp(8.5f), p)
            p.color = Gui.accent
            c.drawCircle(cx, cy, ui.fdp(6f), p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = ui.fdp(1.6f)
            p.color = Gui.muted
            c.drawCircle(cx, cy, ui.fdp(6.5f), p)
        }
    }
}

/** Всё, что окну нужно от «хозяина» (приложение или панель в игре). */
class GuiEnv(
    val c: Context,
    val ui: Ui,
    val prefs: SharedPreferences,
    val wide: Boolean,
    val showSearch: Boolean,
    val innerScroll: Boolean,
    val onClose: (() -> Unit)?,
    val onEditHud: () -> Unit,
    val dragTitle: ((View) -> Unit)?,
    val musicContent: (LinearLayout) -> Unit,
    val settingsContent: (LinearLayout, () -> Unit) -> Unit,
    val moduleExtra: ((String, LinearLayout) -> Unit)?
)

class Mod(
    val key: String,
    val cat: Int,
    val title: String,
    val desc: String,
    val settings: ((GuiEnv, LinearLayout, () -> Unit) -> Unit)? = null
)

object Mods {
    const val HUD = 0
    const val VISUALS = 1
    const val UTILITY = 2
    const val MUSIC = 3
    const val CONFIGS = 4
    const val SETTINGS = 5
    val CATS = listOf("HUD", "Visuals", "Utility", "Music", "Configs", "Settings")

    private val hudTitles = listOf("Часы", "Таймер игры", "Батарея", "Пинг", "CPS (бета)", "ОЗУ", "Сеть")
    private val hudDescs = listOf(
        "текущее время", "сколько вы уже играете", "заряд и температура",
        "задержка сети до сервера", "клики в секунду по экрану", "занятая память", "скорость загрузки"
    )

    val all: List<Mod> by lazy { build() }

    private fun build(): List<Mod> {
        val l = ArrayList<Mod>()
        HudKeys.keys.forEachIndexed { i, k ->
            l.add(Mod("hud_$k", HUD, hudTitles[i], hudDescs[i]) { env, s, _ -> hudScale(env, s, k) })
        }
        l.add(Mod("petals_on", VISUALS, "Сакура", "лепестки падают поверх игры") { env, s, _ ->
            addSliderRow(env.c, env.ui, s, "Количество", 5, 40, env.prefs.getInt("petals_count", 18), "",
                { env.prefs.edit().putInt("petals_count", it).apply() }, {})
        })
        l.add(Mod("cross_on", VISUALS, "Прицел", "свой прицел по центру экрана") { env, s, rr -> crossSettings(env, s, rr) })
        l.add(Mod("dim_on", VISUALS, "Ночной режим", "затемнение и тёплый оттенок") { env, s, _ ->
            addSliderRow(env.c, env.ui, s, "Затемнение", 0, 60, env.prefs.getInt("dim_level", 25), "%",
                { env.prefs.edit().putInt("dim_level", it).apply() }, {})
            val row = LinearLayout(env.c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(env.ui.text("Тёплый оттенок", 12f, Gui.muted), lp(0, WRAP, 1f))
            row.addView(ToggleView(env.c, env.ui, env.prefs.getBoolean("dim_warm", false)) { on ->
                env.prefs.edit().putBoolean("dim_warm", on).apply()
            }, lp(WRAP, WRAP))
            s.addView(row, lp(MATCH, WRAP).apply { topMargin = env.ui.dp(10) })
        })
        l.add(Mod("zoom_on", UTILITY, "Zoom", "увеличивает экран (через Спец. возможности)") { env, s, _ -> zoomSettings(env, s) })
        l.add(Mod("bright_on", UTILITY, "Макс. яркость", "яркость экрана на 100%, пока играешь"))
        l.add(Mod("awake_on", UTILITY, "Не гасить экран", "экран не гаснет при простое"))
        return l
    }

    private fun hudScale(env: GuiEnv, s: LinearLayout, k: String) {
        addSliderRow(env.c, env.ui, s, "Размер", 70, 160, env.prefs.getInt("hudscale_$k", 100), "%",
            { env.prefs.edit().putInt("hudscale_$k", it).apply() },
            { env.prefs.edit().putLong("hud_rev", SystemClock.elapsedRealtime()).apply() })
        env.moduleExtra?.invoke("hud_$k", s)
    }

    private fun crossSettings(env: GuiEnv, s: LinearLayout, rr: () -> Unit) {
        val ui = env.ui
        val p = env.prefs
        addChips(env.c, ui, s, listOf("Крест", "Точка", "Круг", "Т-образ"), 4,
            { it == p.getInt("cross_style", 0) },
            { p.edit().putInt("cross_style", it).apply(); rr() })
        addSliderRow(env.c, ui, s, "Размер", 8, 48, p.getInt("cross_size", 20), "",
            { p.edit().putInt("cross_size", it).apply() }, {})
        val sel = p.getInt("cross_color", 0)
        val colors = LinearLayout(env.c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        FxView.COLORS.forEachIndexed { i, col ->
            val dot = View(env.c).apply {
                background = ui.circle(col).apply { if (i == sel) setStroke(maxOf(1, ui.dp(2)), Color.WHITE) }
                setOnClickListener { p.edit().putInt("cross_color", i).apply(); rr() }
            }
            colors.addView(dot, lp(ui.dp(26), ui.dp(26)).apply { rightMargin = ui.dp(10) })
        }
        s.addView(colors, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
    }

    private fun zoomSettings(env: GuiEnv, s: LinearLayout) {
        val ui = env.ui
        addSliderRow(env.c, ui, s, "Увеличение", 150, 400, env.prefs.getInt("zoom_level", 200), "%",
            { env.prefs.edit().putInt("zoom_level", it).apply() }, {})
        val ok = SakuraAccessibility.instance != null
        s.addView(
            ui.text(
                if (ok) "Служба Zoom включена ✓"
                else "Служба выключена. Открой Специальные возможности и включи «Sakura Zoom».",
                11f, if (ok) Palette.ok else Palette.warn
            ).apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
        )
        if (!ok) {
            s.addView(
                ui.pill("Открыть настройки", STYLE_GHOST) {
                    try {
                        env.c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                },
                lp(MATCH, WRAP).apply { topMargin = ui.dp(8) }
            )
        }
        s.addView(
            ui.text("Увеличивается весь экран вместе с игрой и панелью. Выключи модуль, чтобы вернуть обычный вид.",
                11f, Gui.muted).apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(8) }
        )
    }
}

/** Профили: слепок состояния модулей, их размеров и положения виджетов. */
object Profiles {
    private val re = Regex("^(petals_|cross_|dim_|bright_|awake_|hud_|hudscale_|w_).*")
    private val skip = setOf("hud_edit", "hud_rev")

    fun save(p: SharedPreferences, n: Int) {
        val o = JSONObject()
        for ((k, v) in p.all) {
            if (re.matches(k) && k !in skip && (v is Boolean || v is Int || v is Long)) o.put(k, v)
        }
        p.edit().putString("profile_$n", o.toString()).apply()
    }

    fun load(p: SharedPreferences, n: Int) {
        val s = p.getString("profile_$n", null) ?: return
        val o = JSONObject(s)
        val ed = p.edit()
        for ((k, _) in p.all) if (re.matches(k) && k !in skip) ed.remove(k)
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            when (val v = o.get(k)) {
                is Boolean -> ed.putBoolean(k, v)
                is Int -> ed.putInt(k, v)
                is Long -> ed.putLong(k, v)
            }
        }
        ed.apply()
    }
}

/**
 * Окно мод-меню: заголовок с поиском, категории (слева или чипами сверху на узком экране),
 * карточки модулей с круглым индикатором, шестерёнка открывает настройки модуля.
 */
class ClickGuiView(private val e: GuiEnv) : LinearLayout(e.c) {
    private val c = e.c
    private val ui = e.ui
    private val prefs = e.prefs
    private var cat = GuiState.cat
    private var openKey: String? = null
    private var query = ""
    private val body = LinearLayout(c)
    private var contentBox: LinearLayout? = null
    private var headerCount: TextView? = null
    private var headerMods: List<Mod> = emptyList()
    private var searchEt: EditText? = null

    init {
        orientation = VERTICAL
        addView(titleBar(), lp(MATCH, WRAP))
        addView(body, if (e.innerScroll) lp(MATCH, 0, 1f) else lp(MATCH, WRAP))
        renderBody()
    }

    // ---------- заголовок ----------

    private fun titleBar(): View {
        val wrap = LinearLayout(c).apply {
            orientation = VERTICAL
            setBackgroundColor(Gui.bar)
            setPadding(ui.dp(14), ui.dp(10), ui.dp(12), ui.dp(10))
        }
        val row = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(ui.icon(R.drawable.ic_sakura), lp(ui.dp(26), ui.dp(26)))
        row.addView(ui.text("SAKURA", 15f, Gui.text, true), lp(WRAP, WRAP).apply { leftMargin = ui.dp(8) })
        row.addView(ui.text("v1.11 · beta", 10.5f, Gui.muted), lp(WRAP, WRAP).apply { leftMargin = ui.dp(8) })
        if (e.showSearch && e.wide) {
            row.addView(searchBox(), lp(0, WRAP, 1f).apply { leftMargin = ui.dp(14); rightMargin = ui.dp(10) })
        } else {
            row.addView(View(c), lp(0, 1, 1f))
        }
        e.onClose?.let { close ->
            row.addView(ui.iconButton(R.drawable.ic_close, 32f, 16f, Gui.muted) { close() }, lp(ui.dp(32), ui.dp(32)))
        }
        wrap.addView(row, lp(MATCH, WRAP))
        if (e.showSearch && !e.wide) wrap.addView(searchBox(), lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
        e.dragTitle?.invoke(wrap)
        return wrap
    }

    private fun searchBox(): View {
        val box = LinearLayout(c).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            background = ui.rounded(Gui.field, 10f, Gui.cardStroke)
        }
        box.addView(ui.icon(R.drawable.ic_search, Gui.muted), lp(ui.dp(16), ui.dp(16)))
        val et = EditText(c).apply {
            hint = "поиск модулей"
            setHintTextColor(Gui.muted)
            setTextColor(Gui.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.fdp(13f))
            setSingleLine()
            background = null
            setPadding(ui.dp(8), ui.dp(9), 0, ui.dp(9))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    renderContent()
                }
            })
        }
        searchEt = et
        box.addView(et, lp(0, WRAP, 1f))
        return box
    }

    // ---------- тело: категории + содержимое ----------

    private fun selectCat(i: Int) {
        cat = i
        GuiState.cat = i
        openKey = null
        if (query.isNotEmpty()) {
            query = ""
            searchEt?.setText("")
        }
        renderBody()
    }

    private fun renderBody() {
        body.removeAllViews()
        val area = contentArea()
        if (e.wide) {
            body.orientation = HORIZONTAL
            body.addView(sidebar(), if (e.innerScroll) lp(ui.dp(124), MATCH) else lp(ui.dp(124), WRAP))
            body.addView(area, if (e.innerScroll) lp(0, MATCH, 1f) else lp(0, WRAP, 1f))
        } else {
            body.orientation = VERTICAL
            body.addView(chipsRow(), lp(MATCH, WRAP))
            body.addView(area, if (e.innerScroll) lp(MATCH, 0, 1f) else lp(MATCH, WRAP))
        }
        renderContent()
    }

    private fun sidebar(): View {
        val col = LinearLayout(c).apply {
            orientation = VERTICAL
            setPadding(ui.dp(8), ui.dp(10), ui.dp(6), ui.dp(8))
        }
        Mods.CATS.forEachIndexed { i, name ->
            val sel = i == cat && query.isBlank()
            val row = LinearLayout(c).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(6), ui.dp(9), ui.dp(10), ui.dp(9))
                background = if (sel) ui.rounded(Gui.side, 8f) else ui.ripple(null, 8f)
                isClickable = true
                setOnClickListener { selectCat(i) }
            }
            val mark = View(c).apply { if (sel) background = ui.rounded(Gui.accent, 2f) }
            row.addView(mark, lp(ui.dp(3), ui.dp(16)).apply { rightMargin = ui.dp(8) })
            row.addView(ui.text(name, 13f, if (sel) Gui.text else Gui.muted, true), lp(0, WRAP, 1f))
            val n = Mods.all.count { it.cat == i }
            row.addView(ui.text(if (n > 0) "$n" else "—", 11f, Gui.muted), lp(WRAP, WRAP))
            col.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(2) })
        }
        return col
    }

    private fun chipsRow(): View {
        val hs = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(2))
        }
        val row = LinearLayout(c).apply { orientation = HORIZONTAL }
        Mods.CATS.forEachIndexed { i, name ->
            val sel = i == cat && query.isBlank()
            val chip = ui.text(name, 12f, if (sel) Palette.onPink else Gui.muted, true).apply {
                setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7))
                background = if (sel) ui.gradient(Palette.pink, Palette.pinkDeep, 12f, GradientDrawable.Orientation.LEFT_RIGHT)
                else ui.rounded(Gui.field, 12f, Gui.cardStroke)
                isClickable = true
                setOnClickListener { selectCat(i) }
            }
            row.addView(chip, lp(WRAP, WRAP).apply { rightMargin = ui.dp(8) })
        }
        hs.addView(row)
        return hs
    }

    private fun contentArea(): View {
        val box = LinearLayout(c).apply {
            orientation = VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14))
        }
        contentBox = box
        if (!e.innerScroll) return box
        return ScrollView(c).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(box, FrameLayout.LayoutParams(MATCH, WRAP))
        }
    }

    // ---------- содержимое ----------

    private fun refreshCounts() {
        headerCount?.text = "${headerMods.count { prefs.getBoolean(it.key, false) }} из ${headerMods.size} включено"
    }

    private fun renderContent() {
        val box = contentBox ?: return
        box.removeAllViews()
        headerCount = null
        val q = query.trim().lowercase()
        if (openKey != null && q.isEmpty()) {
            renderSettings(box)
            return
        }
        val mods = if (q.isNotEmpty()) Mods.all.filter { it.title.lowercase().contains(q) || it.desc.lowercase().contains(q) }
        else Mods.all.filter { it.cat == cat }
        headerMods = mods
        val showMods = q.isNotEmpty() || cat <= Mods.UTILITY

        val head = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ui.text(if (q.isNotEmpty()) "Результаты" else Mods.CATS[cat], 15f, Gui.text, true), lp(0, WRAP, 1f))
        if (showMods) {
            val hc = ui.text("", 11f, Gui.muted, true)
            headerCount = hc
            head.addView(hc, lp(WRAP, WRAP))
        }
        box.addView(head, lp(MATCH, WRAP))
        refreshCounts()

        val inner = LinearLayout(c).apply { orientation = VERTICAL }
        when {
            q.isEmpty() && cat == Mods.MUSIC -> {
                box.addView(inner, lp(MATCH, WRAP).apply { topMargin = ui.dp(12) })
                e.musicContent(inner)
            }
            q.isEmpty() && cat == Mods.CONFIGS -> configs(box)
            q.isEmpty() && cat == Mods.SETTINGS -> {
                box.addView(inner, lp(MATCH, WRAP).apply { topMargin = ui.dp(4) })
                e.settingsContent(inner) { renderContent() }
            }
            else -> {
                if (q.isEmpty() && cat == Mods.HUD) editCard(box)
                grid(box, mods)
                if (mods.isEmpty()) {
                    box.addView(
                        ui.text("Ничего не найдено", 12f, Gui.muted).apply { gravity = Gravity.CENTER },
                        lp(MATCH, WRAP).apply { topMargin = ui.dp(24) }
                    )
                }
            }
        }
    }

    private fun grid(box: LinearLayout, mods: List<Mod>) {
        mods.chunked(2).forEach { pair ->
            val row = LinearLayout(c).apply { orientation = HORIZONTAL }
            pair.forEachIndexed { i, m ->
                row.addView(moduleCard(m), lp(0, MATCH, 1f).apply { if (i > 0) leftMargin = ui.dp(8) })
            }
            if (pair.size == 1) row.addView(View(c), lp(0, 1, 1f).apply { leftMargin = ui.dp(8) })
            box.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
        }
    }

    private fun moduleCard(m: Mod): View {
        var on = prefs.getBoolean(m.key, false)
        val card = LinearLayout(c).apply {
            orientation = VERTICAL
            setPadding(ui.dp(11), ui.dp(9), ui.dp(9), ui.dp(9))
        }
        fun bg() = ui.ripple(
            ui.rounded(Gui.card, 12f, if (on) Color.argb(150, 255, 143, 184) else Gui.cardStroke), 12f
        )
        card.background = bg()
        val top = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(
            ui.text(m.title, 13f, Gui.text, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            lp(0, WRAP, 1f)
        )
        val dot = DotView(c, ui, on)
        top.addView(dot, lp(WRAP, WRAP))
        card.addView(top, lp(MATCH, WRAP))
        card.addView(
            ui.text(m.desc, 10.5f, Gui.muted).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.1f)
            },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(4) }
        )
        if (m.settings != null) {
            val gear = ui.iconButton(R.drawable.ic_tune, 28f, 15f, Gui.muted) {
                openKey = m.key
                renderContent()
            }
            card.addView(gear, lp(ui.dp(28), ui.dp(28)).apply { gravity = Gravity.END; topMargin = ui.dp(2) })
        }
        card.isClickable = true
        card.setOnClickListener {
            on = !on
            prefs.edit().putBoolean(m.key, on).apply()
            dot.on = on
            dot.invalidate()
            card.background = bg()
            refreshCounts()
        }
        return card
    }

    private fun editCard(box: LinearLayout) {
        val card = LinearLayout(c).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(10))
            background = ui.rounded(Gui.card, 12f, Gui.cardStroke)
        }
        val col = LinearLayout(c).apply { orientation = VERTICAL }
        col.addView(ui.text("Редактор HUD", 13f, Gui.text, true), lp(WRAP, WRAP))
        col.addView(ui.text("перетащи включённые виджеты на экране игры", 10.5f, Gui.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(3) })
        card.addView(col, lp(0, WRAP, 1f))
        card.addView(smallBtn("Открыть", true) { e.onEditHud() }, lp(WRAP, WRAP))
        box.addView(card, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
    }

    private fun smallBtn(label: String, primary: Boolean, onClick: () -> Unit): TextView =
        ui.text(label, 12f, if (primary) Palette.onPink else Gui.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
            background = ui.ripple(
                if (primary) ui.gradient(Palette.pink, Palette.pinkDeep, 10f, GradientDrawable.Orientation.LEFT_RIGHT)
                else ui.rounded(Gui.field, 10f, Gui.cardStroke), 10f
            )
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun renderSettings(box: LinearLayout) {
        val m = Mods.all.firstOrNull { it.key == openKey }
        if (m == null) {
            openKey = null
            renderContent()
            return
        }
        val head = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(
            ui.iconButton(R.drawable.ic_back, 34f, 18f, Gui.text, ui.circle(Gui.field)) {
                openKey = null
                renderContent()
            }, lp(ui.dp(34), ui.dp(34))
        )
        head.addView(ui.text(m.title, 15f, Gui.text, true), lp(0, WRAP, 1f).apply { leftMargin = ui.dp(10) })
        head.addView(
            ToggleView(c, ui, prefs.getBoolean(m.key, false)) { v -> prefs.edit().putBoolean(m.key, v).apply() },
            lp(WRAP, WRAP)
        )
        box.addView(head, lp(MATCH, WRAP))
        box.addView(ui.text(m.desc, 11.5f, Gui.muted), lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
        val inner = LinearLayout(c).apply { orientation = VERTICAL }
        box.addView(inner, lp(MATCH, WRAP).apply { topMargin = ui.dp(4) })
        m.settings?.invoke(e, inner) { renderContent() }
    }

    private fun configs(box: LinearLayout) {
        box.addView(
            ui.text("Профиль хранит включённые модули, их настройки и положение виджетов.", 11f, Gui.muted)
                .apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(8) }
        )
        for (n in 1..3) {
            val has = prefs.contains("profile_$n")
            val row = LinearLayout(c).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(10))
                background = ui.rounded(Gui.card, 12f, Gui.cardStroke)
            }
            val col = LinearLayout(c).apply { orientation = VERTICAL }
            col.addView(ui.text("Профиль $n", 13f, Gui.text, true), lp(WRAP, WRAP))
            col.addView(
                ui.text(if (has) "сохранён" else "пусто", 10.5f, if (has) Palette.ok else Gui.muted),
                lp(WRAP, WRAP).apply { topMargin = ui.dp(3) }
            )
            row.addView(col, lp(0, WRAP, 1f))
            row.addView(smallBtn("Сохранить", false) {
                Profiles.save(prefs, n)
                Toast.makeText(c, "Профиль $n сохранён", Toast.LENGTH_SHORT).show()
                renderContent()
            }, lp(WRAP, WRAP))
            if (has) {
                row.addView(smallBtn("Загрузить", true) {
                    Profiles.load(prefs, n)
                    Toast.makeText(c, "Профиль $n загружен", Toast.LENGTH_SHORT).show()
                    renderBody()
                }, lp(WRAP, WRAP).apply { leftMargin = ui.dp(6) })
            }
            box.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
        }
    }
}
