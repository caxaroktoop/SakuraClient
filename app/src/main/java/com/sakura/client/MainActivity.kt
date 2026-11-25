package com.sakura.client

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences by lazy { getSharedPreferences("sakura_settings", MODE_PRIVATE) }

    private lateinit var player: PlayerView
    private lateinit var overlayStatus: TextView
    private lateinit var listenerStatus: TextView
    private var notifStatus: TextView? = null
    private lateinit var a11yStatus: TextView
    private lateinit var mcStatus: TextView
    private lateinit var panelBtn: LinearLayout
    private lateinit var petalsBg: PetalsView
    private lateinit var liveBg: LiveBackground
    private lateinit var modulesBox: LinearLayout
    private lateinit var libBox: LinearLayout
    private lateinit var pages: List<View>
    private lateinit var navItems: List<LinearLayout>
    private var tab = 0
    private var libCat = 0
    private var pendingCat = 0

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
        petalsBg.start()
        liveBg.start()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        petalsBg.stop()
        liveBg.stop()
        super.onPause()
    }

    // ---------------- каркас: шапка, страницы, нижняя навигация ----------------

    @Suppress("DEPRECATION")
    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header(), lp(MATCH, WRAP))

        val frame = FrameLayout(this)
        pages = listOf(playPage(), modulesPage(), libraryPage())
        pages.forEach { frame.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }
        root.addView(frame, lp(MATCH, 0, 1f))
        root.addView(bottomNav(), lp(MATCH, WRAP))
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }

        petalsBg = PetalsView(this).apply { configure(26, 1f) }
        liveBg = LiveBackground(this)
        val outer = FrameLayout(this).apply { setBackgroundColor(Palette.bg) }
        outer.addView(liveBg, FrameLayout.LayoutParams(MATCH, MATCH))
        outer.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        outer.addView(petalsBg, FrameLayout.LayoutParams(MATCH, MATCH))
        selectTab(0)
        return outer
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(6))
        }
        val logo = ui.icon(R.drawable.ic_sakura).apply {
            val p = ui.dp(7)
            setPadding(p, p, p, p)
            background = ui.gradient(Color.parseColor("#4A1646"), Color.parseColor("#1A0C22"), 14f)
        }
        row.addView(logo, lp(ui.dp(42), ui.dp(42)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ui.text("Sakura Client", 19f, Palette.text, true), lp(WRAP, WRAP))
        col.addView(ui.text("Помощник для Minecraft Bedrock", 11.5f, Palette.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(3) })
        row.addView(col, lp(0, WRAP, 1f).apply { leftMargin = ui.dp(12) })
        return row
    }

    private fun bottomNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
            background = ui.rounded(Palette.surface, 0f, Palette.stroke)
        }
        val defs = listOf(
            Pair(R.drawable.ic_cube, "Играть"),
            Pair(R.drawable.ic_grid, "Модули"),
            Pair(R.drawable.ic_layers, "Библиотека")
        )
        val items = ArrayList<LinearLayout>()
        defs.forEachIndexed { i, (icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(8), 0, ui.dp(8))
                isClickable = true
                setOnClickListener { selectTab(i) }
            }
            item.addView(ui.icon(icon, Palette.muted), lp(ui.dp(22), ui.dp(22)))
            item.addView(ui.text(label, 11f, Palette.muted, true), lp(WRAP, WRAP).apply { topMargin = ui.dp(4) })
            bar.addView(item, lp(0, WRAP, 1f))
            items.add(item)
        }
        navItems = items
        return bar
    }

    private fun selectTab(i: Int) {
        tab = i
        pages.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
        navItems.forEachIndexed { idx, item ->
            val sel = idx == i
            val col = if (sel) Palette.pink else Palette.muted
            (item.getChildAt(0) as android.widget.ImageView).setColorFilter(col)
            (item.getChildAt(1) as TextView).setTextColor(col)
            item.background = if (sel) ui.ripple(ui.rounded(Color.argb(40, 255, 143, 184), 14f), 14f) else ui.ripple(null, 14f)
        }
    }

    private fun page(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(24))
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        return Pair(scroll, content)
    }

    // ---------------- вкладка «Играть» ----------------

    private fun playPage(): View {
        val (scroll, content) = page()
        content.addView(hero(), lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })

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
        setup.addView(divider(), lp(MATCH, maxOf(1, ui.dp(1))))
        a11yStatus = statusPill()
        setup.addView(
            settingRow(R.drawable.ic_search, "Специальные возможности",
                "Нужны только для модуля Zoom: включи «Sakura Zoom»", a11yStatus) { openAccessibilitySettings() },
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

        content.addView(
            ui.pill("Диагностика Minecraft", STYLE_GHOST, R.drawable.ic_cube) { showDiagnostics() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
        )
        content.addView(
            ui.pill("Эксперимент: тест загрузки Minecraft", STYLE_GHOST, R.drawable.ic_cube) { runLoadTest() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(10) }
        )
        content.addView(
            ui.text("Sakura Client v1.11  •  Freelook и Replay — в разработке", 11f, Palette.muted)
                .apply { gravity = Gravity.CENTER },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(26) }
        )
        return scroll
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
                if (!Launcher.isInstalled(this@MainActivity, Launcher.MINECRAFT)) {
                    Launcher.openStore(this@MainActivity, Launcher.MINECRAFT)
                }
            }
        }
        box.addView(mcStatus, lp(MATCH, WRAP).apply { topMargin = ui.dp(12) })
        return box
    }

    // ---------------- вкладка «Модули» ----------------

    private fun modulesPage(): View {
        modulesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        renderModules()
        return modulesBox
    }

    private fun renderModules() {
        modulesBox.removeAllViews()
        val wide = resources.displayMetrics.widthPixels >= ui.dp(560)
        val env = GuiEnv(
            c = this, ui = ui, prefs = prefs, wide = wide, showSearch = true, innerScroll = true,
            onClose = null,
            onEditHud = {
                if (OverlayService.running) {
                    prefs.edit().putBoolean("hud_edit", true).apply()
                    toast("Перетащи виджеты в игре и нажми «Готово»")
                } else toast("Сначала включи панель кнопкой «Играть»")
            },
            dragTitle = null,
            musicContent = { box ->
                val pv = PlayerView(this, ui, 56f) { openApp(Launcher.SPOTIFY, "Spotify") }
                box.addView(pv, lp(MATCH, WRAP))
                pv.refresh()
            },
            settingsContent = { box, _ -> addPingField(box) },
            moduleExtra = { key, box -> if (key == "hud_ping") addPingField(box) }
        )
        val gui = ClickGuiView(env)
        modulesBox.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))
        gui.background = ui.rounded(Gui.bg, 18f, Gui.border, 1.2f)
        clipRounded(gui, ui, 18f)
        modulesBox.addView(gui, lp(MATCH, MATCH))
    }

    /** Поле для адреса сервера, до которого HUD мерит пинг. */
    private fun addPingField(parent: LinearLayout) {
        parent.addView(ui.text("Адрес сервера", 12f, Palette.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(12) })
        val input = android.widget.EditText(this).apply {
            setText(prefs.getString("ping_host", ""))
            hint = "play.server.net или 1.2.3.4:443"
            setHintTextColor(Palette.muted)
            setTextColor(Palette.text)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.fdp(14f))
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
            background = ui.rounded(Palette.surface, 12f, Palette.stroke)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    prefs.edit().putString("ping_host", s?.toString() ?: "").apply()
                }
            })
        }
        parent.addView(input, lp(MATCH, WRAP).apply { topMargin = ui.dp(6) })
        parent.addView(
            ui.text("Пусто: замер до Cloudflare. Мерится время TCP-соединения (порт 443, затем 80): " +
                "это задержка сети до адреса, а не игровой пинг по UDP.", 11f, Palette.muted)
                .apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(6) }
        )
    }

    // ---------------- вкладка «Библиотека» ----------------

    private fun libraryPage(): View {
        val (scroll, content) = page()
        content.addView(ui.text("Библиотека", 20f, Palette.text, true), lp(WRAP, WRAP).apply { topMargin = ui.dp(6) })
        content.addView(
            ui.text("Добавь свои миры, аддоны и скины, а потом одним нажатием отправь их в Minecraft.",
                12f, Palette.muted).apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(6) }
        )
        libBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(libBox, lp(MATCH, WRAP).apply { topMargin = ui.dp(12) })
        renderLibrary()
        return scroll
    }

    private fun libDir(cat: Int): File = File(filesDir, "library/" + LIB_KEYS[cat]).apply { mkdirs() }

    private fun renderLibrary() {
        libBox.removeAllViews()
        addChips(this, ui, libBox, listOf("Миры", "Аддоны", "Скины"), 3,
            { it == libCat }, { libCat = it; renderLibrary() })

        val info = when (libCat) {
            0 -> "Файлы .mcworld и .mctemplate. После импорта мир появится в списке «Играть» в Minecraft."
            1 -> "Add-Ons для Bedrock: .mcaddon и .mcpack (ресурс-паки, паки поведения). Это аддоны игры; " +
                "кодовые моды вроде Flarial требуют изменённую версию Minecraft."
            else -> "Файл .mcpack или обычный PNG-скин 64×64: из PNG приложение само соберёт пакет. " +
                "Скин появится в «Гардеробе» Minecraft."
        }
        libBox.addView(
            ui.text(info, 11.5f, Palette.muted).apply { setLineSpacing(0f, 1.1f) },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(12) }
        )
        libBox.addView(
            ui.pill(if (libCat == 2) "Добавить скин или пак" else "Добавить файл", STYLE_PRIMARY, R.drawable.ic_download) { pick() },
            lp(MATCH, WRAP).apply { topMargin = ui.dp(12) }
        )

        val files = libDir(libCat).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            libBox.addView(
                ui.text("Здесь пока пусто", 12f, Palette.muted).apply { gravity = Gravity.CENTER },
                lp(MATCH, WRAP).apply { topMargin = ui.dp(24) }
            )
            return
        }
        val iconRes = when (libCat) { 0 -> R.drawable.ic_cube; 1 -> R.drawable.ic_layers; else -> R.drawable.ic_petal }
        for (f in files) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(12), ui.dp(10), ui.dp(8), ui.dp(10))
                background = ui.rounded(Palette.surface2, 16f, Palette.stroke)
            }
            val ic = ui.icon(iconRes, Palette.pink).apply {
                val p = ui.dp(8)
                setPadding(p, p, p, p)
                background = ui.circle(Palette.surface)
            }
            row.addView(ic, lp(ui.dp(38), ui.dp(38)))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(ui.text(f.name, 13f, Palette.text, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }, lp(MATCH, WRAP))
            col.addView(ui.text(humanSize(f.length()), 11f, Palette.muted), lp(WRAP, WRAP).apply { topMargin = ui.dp(2) })
            row.addView(col, lp(0, WRAP, 1f).apply { leftMargin = ui.dp(10); rightMargin = ui.dp(6) })
            row.addView(ui.pill("В игру", STYLE_GHOST) { openInMinecraft(f) }, lp(WRAP, WRAP))
            row.addView(
                ui.iconButton(R.drawable.ic_close, 36f, 16f, Palette.muted) { confirmDelete(f) },
                lp(ui.dp(36), ui.dp(36)).apply { leftMargin = ui.dp(2) }
            )
            libBox.addView(row, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
        }
    }

    private fun humanSize(b: Long): String = when {
        b >= 1024L * 1024 -> String.format(Locale.US, "%.1f МБ", b / 1048576f)
        b >= 1024 -> "${b / 1024} КБ"
        else -> "$b Б"
    }

    private fun pick() {
        pendingCat = libCat
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, REQ_PICK)
        } catch (e: Exception) {
            toast("Не удалось открыть выбор файла")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { importFile(it, pendingCat) }
        }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun sanitize(n: String): String = n.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 2
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "${base}_$n" else "${base}_$n.$ext")
            n++
        }
        return f
    }

    private fun importFile(uri: Uri, cat: Int) {
        val name = displayName(uri) ?: "file"
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val base = sanitize(name.substringBeforeLast('.', name))
        val allowed = when (cat) {
            0 -> listOf("mcworld", "mctemplate", "zip")
            1 -> listOf("mcaddon", "mcpack")
            else -> listOf("mcpack", "png")
        }
        if (ext !in allowed) {
            toast("Нужен файл: " + allowed.joinToString(", ") { ".$it" })
            return
        }
        if (cat == 2 && ext == "png") {
            AlertDialog.Builder(this)
                .setTitle("Тип скина")
                .setItems(arrayOf("Обычный (руки 4 пикселя, Steve)", "Тонкий (руки 3 пикселя, Alex)")) { _, which ->
                    buildSkinPack(uri, base, which == 1)
                }
                .show()
            return
        }
        val finalName = if (cat == 0 && ext == "zip") "$base.mcworld" else sanitize(name)
        toast("Добавляю файл…")
        Thread {
            try {
                val dest = uniqueFile(libDir(cat), finalName)
                contentResolver.openInputStream(uri)!!.use { ins ->
                    dest.outputStream().use { ins.copyTo(it) }
                }
                runOnUiThread { toast("Добавлено: ${dest.name}"); renderLibrary() }
            } catch (e: Exception) {
                runOnUiThread { toast("Не удалось добавить файл") }
            }
        }.start()
    }

    /** Собирает из PNG готовый skin pack (.mcpack), который Minecraft импортирует как обычный пакет скинов. */
    private fun buildSkinPack(uri: Uri, baseName: String, slim: Boolean) {
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                val w = o.outWidth
                val h = o.outHeight
                val ok = (w == 64 && (h == 64 || h == 32)) || (w == 128 && h == 128)
                if (!ok) {
                    runOnUiThread { toast("Скин должен быть PNG 64×64 (или 128×128), а у файла $w×$h") }
                    return@Thread
                }
                val shown = baseName.replace("\"", "").replace("\\", "").ifEmpty { "Skin" }
                val id = "Sakura" + shown.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }.take(16).ifEmpty { "Skin" }
                val geometry = if (slim) "geometry.humanoid.customSlim" else "geometry.humanoid.custom"
                val manifest = """{
  "format_version": 1,
  "header": {"name": "$shown", "description": "Skin pack by Sakura Client", "uuid": "${UUID.randomUUID()}", "version": [1, 0, 0]},
  "modules": [{"type": "skin_pack", "uuid": "${UUID.randomUUID()}", "version": [1, 0, 0]}]
}"""
                val skins = """{
  "skins": [{"localization_name": "$id", "geometry": "$geometry", "texture": "skin.png", "type": "free"}],
  "serialize_name": "$id",
  "localization_name": "$id"
}"""
                val lang = "skinpack.$id=$shown\nskin.$id.$id=$shown\n"
                val dest = uniqueFile(libDir(2), sanitize(baseName) + "_skin.mcpack")
                ZipOutputStream(FileOutputStream(dest)).use { z ->
                    fun put(n: String, data: ByteArray) {
                        z.putNextEntry(ZipEntry(n))
                        z.write(data)
                        z.closeEntry()
                    }
                    put("manifest.json", manifest.toByteArray())
                    put("skins.json", skins.toByteArray())
                    put("texts/languages.json", "[\"en_US\"]".toByteArray())
                    put("texts/en_US.lang", lang.toByteArray())
                    put("skin.png", bytes)
                }
                runOnUiThread { toast("Скин собран: ${dest.name}"); renderLibrary() }
            } catch (e: Exception) {
                runOnUiThread { toast("Не удалось собрать скин") }
            }
        }.start()
    }

    private fun openInMinecraft(f: File) {
        if (!Launcher.isInstalled(this, Launcher.MINECRAFT)) {
            toast("Minecraft Bedrock не найден")
            return
        }
        val uri = LibraryProvider.uriFor(LIB_KEYS[libCat], f.name)
        if (Launcher.openFile(this, uri)) toast("Minecraft сам покажет импорт. Дождись сообщения об успехе")
        else toast("Minecraft не принял файл")
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("Удалить из библиотеки?")
            .setMessage(f.name)
            .setPositiveButton("Удалить") { _, _ -> f.delete(); renderLibrary() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------------- общие элементы ----------------

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
        val base = if (ok) Palette.ok else Palette.warn
        tv.setTextColor(base)
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
        setStatus(a11yStatus, SakuraAccessibility.instance != null, "Вкл", "Выкл")
        notifStatus?.let {
            val granted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            setStatus(it, granted, "Вкл", "Выкл")
        }
        mcStatus.text = if (Launcher.isInstalled(this, Launcher.MINECRAFT)) "Minecraft Bedrock найден ✓"
        else "Minecraft Bedrock не найден — нажми, чтобы открыть Google Play"
        ui.pillLabel(panelBtn).text = if (OverlayService.running) "Выключить панель" else "Включить панель без игры"
        if (tab == 0) player.refresh()
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

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            toast("Открой: Настройки → Специальные возможности")
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

    private fun showDiagnostics() = showText("Диагностика", Diagnostics.run(this))

    private fun runLoadTest() {
        LoadTestService.reportFile(this).delete()
        try {
            startService(Intent(this, LoadTestService::class.java))
        } catch (e: Exception) {
            toast("Не удалось запустить тест")
            return
        }
        toast("Тест идёт, подожди 8 секунд…")
        handler.postDelayed({
            val f = LoadTestService.reportFile(this)
            var t = if (f.exists()) f.readText() else "Отчёта нет: процесс теста не стартовал."
            if (!t.contains("КОНЕЦ")) t += "\n(Процесс остановился раньше конца: вероятно, упал на последнем шаге выше.)"
            showText("Тест загрузки", t)
        }, 8000)
    }

    private fun showText(title: String, text: String) {
        val tv = TextView(this).apply {
            this.text = text
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
        }
        val sv = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(sv)
            .setPositiveButton("Копировать") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("sakura", text))
                toast("Скопировано")
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_PICK = 21
        private val LIB_KEYS = arrayOf("world", "addon", "skin")
    }
}
