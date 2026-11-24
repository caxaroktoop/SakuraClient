package com.sakura.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var crosshair: CrosshairView? = null
    private var bubble: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelBg: GradientDrawable? = null
    private var player: PlayerView? = null
    private var page = PAGE_PLAYER
    private var shown = false

    private val ticker = object : Runnable {
        override fun run() {
            player?.refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun prefs(): SharedPreferences = getSharedPreferences("sakura_settings", MODE_PRIVATE)
    private fun scale() = prefs().getInt("scale", 100).coerceIn(80, 140) / 100f
    private fun opacity() = prefs().getInt("opacity", 92).coerceIn(35, 100)
    private fun panelFill() = Color.argb(opacity() * 255 / 100, 26, 16, 32)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        showOverlay()
        return START_NOT_STICKY
    }

    // ---------- foreground-уведомление (чтобы система не убивала панель, пока идёт игра) ----------

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Панель Sakura", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), flags
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sakura Client")
            .setContentText("Панель работает поверх игры")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close), "Выключить", stop).build()
            )
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------- окна поверх экрана ----------

    @Suppress("DEPRECATION")
    private fun overlayParams(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            Point(m.widthPixels, m.heightPixels)
        }
    }

    private fun showOverlay() {
        if (shown) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Нет разрешения «Поверх других окон»", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        shown = true
        running = true
        val ui = Ui(this)

        val cv = CrosshairView(this, prefs())
        crosshair = cv
        wm.addView(cv, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false))

        val b = ImageView(this).apply {
            setImageResource(R.drawable.ic_sakura)
            val p = ui.dp(8)
            setPadding(p, p, p, p)
            background = ui.circle(Color.argb(235, 26, 16, 32)).apply { setStroke(ui.dp(2), Palette.pink) }
        }
        bubble = b
        bubbleParams = overlayParams(ui.dp(46), ui.dp(46), true).apply {
            x = prefs().getInt("bubble_x", ui.dp(12))
            y = prefs().getInt("bubble_y", ui.dp(90))
        }
        wm.addView(b, bubbleParams)
        b.setOnTouchListener(
            DragListener(
                this, wm, { bubble }, { bubbleParams }, { screenSize() },
                onClick = {
                    if (panel == null) {
                        prefs().edit().putBoolean("panel_open", true).apply()
                        showPanel()
                    } else hidePanel()
                },
                onMoved = { x, y -> prefs().edit().putInt("bubble_x", x).putInt("bubble_y", y).apply() }
            )
        )
        if (prefs().getBoolean("panel_open", true)) showPanel()
    }

    // ---------- панель ----------

    private fun showPanel() {
        if (panel != null) return
        val ui = Ui(this, scale())
        val v = buildPanel(ui)
        panel = v
        val p = panelParams ?: overlayParams(ui.dp(264), WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            x = prefs().getInt("panel_x", Ui(this@OverlayService).dp(70))
            y = prefs().getInt("panel_y", Ui(this@OverlayService).dp(40))
        }
        p.width = ui.dp(264)
        panelParams = p
        wm.addView(v, p)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        v.post { clamp(panel, panelParams) }
    }

    private fun removePanelView() {
        handler.removeCallbacks(ticker)
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        player = null
        panelBg = null
    }

    private fun hidePanel() {
        removePanelView()
        prefs().edit().putBoolean("panel_open", false).apply()
    }

    private fun rebuildPanel() {
        removePanelView()
        showPanel()
    }

    private fun clamp(view: View?, p: WindowManager.LayoutParams?) {
        if (view == null || p == null) return
        val s = screenSize()
        p.x = p.x.coerceIn(0, maxOf(0, s.x - view.width))
        p.y = p.y.coerceIn(0, maxOf(0, s.y - view.height))
        try { wm.updateViewLayout(view, p) } catch (_: Exception) {}
    }

    private fun buildPanel(ui: Ui): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12))
        }
        val bg = ui.rounded(panelFill(), 20f, Palette.stroke)
        panelBg = bg
        root.background = bg

        root.addView(buildHeader(ui), lp(MATCH, WRAP))
        if (page == PAGE_PLAYER) buildPlayerPage(root, ui) else buildSettingsPage(root, ui)
        return root
    }

    private fun buildHeader(ui: Ui): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        h.addView(ui.icon(R.drawable.ic_sakura), lp(ui.dp(22), ui.dp(22)).apply { rightMargin = ui.dp(8) })
        h.addView(ui.text("Sakura", 15f, Palette.pink, true), lp(0, WRAP, 1f))
        val settingsActive = page == PAGE_SETTINGS
        h.addView(
            ui.iconButton(R.drawable.ic_tune, 34f, 18f, if (settingsActive) Palette.pink else Palette.muted) {
                page = if (page == PAGE_PLAYER) PAGE_SETTINGS else PAGE_PLAYER
                rebuildPanel()
            }, lp(ui.dp(34), ui.dp(34))
        )
        h.addView(
            ui.iconButton(R.drawable.ic_close, 34f, 16f, Palette.muted) { hidePanel() },
            lp(ui.dp(34), ui.dp(34))
        )
        h.setOnTouchListener(
            DragListener(
                this, wm, { panel }, { panelParams }, { screenSize() },
                onClick = null,
                onMoved = { x, y -> prefs().edit().putInt("panel_x", x).putInt("panel_y", y).apply() }
            )
        )
        return h
    }

    private fun buildPlayerPage(root: LinearLayout, ui: Ui) {
        val pv = PlayerView(this, ui, 52f) { openApp(Launcher.SPOTIFY, "Spotify") }
        player = pv
        root.addView(pv, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
        pv.refresh()

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            ui.pill("Minecraft", STYLE_GHOST, R.drawable.ic_cube) { openApp(Launcher.MINECRAFT, "Minecraft") },
            lp(0, WRAP, 1f)
        )
        root.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
    }

    private fun openApp(pkg: String, name: String) {
        if (!Launcher.open(this, pkg)) {
            Toast.makeText(this, "$name не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sliderRow(
        parent: LinearLayout, ui: Ui, label: String, min: Int, max: Int, cur: Int, unit: String,
        onChange: (Int) -> Unit, onDone: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(ui.text(label, 12f, Palette.muted), lp(0, WRAP, 1f))
        val value = ui.text("$cur$unit", 12f, Palette.text, true)
        row.addView(value, lp(WRAP, WRAP))
        parent.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })
        parent.addView(
            SliderView(this, ui, min, max, cur, { v -> value.text = "$v$unit"; onChange(v) }, onDone),
            lp(MATCH, WRAP)
        )
    }

    private fun buildSettingsPage(root: LinearLayout, ui: Ui) {
        sliderRow(root, ui, "Прозрачность панели", 35, 100, opacity(), "%",
            onChange = { v ->
                prefs().edit().putInt("opacity", v).apply()
                panelBg?.setColor(panelFill())
            }, onDone = {})
        sliderRow(root, ui, "Размер панели", 80, 140, (scale() * 100).toInt(), "%",
            onChange = { v -> prefs().edit().putInt("scale", v).apply() },
            onDone = { rebuildPanel() })

        root.addView(View(this).apply { setBackgroundColor(Palette.stroke) },
            lp(MATCH, maxOf(1, ui.dp(1))).apply { topMargin = ui.dp(10); bottomMargin = ui.dp(2) })

        val crossRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        crossRow.addView(ui.text("Свой прицел", 13f, Palette.text, true), lp(0, WRAP, 1f))
        crossRow.addView(
            ToggleView(this, ui, prefs().getBoolean("cross_on", false)) { on ->
                prefs().edit().putBoolean("cross_on", on).apply()
                crosshair?.reload()
            }, lp(WRAP, WRAP)
        )
        root.addView(crossRow, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })

        sliderRow(root, ui, "Размер прицела", 8, 48, prefs().getInt("cross_size", 20), "",
            onChange = { v ->
                prefs().edit().putInt("cross_size", v).apply()
                crosshair?.reload()
            }, onDone = {})

        val sel = prefs().getInt("cross_color", 0)
        val colors = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        CrosshairView.COLORS.forEachIndexed { i, col ->
            val dot = View(this).apply {
                background = ui.circle(col).apply { if (i == sel) setStroke(ui.dp(2), Color.WHITE) }
                setOnClickListener {
                    prefs().edit().putInt("cross_color", i).apply()
                    crosshair?.reload()
                    rebuildPanel()
                }
            }
            colors.addView(dot, lp(ui.dp(26), ui.dp(26)).apply { rightMargin = ui.dp(10) })
        }
        root.addView(colors, lp(MATCH, WRAP).apply { topMargin = ui.dp(6) })

        root.addView(
            ui.pill("Выключить Sakura", STYLE_DANGER, R.drawable.ic_close) { stopSelf() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(14) }
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        panel?.post { clamp(panel, panelParams) }
        bubble?.post { clamp(bubble, bubbleParams) }
    }

    override fun onDestroy() {
        removePanelView()
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { crosshair?.let { wm.removeView(it) } } catch (_: Exception) {}
        bubble = null
        crosshair = null
        shown = false
        running = false
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.sakura.client.START"
        const val ACTION_STOP = "com.sakura.client.STOP"
        private const val CHANNEL = "sakura_overlay"
        private const val NOTIF_ID = 1301
        private const val PAGE_PLAYER = 0
        private const val PAGE_SETTINGS = 1

        @Volatile
        var running = false
    }
}

