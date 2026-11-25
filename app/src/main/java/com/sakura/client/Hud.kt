package com.sakura.client

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Список виджетов HUD: ключ в настройках, подпись, значок и включён ли по умолчанию. */
object HudKeys {
    val keys = listOf("time", "timer", "batt", "ping", "cps", "ram", "net")
    val labels = listOf("Время", "Таймер", "Заряд", "Пинг", "CPS", "ОЗУ", "Сеть")
    val defaults = listOf(false, false, false, false, false, false, false)
    val icons = listOf(
        R.drawable.ic_clock, R.drawable.ic_timer, R.drawable.ic_battery, R.drawable.ic_wifi,
        R.drawable.ic_speed, R.drawable.ic_memory, R.drawable.ic_download
    )
}

/**
 * HUD в стиле игровых клиентов: отдельные карточки-виджеты (время, таймер, заряд, пинг, CPS, ОЗУ, сеть).
 * Режим редактирования: слой перехватывает касания, виджеты можно перетащить, позиции запоминаются.
 */
class HudWindow(
    private val ctx: Context,
    private val wm: WindowManager,
    private val prefs: SharedPreferences,
    private val onEditDone: () -> Unit,
    private val newParams: (Int, Int) -> WindowManager.LayoutParams
) {
    private class Widget(val key: String, val tv: TextView)

    private val handler = Handler(Looper.getMainLooper())
    private val ui = Ui(ctx)
    private val startedAt = SystemClock.elapsedRealtime()
    private var layer: FrameLayout? = null
    private var probe: View? = null
    private val widgets = ArrayList<Widget>()
    private val taps = ArrayDeque<Long>()
    private var lastRx = -1L
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var editing = false

    @Volatile private var pingMs = -1
    @Volatile private var pingRun = false
    private var pingThread: Thread? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    /** Пересобирает слой по текущим настройкам (или убирает, если модуль выключен). */
    fun apply() {
        remove()
        if (enabledIndexes().isNotEmpty() || editing) add()
    }

    fun setEditing(on: Boolean) {
        editing = on
        apply()
    }

    fun destroy() {
        editing = false
        remove()
    }

    private fun enabledIndexes(): List<Int> = HudKeys.keys.indices.filter {
        prefs.getBoolean("hud_" + HudKeys.keys[it], HudKeys.defaults[it])
    }

    private fun add() {
        val enabled = enabledIndexes()
        if (enabled.isEmpty() && !editing) return

        val root = FrameLayout(ctx)
        enabled.forEachIndexed { order, i -> addWidget(root, i, order) }
        if (editing) {
            root.setBackgroundColor(Color.argb(95, 0, 0, 0))
            addHint(root)
        }

        val p = newParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        if (editing) p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try {
            wm.addView(root, p)
        } catch (e: Exception) {
            widgets.clear()
            return
        }
        layer = root

        if (enabled.any { HudKeys.keys[it] == "cps" }) addProbe()
        if (enabled.any { HudKeys.keys[it] == "ping" }) startPing()
        lastRx = -1L
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun cardBg(ui: Ui) = ui.rounded(
        Color.argb(205, 22, 13, 30), 12f,
        if (editing) Palette.pink else Palette.stroke, if (editing) 1.5f else 1f
    )

    private fun addWidget(root: FrameLayout, idx: Int, order: Int) {
        val key = HudKeys.keys[idx]
        val wui = Ui(ctx, prefs.getInt("hudscale_$key", 100).coerceIn(70, 160) / 100f)
        val tv = wui.text("--", 12.5f, Palette.text, true)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(wui.dp(10), wui.dp(6), wui.dp(12), wui.dp(6))
            background = cardBg(wui)
        }
        card.addView(wui.icon(HudKeys.icons[idx], Palette.pink), lp(wui.dp(14), wui.dp(14)).apply { rightMargin = wui.dp(6) })
        card.addView(tv, lp(WRAP, WRAP))

        val flp = FrameLayout.LayoutParams(WRAP, WRAP).apply {
            leftMargin = prefs.getInt("w_${key}_x", ui.dp(12))
            topMargin = prefs.getInt("w_${key}_y", ui.dp(12) + order * ui.dp(36))
        }
        root.addView(card, flp)
        widgets.add(Widget(key, tv))

        card.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                if (!editing) return false
                val l = v.layoutParams as FrameLayout.LayoutParams
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = l.leftMargin; startY = l.topMargin
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        l.leftMargin = (startX + e.rawX - downX).toInt().coerceIn(0, maxOf(0, root.width - v.width))
                        l.topMargin = (startY + e.rawY - downY).toInt().coerceIn(0, maxOf(0, root.height - v.height))
                        v.layoutParams = l
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit().putInt("w_${key}_x", l.leftMargin).putInt("w_${key}_y", l.topMargin).apply()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun addHint(root: FrameLayout) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(16))
            background = ui.rounded(Color.argb(235, 26, 16, 32), 20f, Palette.stroke)
        }
        box.addView(ui.text("Режим редактирования", 14f, Palette.text, true), lp(WRAP, WRAP))
        box.addView(ui.text("Перетащи виджеты в нужное место", 12f, Palette.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(4) })
        box.addView(
            ui.pill("Готово", STYLE_PRIMARY) { onEditDone() },
            lp(WRAP, WRAP).apply { topMargin = ui.dp(12) }
        )
        root.addView(box, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = ui.dp(36)
        })
    }

    /**
     * Крошечное окно 1x1 пиксель, которое получает событие ACTION_OUTSIDE при любом касании экрана вне него.
     * Координат система не даёт, но факт касания виден: этого хватает для счётчика CPS (экспериментально).
     */
    private fun addProbe() {
        val v = View(ctx)
        v.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) taps.addLast(SystemClock.elapsedRealtime())
            false
        }
        val p = newParams(1, 1)
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        p.x = 0
        p.y = 0
        try {
            wm.addView(v, p)
            probe = v
        } catch (_: Exception) {
        }
    }

    private fun remove() {
        handler.removeCallbacks(ticker)
        layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        probe?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        layer = null
        probe = null
        widgets.clear()
        taps.clear()
        stopPing()
    }

    private fun tick() {
        for (w in widgets) {
            when (w.key) {
                "time" -> w.tv.text = timeFmt.format(Date())
                "timer" -> w.tv.text = elapsed()
                "batt" -> w.tv.text = battery()
                "cps" -> w.tv.text = "${cps()} CPS"
                "ping" -> {
                    val ms = pingMs
                    w.tv.text = if (ms < 0) "— мс" else "$ms мс"
                    w.tv.setTextColor(
                        when {
                            ms < 0 -> Palette.muted
                            ms < 60 -> Palette.ok
                            ms < 120 -> Palette.warn
                            else -> Palette.danger
                        }
                    )
                }
                "ram" -> w.tv.text = "ОЗУ ${ramUsed()}%"
                "net" -> w.tv.text = netSpeed()
            }
        }
    }

    private fun cps(): Int {
        val now = SystemClock.elapsedRealtime()
        while (taps.isNotEmpty() && now - taps.peekFirst()!! > 1000) taps.removeFirst()
        return taps.size
    }

    private fun elapsed(): String {
        val s = (SystemClock.elapsedRealtime() - startedAt) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    private fun battery(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "--"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
        if (level < 0) return "--"
        return "${level * 100 / scale}%  ${temp}°"
    }

    private fun ramUsed(): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.totalMem <= 0) return 0
        return (100 - mi.availMem * 100 / mi.totalMem).toInt()
    }

    private fun netSpeed(): String {
        val rx = TrafficStats.getTotalRxBytes()
        var out = "— КБ/с"
        if (rx >= 0 && lastRx >= 0) {
            val kb = (rx - lastRx) / 1024
            out = if (kb >= 1024) String.format(Locale.US, "%.1f МБ/с", kb / 1024f) else "$kb КБ/с"
        }
        lastRx = rx
        return out
    }

    /** Адрес для пинга из настроек: «хост» или «хост:порт». Пусто: Cloudflare 1.1.1.1. */
    private fun pingTarget(): Pair<String, Int?> {
        val raw = (prefs.getString("ping_host", "") ?: "").trim()
            .removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (raw.isEmpty()) return Pair("1.1.1.1", 443)
        val host = raw.substringBefore(':')
        val port = raw.substringAfter(':', "").toIntOrNull()
        return Pair(host, port)
    }

    private fun measure(host: String, port: Int): Int {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            Socket().use {
                it.connect(InetSocketAddress(host, port), 1500)
                (SystemClock.elapsedRealtime() - t0).toInt()
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun startPing() {
        if (pingRun) return
        pingRun = true
        pingThread = Thread {
            while (pingRun) {
                val (host, port) = pingTarget()
                var ms = if (port != null) measure(host, port) else measure(host, 443)
                if (ms < 0 && port == null) ms = measure(host, 80)
                pingMs = ms
                try {
                    Thread.sleep(3000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopPing() {
        pingRun = false
        pingThread?.interrupt()
        pingThread = null
    }
}
