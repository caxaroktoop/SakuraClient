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
    private var fxParams: WindowManager.LayoutParams? = null
    private var panelBg: GradientDrawable? = null
    private var petalsView: PetalsView? = null
    private var player: PlayerView? = null
    private var shown = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        handler.post { onPrefChanged(key) }
    }

    private val ticker = object : Runnable {
        override fun run() {
            player?.refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun prefs(): SharedPreferences = getSharedPreferences("sakura_settings", MODE_PRIVATE)
    private fun scale() = prefs().getInt("scale", 100).coerceIn(80, 140) / 100f
    private fun opacity() = prefs().getInt("opacity", 92).coerceIn(35, 100)

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

        // нижний слой: затемнение, лепестки, прицел
        val f = FxView(this, prefs())
        fx = f
        fxParams = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false)
        wm.addView(f, fxParams)
        applyScreenFlags()

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
        if (key == null || key == "bright_on" || key == "awake_on") applyScreenFlags()
        if (key != null && key.startsWith("hud_")) hud?.apply()
    }

    /** Максимальная яркость и «не гасить экран» реализованы свойствами окна-слоя. */
    private fun applyScreenFlags() {
        val p = fxParams ?: return
        val v = fx ?: return
        p.screenBrightness = if (prefs().getBoolean("bright_on", false)) 1f
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        p.flags = if (prefs().getBoolean("awake_on", false)) p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        else p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
    }

    // ---------- окно мод-меню ----------

    private fun showPanel() {
        if (panel != null) return
        val ui = Ui(this, scale())
        val screen = screenSize()
        val w = minOf(screen.x - ui.dp(24), ui.dp(760))
        val wide = w >= ui.dp(520)
        val h = minOf(screen.y - ui.dp(24), if (wide) ui.dp(400) else ui.dp(580))

        val root = FrameLayout(this)
        val bg = ui.rounded(Gui.bg, 18f, Gui.border, 1.2f)
        bg.alpha = opacity() * 255 / 100
        panelBg = bg
        root.background = bg
        clipRounded(root, ui, 18f)

        val gui = ClickGuiView(
            GuiEnv(
                c = this, ui = ui, prefs = prefs(), wide = wide, showSearch = false, innerScroll = true,
                onClose = { hidePanel() },
                onEditHud = { prefs().edit().putBoolean("hud_edit", true).apply() },
                dragTitle = { v ->
                    v.setOnTouchListener(
                        DragListener(
                            this, wm, { panel }, { panelParams }, { screenSize() },
                            onClick = null,
                            onMoved = { x, y -> prefs().edit().putInt("gui_x", x).putInt("gui_y", y).apply() }
                        )
                    )
                },
                musicContent = { box -> buildPlayer(box, ui) },
                settingsContent = { box, _ -> buildSettings(box, ui) },
                moduleExtra = { key, box ->
                    if (key == "hud_ping") {
                        box.addView(
                            ui.text("Адрес сервера для пинга меняется в приложении: Модули → Пинг.", 11f, Gui.muted),
                            lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
                        )
                    }
                }
            )
        )
        root.addView(gui, FrameLayout.LayoutParams(MATCH, MATCH))

        // лепестки внутри окна (касания не перехватывают)
        val pv = PetalsView(this).apply { configure(10, 0.55f) }
        petalsView = pv
        root.addView(pv, FrameLayout.LayoutParams(MATCH, MATCH))
        pv.start()

        panel = root
        val p = panelParams ?: overlayParams(w, h, true)
        p.width = w
        p.height = h
        p.x = prefs().getInt("gui_x", (screen.x - w) / 2)
        p.y = prefs().getInt("gui_y", (screen.y - h) / 2)
        panelParams = p
        wm.addView(root, p)
        root.post { clamp(panel, panelParams) }
    }

    private fun removePanelView() {
        petalsView?.stop()
        petalsView = null
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        panelParams = null
        player = null
        panelBg = null
    }

    private fun hidePanel() {
        removePanelView()
        prefs().edit().putBoolean("panel_open", false).apply()
    }

    private fun clamp(view: View?, p: WindowManager.LayoutParams?) {
        if (view == null || p == null) return
        val s = screenSize()
        p.x = p.x.coerceIn(0, maxOf(0, s.x - view.width))
        p.y = p.y.coerceIn(0, maxOf(0, s.y - view.height))
        try { wm.updateViewLayout(view, p) } catch (_: Exception) {}
    }

    private fun buildPlayer(b: LinearLayout, ui: Ui) {
        val pv = PlayerView(this, ui, 56f) { openApp(Launcher.SPOTIFY, "Spotify") }
        player = pv
        b.addView(pv, lp(MATCH, WRAP))
        pv.refresh()
        b.addView(
            ui.pill("Открыть Minecraft", STYLE_GHOST, R.drawable.ic_cube) { openApp(Launcher.MINECRAFT, "Minecraft") },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(12) }
        )
    }

    private fun buildSettings(b: LinearLayout, ui: Ui) {
        addSliderRow(this, ui, b, "Прозрачность окна", 35, 100, opacity(), "%",
            onChange = { v ->
                prefs().edit().putInt("opacity", v).apply()
                panelBg?.alpha = v * 255 / 100
            }, onDone = {})
        addSliderRow(this, ui, b, "Размер окна", 80, 140, (scale() * 100).toInt(), "%",
            onChange = { v -> prefs().edit().putInt("scale", v).apply() },
            onDone = {
                removePanelView()
                prefs().edit().remove("gui_x").remove("gui_y").apply()
                showPanel()
            })
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
        if (SakuraAccessibility.instance != null) prefs().edit().putBoolean("zoom_on", false).apply()
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
