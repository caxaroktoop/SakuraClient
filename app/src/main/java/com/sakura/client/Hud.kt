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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Капсула с игровой информацией сверху экрана: время, таймер, батарея, пинг, ОЗУ, сеть. */
class HudWindow(
    private val ctx: Context,
    private val wm: WindowManager,
    private val prefs: SharedPreferences,
    private val newParams: (Int, Int) -> WindowManager.LayoutParams
) {
    private class Chip(val key: String, val tv: TextView)

    private val handler = Handler(Looper.getMainLooper())
    private val ui = Ui(ctx)
    private val startedAt = SystemClock.elapsedRealtime()
    private var root: FrameLayout? = null
    private val chips = ArrayList<Chip>()
    private var lastRx = -1L
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    @Volatile private var pingMs = -1
    @Volatile private var pingRun = false
    private var pingThread: Thread? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    /** Пересобирает капсулу по текущим настройкам (или убирает, если модуль выключен). */
    fun apply() {
        remove()
        if (!prefs.getBoolean("hud_on", false)) return
        add()
    }

    fun destroy() = remove()

    private fun add() {
        val defs = listOf(
            Triple("time", R.drawable.ic_clock, true),
            Triple("timer", R.drawable.ic_timer, true),
            Triple("batt", R.drawable.ic_battery, true),
            Triple("ping", R.drawable.ic_wifi, true),
            Triple("ram", R.drawable.ic_memory, false),
            Triple("net", R.drawable.ic_download, false)
        )
        val capsule = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7))
            background = ui.rounded(Color.argb(215, 22, 13, 30), 18f, Palette.stroke)
        }
        for ((key, icon, dflt) in defs) {
            if (!prefs.getBoolean("hud_$key", dflt)) continue
            val tv = ui.text("--", 12f, Palette.text, true)
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            item.addView(ui.icon(icon, Palette.pink), lp(ui.dp(13), ui.dp(13)).apply { rightMargin = ui.dp(5) })
            item.addView(tv, lp(WRAP, WRAP))
            capsule.addView(item, lp(WRAP, WRAP).apply { if (capsule.childCount > 0) leftMargin = ui.dp(12) })
            chips.add(Chip(key, tv))
        }
        if (chips.isEmpty()) return

        val frame = FrameLayout(ctx).apply { setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8)) }
        frame.addView(capsule, FrameLayout.LayoutParams(WRAP, WRAP))
        val p = newParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or when (prefs.getInt("hud_pos", 1)) {
                0 -> Gravity.START
                2 -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            x = 0
            y = 0
        }
        try {
            wm.addView(frame, p)
        } catch (e: Exception) {
            chips.clear()
            return
        }
        root = frame
        lastRx = -1L
        if (chips.any { it.key == "ping" }) startPing()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun remove() {
        handler.removeCallbacks(ticker)
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        chips.clear()
        stopPing()
    }

    private fun tick() {
        for (c in chips) {
            when (c.key) {
                "time" -> c.tv.text = timeFmt.format(Date())
                "timer" -> c.tv.text = elapsed()
                "batt" -> c.tv.text = battery()
                "ping" -> {
                    val ms = pingMs
                    c.tv.text = if (ms < 0) "— мс" else "$ms мс"
                    c.tv.setTextColor(
                        when {
                            ms < 0 -> Palette.muted
                            ms < 60 -> Palette.ok
                            ms < 120 -> Palette.warn
                            else -> Palette.danger
                        }
                    )
                }
                "ram" -> c.tv.text = "ОЗУ ${ramUsed()}%"
                "net" -> c.tv.text = netSpeed()
            }
        }
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

    private fun startPing() {
        if (pingRun) return
        pingRun = true
        pingThread = Thread {
            while (pingRun) {
                val t0 = SystemClock.elapsedRealtime()
                var ms = -1
                try {
                    Socket().use {
                        it.connect(InetSocketAddress("1.1.1.1", 443), 1500)
                        ms = (SystemClock.elapsedRealtime() - t0).toInt()
                    }
                } catch (_: Exception) {
                }
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
