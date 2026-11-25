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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var fx: FxView? = null
    private var hud: HudWindow? = null
    private var bubble: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelUi: Ui? = null
    private var glass: GlassDrawable? = null
    private var body: LinearLayout? = null
    private var subtitle: TextView? = null
    private var player: PlayerView? = null
    private var page = PAGE_PLAYER
    private var shown = false
    private var startedAt = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        handler.post { onPrefChanged(key) }
    }

    private val ticker = object : Runnable {
        override fun run() {
            player?.refresh()
            subtitle?.text = "В игре " + elapsed()
            handler.postDelayed(this, 1000)
        }
    }

    private fun prefs(): SharedPreferences = getSharedPreferences("sakura_settings", MODE_PRIVATE)
    private fun scale() = prefs().getInt("scale", 100).coerceIn(80, 140) / 100f
    private fun opacity() = prefs().getInt("opacity", 92).coerceIn(35, 100)

    private fun elapsed(): String {
        val s = (SystemClock.elapsedRealtime() - startedAt) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

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
        startedAt = SystemClock.elapsedRealtime()
        val ui = Ui(this)

        // нижний слой: затемнение, лепестки, прицел
        val f = FxView(this, prefs())
        fx = f
        wm.addView(f, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false))

        // HUD сверху экрана
        prefs().edit().putBoolean("hud_edit", false).apply()
        hud = HudWindow(
            this, wm, prefs(),
            onEditDone = { prefs().edit().putBoolean("hud_edit", false).apply() }
        ) { w, h -> overlayParams(w, h, false) }.also { it.apply() }

        // плавающая кнопка
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

        prefs().registerOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (prefs().getBoolean("panel_open", true)) showPanel()
    }

    private fun onPrefChanged(key: String?) {
        if (!shown) return
        fx?.reload()
        if (key == "hud_edit") {
            val edit = prefs().getBoolean("hud_edit", false)
            if (edit) removePanelView() else if (prefs().getBoolean("panel_open", true)) showPanel()
            hud?.setEditing(edit)
            return
        }
        if (key != null && key.startsWith("hud_")) hud?.apply()
    }

    // ---------- панель ----------

    private fun showPanel() {
        if (panel != null) return
        val ui = Ui(this, scale())
        panelUi = ui

        val root = FrameLayout(this)
        val g = GlassDrawable(ui, opacity())
        glass = g
        root.background = g
        root.clipToOutline = true
        root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ui.fdp(24f))
            }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14))
        }
        root.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        col.addView(buildHeader(ui), lp(MATCH, WRAP))
        col.addView(buildTabs(ui), lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })

        val maxBody = maxOf(ui.dp(120), screenSize().y - ui.dp(170))
        val scroll = MaxHeightScrollView(this, maxBody).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(b, FrameLayout.LayoutParams(MATCH, WRAP))
        body = b
        col.addView(scroll, lp(MATCH, WRAP).apply { topMargin = ui.dp(12) })

        panel = root
        fillBody()

        val p = panelParams ?: overlayParams(ui.dp(290), WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            x = prefs().getInt("panel_x", Ui(this@OverlayService).dp(70))
            y = prefs().getInt("panel_y", Ui(this@OverlayService).dp(40))
        }
        p.width = ui.dp(290)
        p.height = WindowManager.LayoutParams.WRAP_CONTENT
        panelParams = p
        wm.addView(root, p)
        root.post { clamp(panel, panelParams) }
    }

    private fun removePanelView() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        player = null
        body = null
        subtitle = null
        glass = null
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

    private fun buildHeader(ui: Ui): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        h.addView(ui.icon(R.drawable.ic_sakura), lp(ui.dp(30), ui.dp(30)).apply { rightMargin = ui.dp(10) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(ui.text("Sakura", 16f, Palette.text, true), lp(WRAP, WRAP))
        val sub = ui.text("В игре " + elapsed(), 11f, Palette.pink)
        subtitle = sub
        titles.addView(sub, lp(WRAP, WRAP).apply { topMargin = ui.dp(3) })
        h.addView(titles, lp(0, WRAP, 1f))
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

    private fun buildTabs(ui: Ui): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
            background = ui.rounded(Color.argb(110, 0, 0, 0), 14f)
        }
        val tabs = listOf(
            Triple(PAGE_PLAYER, R.drawable.ic_music, "Плеер"),
            Triple(PAGE_MODULES, R.drawable.ic_grid, "Модули"),
            Triple(PAGE_VIEW, R.drawable.ic_tune, "Вид")
        )
        for ((id, ic, label) in tabs) {
            val sel = id == page
            val fg = if (sel) Palette.onPink else Palette.muted
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(8), 0, ui.dp(8))
                if (sel) background = ui.gradient(Palette.pink, Palette.pinkDeep, 11f, GradientDrawable.Orientation.LEFT_RIGHT)
                isClickable = true
                setOnClickListener {
                    if (page != id) {
                        page = id
                        rebuildPanel()
                    }
                }
            }
            tab.addView(ui.icon(ic, fg), lp(ui.dp(14), ui.dp(14)).apply { rightMargin = ui.dp(5) })
            tab.addView(ui.text(label, 12f, fg, true))
            bar.addView(tab, lp(0, WRAP, 1f))
        }
        return bar
    }

    private fun fillBody() {
        val b = body ?: return
        val ui = panelUi ?: return
        b.removeAllViews()
        player = null
        when (page) {
            PAGE_PLAYER -> buildPlayerPage(b, ui)
            PAGE_MODULES -> buildModulesPage(b, ui)
            else -> buildViewPage(b, ui)
        }
        panel?.post { clamp(panel, panelParams) }
    }

    private fun buildPlayerPage(b: LinearLayout, ui: Ui) {
        val pv = PlayerView(this, ui, 56f) { openApp(Launcher.SPOTIFY, "Spotify") }
        player = pv
        b.addView(pv, lp(MATCH, WRAP))
        pv.refresh()
        b.addView(
            ui.pill("Открыть Minecraft", STYLE_GHOST, R.drawable.ic_cube) { openApp(Launcher.MINECRAFT, "Minecraft") },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(12) }
        )
    }

    private fun buildModulesPage(b: LinearLayout, ui: Ui) {
        addModulesWindow(this, ui, prefs(), b,
            onEditHud = { prefs().edit().putBoolean("hud_edit", true).apply() },
            extra = { key, box ->
                if (key == "ping") {
                    box.addView(
                        ui.text("Адрес сервера для пинга меняется в приложении: Модули, Пинг.", 11f, Palette.muted),
                        lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
                    )
                }
            }
        ) { fillBody() }
    }

    private fun buildViewPage(b: LinearLayout, ui: Ui) {
        addSliderRow(this, ui, b, "Прозрачность панели", 35, 100, opacity(), "%",
            onChange = { v ->
                prefs().edit().putInt("opacity", v).apply()
                glass?.setPanelOpacity(v)
            }, onDone = {})
        addSliderRow(this, ui, b, "Размер панели", 80, 140, (scale() * 100).toInt(), "%",
            onChange = { v -> prefs().edit().putInt("scale", v).apply() },
            onDone = { rebuildPanel() })
        b.addView(
            ui.pill("Выключить Sakura", STYLE_DANGER, R.drawable.ic_close) { stopSelf() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(16) }
        )
    }

    private fun openApp(pkg: String, name: String) {
        if (!Launcher.open(this, pkg)) {
            Toast.makeText(this, "$name не найден", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        panel?.post { clamp(panel, panelParams) }
        bubble?.post { clamp(bubble, bubbleParams) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try { prefs().unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        removePanelView()
        hud?.destroy()
        hud = null
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { fx?.let { wm.removeView(it) } } catch (_: Exception) {}
        bubble = null
        fx = null
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
        private const val PAGE_MODULES = 1
        private const val PAGE_VIEW = 2

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
