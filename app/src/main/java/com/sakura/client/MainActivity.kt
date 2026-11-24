package com.sakura.client

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 12, 18)
    private val panel = Color.rgb(34, 25, 36)
    private val pink = Color.rgb(255, 166, 202)
    private val white = Color.rgb(248, 242, 247)
    private val muted = Color.rgb(190, 174, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildDashboard()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
        }

        val brand = TextView(this).apply {
            text = "🌸 SAKURA CLIENT"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pink)
            gravity = Gravity.CENTER
        }
        content.addView(brand)

        val version = TextView(this).apply {
            text = "BEDROCK COMPANION  •  VERSION 1.3"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(22))
        }
        content.addView(version)

        section(content, "БЫСТРЫЙ ЗАПУСК")
        card(content).also { c ->
            c.addView(actionButton("🌸  Запустить плавающую панель", pink) {
                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
                    toast("Панель Sakura запущена")
                } else {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            })
            c.addView(actionButton("⛏  Открыть Minecraft Bedrock", white) {
                val launch = packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
                if (launch != null) startActivity(launch) else toast("Minecraft не найден")
            })
            c.addView(actionButton("♫  Открыть Spotify", white) {
                val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (launch != null) startActivity(launch) else toast("Spotify не найден")
            })
        }

        section(content, "РАЗРЕШЕНИЯ")
        card(content).also { c ->
            c.addView(infoText("Наложение нужно для плавающей кнопки и прицела."))
            c.addView(actionButton("Разрешить отображение поверх окон", white) {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else toast("Разрешение уже включено")
            })
            c.addView(infoText("Доступ к уведомлениям нужен для управления медиасессией Spotify."))
            c.addView(actionButton("Настроить доступ к уведомлениям", white) {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            })
        }

        section(content, "МОДУЛИ")
        card(content).also { c ->
            moduleRow(c, "🎯  Custom Crosshair", "Доступен в плавающей панели", true)
            moduleRow(c, "🎵  Spotify Controls", "Play/Pause и переключение треков", true)
            moduleRow(c, "🔎  Zoom камеры", "Пока не реализован", false)
            moduleRow(c, "👁  Freelook", "Пока не реализован", false)
            moduleRow(c, "🎬  Replay", "Пока не реализован", false)
            moduleRow(c, "✏️  HUD Editor", "Пока не реализован", false)
        }

        section(content, "О ПРОЕКТЕ")
        card(content).also { c ->
            c.addView(infoText("Sakura Client — companion-приложение для Android. Оно показывает системную панель поверх других приложений, но не внедряется в Minecraft и не меняет игровой код."))
            c.addView(actionButton("Остановить плавающую панель", white) {
                stopService(Intent(this, OverlayService::class.java))
                toast("Панель остановлена")
            })
        }

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun section(parent: LinearLayout, label: String) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
            setTextColor(pink)
            setPadding(dp(3), dp(16), 0, dp(8))
        })
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(4)
        parent.addView(box, params)
        return box
    }

    private fun infoText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(muted)
        setPadding(dp(4), dp(7), dp(4), dp(7))
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(color)
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun moduleRow(parent: LinearLayout, title: String, detail: String, available: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (available) white else muted)
        })
        row.addView(TextView(this).apply {
            text = detail
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
        parent.addView(row)
        val line = View(this).apply { setBackgroundColor(Color.rgb(67, 48, 67)) }
        parent.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
