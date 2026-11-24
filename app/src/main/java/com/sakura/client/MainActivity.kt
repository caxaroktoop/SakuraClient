package com.sakura.client

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: PlayerView
    private lateinit var overlayStatus: TextView
    private lateinit var listenerStatus: TextView
    private var notifStatus: TextView? = null
    private lateinit var mcStatus: TextView
    private lateinit var panelBtn: LinearLayout

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.statusBarColor = Palette.bg
        window.navigationBarColor = Palette.bg
        setContentView(build())
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------------- построение экрана ----------------

    @Suppress("DEPRECATION")
    private fun build(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.bg)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(28))
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            content.setPadding(
                ui.dp(18), insets.systemWindowInsetTop + ui.dp(18),
                ui.dp(18), insets.systemWindowInsetBottom + ui.dp(28)
            )
            insets
        }

        content.addView(header(), lp(MATCH, WRAP))
        content.addView(hero(), lp(MATCH, WRAP).apply { topMargin = ui.dp(22) })

        content.addView(sectionTitle("SPOTIFY"), lp(MATCH, WRAP).apply { topMargin = ui.dp(24) })
        val spotifyCard = card()
        player = PlayerView(this, ui, 64f) { openApp(Launcher.SPOTIFY, "Spotify") }
        spotifyCard.addView(player, lp(MATCH, WRAP))
        content.addView(spotifyCard, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })

        content.addView(sectionTitle("НАСТРОЙКА"), lp(MATCH, WRAP).apply { topMargin = ui.dp(24) })
        val setup = card()
        overlayStatus = statusPill()
        setup.addView(
            settingRow(R.drawable.ic_layers, "Поверх других окон",
                "Нужно, чтобы панель и прицел были видны в игре", overlayStatus) { openOverlaySettings() },
            lp(MATCH, WRAP)
        )
        setup.addView(divider(), lp(MATCH, maxOf(1, ui.dp(1))))
        listenerStatus = statusPill()
        setup.addView(
            settingRow(R.drawable.ic_music, "Доступ к уведомлениям",
                "Нужен, чтобы видеть название трека и обложку Spotify", listenerStatus) { openListenerSettings() },
            lp(MATCH, WRAP)
        )
        if (Build.VERSION.SDK_INT >= 33) {
            setup.addView(divider(), lp(MATCH, maxOf(1, ui.dp(1))))
            val np = statusPill()
            notifStatus = np
            setup.addView(
                settingRow(R.drawable.ic_bell, "Уведомления",
                    "Показывают, что панель работает, и дают кнопку «Выключить»", np) { askNotifications() },
                lp(MATCH, WRAP)
            )
        }
        content.addView(setup, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })

        content.addView(sectionTitle("ПАНЕЛЬ"), lp(MATCH, WRAP).apply { topMargin = ui.dp(24) })
        panelBtn = ui.pill("Включить панель без игры", STYLE_GHOST, R.drawable.ic_sakura) { togglePanel() }
        content.addView(panelBtn, lp(MATCH, WRAP).apply { topMargin = ui.dp(10) })

        val foot = ui.text(
            "Sakura Client v1.4  •  Zoom, Freelook и Replay — в разработке", 11f, Palette.muted
        ).apply { gravity = Gravity.CENTER }
        content.addView(foot, lp(MATCH, WRAP).apply { topMargin = ui.dp(26) })
        return scroll
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ui.icon(R.drawable.ic_sakura).apply {
            val p = ui.dp(9)
            setPadding(p, p, p, p)
            background = ui.gradient(Color.parseColor("#4A1646"), Color.parseColor("#1A0C22"), 16f)
        }
        row.addView(logo, lp(ui.dp(52), ui.dp(52)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ui.text("Sakura Client", 22f, Palette.text, true), lp(WRAP, WRAP))
        col.addView(ui.text("Помощник для Minecraft Bedrock", 12f, Palette.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(4) })
        row.addView(col, lp(0, WRAP, 1f).apply { leftMargin = ui.dp(14) })
        return row
    }

    private fun hero(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(18))
            background = ui.gradient(Color.parseColor("#E8528C"), Color.parseColor("#7C3AB0"), 26f)
        }
        box.addView(ui.text("Готов играть?", 22f, Color.WHITE, true), lp(WRAP, WRAP))
        box.addView(
            ui.text("Одна кнопка включает панель Sakura и открывает Minecraft Bedrock.", 13f,
                Color.argb(225, 255, 255, 255)).apply { setLineSpacing(0f, 1.15f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(8) }
        )
        box.addView(
            ui.pill("Играть в Minecraft", STYLE_WHITE, R.drawable.ic_cube) { playMinecraft() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(18) }
        )
        mcStatus = ui.text("", 12f, Color.argb(235, 255, 255, 255)).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                if (!Launcher.isInstalled(this@MainActivity, Launcher.MINECRAFT)) Launcher.openStore(this@MainActivity, Launcher.MINECRAFT)
            }
        }
        box.addView(mcStatus, lp(MATCH, WRAP).apply { topMargin = ui.dp(12) })
        return box
    }

    private fun sectionTitle(t: String): TextView = ui.text(t, 11f, Palette.pink, true).apply {
        letterSpacing = 0.12f
        setPadding(ui.dp(4), 0, 0, 0)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        background = ui.rounded(Palette.surface, 22f, Palette.stroke)
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(Palette.stroke) }

    private fun statusPill(): TextView = ui.text("", 11f, Palette.ok, true).apply {
        setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5))
    }

    private fun setStatus(tv: TextView, ok: Boolean, on: String, off: String) {
        tv.text = if (ok) on else off
        tv.setTextColor(if (ok) Palette.ok else Palette.warn)
        val base = if (ok) Palette.ok else Palette.warn
        tv.background = ui.rounded(Color.argb(38, Color.red(base), Color.green(base), Color.blue(base)), 12f)
    }

    private fun settingRow(icon: Int, title: String, sub: String, pill: TextView, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(12), 0, ui.dp(12))
            isClickable = true
            setOnClickListener { onClick() }
            val ic = ui.icon(icon, Palette.pink).apply {
                val p = ui.dp(9)
                setPadding(p, p, p, p)
                background = ui.circle(Palette.surface2)
            }
            addView(ic, lp(ui.dp(40), ui.dp(40)))
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(ui.text(title, 14f, Palette.text, true), lp(MATCH, WRAP))
            col.addView(ui.text(sub, 11.5f, Palette.muted), lp(MATCH, WRAP).apply { topMargin = ui.dp(3) })
            addView(col, lp(0, WRAP, 1f).apply { leftMargin = ui.dp(12); rightMargin = ui.dp(10) })
            addView(pill, lp(WRAP, WRAP))
        }

    // ---------------- обновление состояния ----------------

    private fun refresh() {
        setStatus(overlayStatus, Settings.canDrawOverlays(this), "Вкл", "Выкл")
        setStatus(listenerStatus, SpotifyBridge.listenerEnabled(this), "Вкл", "Выкл")
        notifStatus?.let {
            val granted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            setStatus(it, granted, "Вкл", "Выкл")
        }
        mcStatus.text = if (Launcher.isInstalled(this, Launcher.MINECRAFT)) "Minecraft Bedrock найден ✓"
        else "Minecraft Bedrock не найден — нажми, чтобы открыть Google Play"
        ui.pillLabel(panelBtn).text = if (OverlayService.running) "Выключить панель" else "Включить панель без игры"
        player.refresh()
    }

    // ---------------- действия ----------------

    private fun playMinecraft() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Сначала разреши «Поверх других окон», потом снова нажми «Играть»")
            openOverlaySettings()
            return
        }
        askNotificationsQuietly()
        startOverlay()
        if (!Launcher.open(this, Launcher.MINECRAFT)) {
            toast("Minecraft Bedrock не найден. Открываю Google Play")
            Launcher.openStore(this, Launcher.MINECRAFT)
        }
    }

    private fun togglePanel() {
        if (OverlayService.running) {
            stopService(Intent(this, OverlayService::class.java))
            toast("Панель выключена")
        } else {
            if (!Settings.canDrawOverlays(this)) {
                toast("Сначала разреши «Поверх других окон»")
                openOverlaySettings()
                return
            }
            askNotificationsQuietly()
            startOverlay()
            toast("Панель включена")
        }
        handler.postDelayed({ refresh() }, 300)
    }

    private fun startOverlay() {
        val i = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun openApp(pkg: String, name: String) {
        if (!Launcher.open(this, pkg)) {
            toast("$name не найден. Открываю Google Play")
            Launcher.openStore(this, pkg)
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun openListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            toast("Открой: Настройки → Уведомления → Доступ к уведомлениям")
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        } else toast("Уведомления уже разрешены")
    }

    private fun askNotificationsQuietly() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
