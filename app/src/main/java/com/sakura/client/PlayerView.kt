package com.sakura.client

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout

/** Карточка Spotify: обложка, трек, прогресс и кнопки. Обновляется вызовом refresh(). */
class PlayerView(
    private val c: Context,
    private val ui: Ui,
    private val artDp: Float,
    private val onOpenSpotify: () -> Unit
) : LinearLayout(c) {

    private val art = ImageView(c)
    private val title = ui.text("", 14f, Palette.text, true)
    private val artist = ui.text("", 12f, Palette.muted)
    private val progress = ThinProgress(c)
    private val playBtn: ImageView
    private val openBtn: LinearLayout
    private var lastKey: String? = null

    init {
        orientation = VERTICAL

        art.apply {
            background = ui.rounded(Palette.surface2, 12f)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            isClickable = true
            setOnClickListener { onOpenSpotify() }
        }
        title.apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        artist.apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }

        val top = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(art, lp(ui.dp(artDp), ui.dp(artDp)))
        val texts = LinearLayout(c).apply { orientation = VERTICAL }
        texts.addView(title, lp(MATCH, WRAP))
        texts.addView(artist, lp(MATCH, WRAP).apply { topMargin = ui.dp(4) })
        top.addView(texts, lp(0, WRAP, 1f).apply { leftMargin = ui.dp(12) })
        addView(top, lp(MATCH, WRAP))

        addView(progress, lp(MATCH, ui.dp(4)).apply { topMargin = ui.dp(12) })

        val controls = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        val prev = ui.iconButton(R.drawable.ic_prev, 44f, 24f, Palette.text) {
            SpotifyBridge.previous(c); later()
        }
        playBtn = ui.iconButton(R.drawable.ic_play, 52f, 26f, Palette.onPink, ui.circle(Palette.pink)) {
            SpotifyBridge.playPause(c); later()
        }
        val next = ui.iconButton(R.drawable.ic_next, 44f, 24f, Palette.text) {
            SpotifyBridge.next(c); later()
        }
        controls.addView(prev, lp(ui.dp(44), ui.dp(44)))
        controls.addView(playBtn, lp(ui.dp(52), ui.dp(52)).apply { leftMargin = ui.dp(14); rightMargin = ui.dp(14) })
        controls.addView(next, lp(ui.dp(44), ui.dp(44)))
        addView(controls, lp(MATCH, WRAP).apply { topMargin = ui.dp(6) })

        openBtn = ui.pill("Открыть Spotify", STYLE_GHOST, R.drawable.ic_music) { onOpenSpotify() }
        addView(openBtn, lp(MATCH, WRAP).apply { topMargin = ui.dp(8) })
    }

    private fun later() {
        postDelayed({ refresh() }, 300)
    }

    fun refresh() {
        val s = SpotifyBridge.snapshot(c)
        val t: String
        val a: String
        when {
            !s.listenerOk -> { t = "Spotify"; a = "Включи доступ к уведомлениям, чтобы видеть трек. Кнопки работают и так." }
            !s.active -> { t = "Музыка не играет"; a = "Запусти трек в Spotify" }
            else -> { t = s.title ?: "Без названия"; a = s.artist ?: "Spotify" }
        }
        if (title.text.toString() != t) title.text = t
        if (artist.text.toString() != a) artist.text = a

        val key = t + "|" + a + "|" + (s.art?.let { System.identityHashCode(it) } ?: 0)
        if (key != lastKey) {
            lastKey = key
            if (s.art != null) {
                art.setPadding(0, 0, 0, 0)
                art.clearColorFilter()
                art.scaleType = ImageView.ScaleType.CENTER_CROP
                art.setImageBitmap(s.art)
            } else {
                val p = ui.dp(artDp * 0.28f)
                art.setPadding(p, p, p, p)
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.setImageResource(R.drawable.ic_music)
                art.setColorFilter(Palette.muted)
            }
        }
        progress.fraction = s.fraction
        playBtn.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        openBtn.visibility = if (s.active) View.GONE else View.VISIBLE
    }
}
