package com.sakura.client

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/** Служба только для модуля Zoom: управляет увеличением экрана. Содержимое экрана не читает. */
class SakuraAccessibility : AccessibilityService() {
    private val prefs: SharedPreferences by lazy { getSharedPreferences("sakura_settings", MODE_PRIVATE) }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "zoom_on" || key == "zoom_level") apply()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putBoolean("zoom_on", false).apply()
        apply()
    }

    @Suppress("DEPRECATION")
    private fun apply() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val mc = magnificationController
            if (prefs.getBoolean("zoom_on", false)) {
                val level = prefs.getInt("zoom_level", 200).coerceIn(150, 400) / 100f
                val m = DisplayMetrics()
                (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
                mc.setScale(level, true)
                mc.setCenter(m.widthPixels / 2f, m.heightPixels / 2f, true)
            } else {
                mc.reset(true)
            }
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        try { prefs.unregisterOnSharedPreferenceChangeListener(listener) } catch (_: Exception) {}
        try { if (Build.VERSION.SDK_INT >= 26) magnificationController.reset(false) } catch (_: Exception) {}
        prefs.edit().putBoolean("zoom_on", false).apply()
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var instance: SakuraAccessibility? = null
    }
}
