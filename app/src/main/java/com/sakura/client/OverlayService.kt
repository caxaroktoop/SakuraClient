package com.sakura.client

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var crosshair: CrosshairView? = null
    private var panel: View? = null
    private var bubble: TextView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var trackText: TextView? = null
    private var artistText: TextView? = null
    private var albumArt: ImageView? = null
    private var playButton: Button? = null
    private var panelRoot: LinearLayout? = null
    private var panelBg: GradientDrawable? = null
    private fun prefs() = getSharedPreferences("sakura_settings", MODE_PRIVATE)

    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) showOverlay()
        return START_STICKY
    }

    private fun makeParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun showOverlay() {
        if (crosshair != null) return
        crosshair = CrosshairView(this)
        val cp = makeParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0; y = 0
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.addView(crosshair, cp)

        bubble = TextView(this).apply {
            text = "🌸"; textSize = 23f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.argb(230, 145, 55, 105))
            setPadding(13, 8, 13, 8)
            setOnClickListener { if (panel == null) showPanel() else hidePanel() }
        }
        bubbleParams = makeParams().apply { x = prefs().getInt("bubble_x", 18); y = prefs().getInt("bubble_y", 210) }
        wm.addView(bubble, bubbleParams)
        enableDrag(bubble!!, bubbleParams!!)
        showPanel()
    }

    private fun hidePanel() {
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        panel = null; panelRoot = null; panelBg = null; trackText = null; artistText = null; albumArt = null; playButton = null
    }

    private fun mediaController() = SakuraMediaListener.currentController

    private fun updateTrackInfo() {
        val controller = mediaController()
        val metadata = controller?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        trackText?.text = if (!title.isNullOrBlank()) title else "Музыка не обнаружена"
        artistText?.text = artist ?: "Открой Spotify и запусти музыку"

        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (artwork != null) {
            albumArt?.setImageBitmap(artwork)
            albumArt?.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            albumArt?.setImageResource(android.R.drawable.ic_media_play)
            albumArt?.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val state = controller?.playbackState?.state
        playButton?.text = if (state == PlaybackState.STATE_PLAYING) "Пауза" else "Играть"
    }

    private fun showPanel() {
        if (panel != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = GradientDrawable().apply {
                setColor(Color.argb(prefs().getInt("opacity", 96) * 255 / 100, 30, 21, 33))
                cornerRadius = 22f
                setStroke(2, Color.rgb(118, 67, 99))
            }
        }
        panelBg = root.background as? GradientDrawable
        panelRoot = root
        val initialScale = prefs().getInt("scale", 100) / 100f
        root.scaleX = initialScale
        root.scaleY = initialScale
        root.addView(TextView(this).apply {
            text = "🌸 SAKURA CLIENT"
            textSize = 16f
            setTextColor(Color.rgb(255, 182, 208))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })

        val musicCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 10, 8, 8)
        }
        albumArt = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            background = GradientDrawable().apply {
                setColor(Color.rgb(70, 48, 69))
                cornerRadius = 12f
            }
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        musicCard.addView(albumArt, LinearLayout.LayoutParams(dp(66), dp(66)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        trackText = TextView(this).apply {
            text = "Музыка не обнаружена"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 2
        }
        artistText = TextView(this).apply {
            text = "Открой Spotify и запусти музыку"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            maxLines = 1
        }
        info.addView(trackText)
        info.addView(artistText)
        musicCard.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(musicCard)

        val mediaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        mediaRow.addView(Button(this).apply {
            text = "⏮"
            contentDescription = "Предыдущий трек"
            setOnClickListener { mediaController()?.transportControls?.skipToPrevious(); updateTrackInfo() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        playButton = Button(this).apply {
            text = "Играть"
            setOnClickListener {
                val c = mediaController()
                if (c?.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c?.transportControls?.play()
                updateTrackInfo()
            }
        }
        mediaRow.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))
        mediaRow.addView(Button(this).apply {
            text = "⏭"
            contentDescription = "Следующий трек"
            setOnClickListener { mediaController()?.transportControls?.skipToNext(); updateTrackInfo() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(mediaRow)
        root.addView(Button(this).apply {
            text = "Обновить трек"
            setOnClickListener { updateTrackInfo() }
        })

        root.addView(TextView(this).apply {
            text = "ВНЕШНИЙ ВИД"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 182, 208))
        })
        root.addView(TextView(this).apply { text = "Прозрачность панели"; textSize = 11f; setTextColor(Color.LTGRAY) })
        root.addView(SeekBar(this).apply {
            max = 65
            progress = prefs().getInt("opacity", 96).coerceIn(35, 100) - 35
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) { val v = value + 35; prefs().edit().putInt("opacity", v).apply(); panelBg?.setColor(Color.argb(v * 255 / 100, 30, 21, 33)) }
                }
                override fun onStartTrackingTouch(seek: SeekBar?) {}
                override fun onStopTrackingTouch(seek: SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply { text = "Размер панели"; textSize = 11f; setTextColor(Color.LTGRAY) })
        root.addView(SeekBar(this).apply {
            max = 60
            progress = prefs().getInt("scale", 100).coerceIn(80, 140) - 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) { val v = value + 80; prefs().edit().putInt("scale", v).apply(); panelRoot?.scaleX = v / 100f; panelRoot?.scaleY = v / 100f }
                }
                override fun onStartTrackingTouch(seek: SeekBar?) {}
                override fun onStopTrackingTouch(seek: SeekBar?) {}
            })
        })

        val divider = View(this).apply { setBackgroundColor(Color.rgb(86, 59, 81)) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(6); bottomMargin = dp(6)
        })

        root.addView(TextView(this).apply {
            text = "ПРИЦЕЛ"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 182, 208))
        })
        root.addView(Switch(this).apply {
            text = "Показывать прицел"
            setTextColor(Color.WHITE)
            isChecked = crosshair?.visible ?: true
            setOnCheckedChangeListener { _, checked ->
                crosshair?.visible = checked; crosshair?.invalidate()
            }
        })
        root.addView(SeekBar(this).apply {
            max = 36; progress = 12
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, value: Int, user: Boolean) {
                    if (user) { crosshair?.arm = 8f + value; crosshair?.invalidate() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        root.addView(Button(this).apply {
            text = "Сменить цвет прицела"
            setOnClickListener { crosshair?.cycleColor(); crosshair?.invalidate() }
        })
        root.addView(TextView(this).apply {
            text = "Zoom камеры Minecraft пока не реализован"
            textSize = 10f; setTextColor(Color.LTGRAY)
        })
        root.addView(Button(this).apply {
            text = "Свернуть панель"
            setOnClickListener { hidePanel() }
        })
        root.addView(Button(this).apply {
            text = "Выключить Sakura"
            setOnClickListener { stopSelf() }
        })

        panel = root
        panelParams = makeParams().apply { x = prefs().getInt("panel_x", 75); y = prefs().getInt("panel_y", 130) }
        wm.addView(root, panelParams)
        enableDrag(root, panelParams!!)
        updateTrackInfo()
    }

    private fun enableDrag(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = params.x; startY = params.y; moved = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                        if (moved) {
                            params.x = startX + dx; params.y = startY + dy
                            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                            if (view === panelRoot) prefs().edit().putInt("panel_x", params.x).putInt("panel_y", params.y).apply()
                            if (view === bubble) prefs().edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        hidePanel()
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { crosshair?.let { wm.removeView(it) } } catch (_: Exception) {}
        bubble = null; crosshair = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    class CrosshairView(context: android.content.Context) : View(context) {
        var visible = true
        var arm = 20f
        private val colors = intArrayOf(Color.rgb(255,105,180), Color.WHITE, Color.rgb(120,220,255), Color.rgb(180,255,150))
        private var colorIndex = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[0]; strokeWidth = 3.5f; style = Paint.Style.STROKE }
        fun cycleColor() { colorIndex = (colorIndex + 1) % colors.size; paint.color = colors[colorIndex] }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); if (!visible) return
            paint.color = colors[colorIndex]
            val x = width / 2f; val y = height / 2f; val gap = 5f
            canvas.drawLine(x-arm, y, x-gap, y, paint)
            canvas.drawLine(x+gap, y, x+arm, y, paint)
            canvas.drawLine(x, y-arm, x, y-gap, paint)
            canvas.drawLine(x, y+gap, x, y+arm, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 2.2f, paint)
            paint.style = Paint.Style.STROKE
        }
    }

    companion object { const val ACTION_START = "com.sakura.client.START" }
}