/** Перетаскивание окна за любое место, кроме кнопок. Короткий тап без движения = onClick. */
class DragListener(
    ctx: Context,
    private val wm: WindowManager,
    private val target: () -> View?,
    private val params: () -> WindowManager.LayoutParams?,
    private val screen: () -> Point,
    private val onClick: (() -> Unit)?,
    private val onMoved: (Int, Int) -> Unit
) : View.OnTouchListener {
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        val p = params() ?: return false
        val t = target() ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                startX = p.x; startY = p.y
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downX).toInt()
                val dy = (e.rawY - downY).toInt()
                if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                if (moved) {
                    val s = screen()
                    p.x = (startX + dx).coerceIn(0, maxOf(0, s.x - t.width))
                    p.y = (startY + dy).coerceIn(0, maxOf(0, s.y - t.height))
                    try { wm.updateViewLayout(t, p) } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moved) onMoved(p.x, p.y) else onClick?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }
}

/** Прицел по центру экрана. Тёмная обводка делает его заметным на любом фоне. */
class CrosshairView(ctx: Context, private val prefs: SharedPreferences) : View(ctx) {
    private var on = false
    private var armDp = 20
    private var colorIdx = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init { reload() }

    fun reload() {
        on = prefs.getBoolean("cross_on", false)
        armDp = prefs.getInt("cross_size", 20)
        colorIdx = prefs.getInt("cross_color", 0).coerceIn(0, COLORS.size - 1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!on) return
        val d = resources.displayMetrics.density
        val x = width / 2f
        val y = height / 2f
        val gap = 4f * d
        val arm = armDp * d
        val w = 2.5f * d
        for (pass in 0..1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (pass == 0) w + 2f * d else w
            paint.color = if (pass == 0) Color.argb(150, 0, 0, 0) else COLORS[colorIdx]
            canvas.drawLine(x - arm, y, x - gap, y, paint)
            canvas.drawLine(x + gap, y, x + arm, y, paint)
            canvas.drawLine(x, y - arm, x, y - gap, paint)
            canvas.drawLine(x, y + gap, x, y + arm, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = COLORS[colorIdx]
        canvas.drawCircle(x, y, 1.8f * d, paint)
    }

    companion object {
        val COLORS = intArrayOf(
            Color.rgb(255, 105, 180), Color.WHITE, Color.rgb(120, 220, 255),
            Color.rgb(180, 255, 150), Color.rgb(255, 224, 102), Color.rgb(255, 90, 90)
        )
    }
}
