package com.sakura.client

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import java.io.File
import java.io.FileWriter

/**
 * Эксперимент в ОТДЕЛЬНОМ процессе (:mctest): пробуем загрузить код и нативные библиотеки Minecraft
 * загрузчиком самой игры. Игру это не запускает; если процесс упадёт, основное приложение не пострадает.
 * Каждый шаг пишется в файл ДО выполнения, чтобы по отчёту было видно, на чём упало.
 */
class LoadTestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread { run(); Process.killProcess(Process.myPid()) }.start()
        return START_NOT_STICKY
    }

    private fun log(s: String) {
        try {
            FileWriter(reportFile(this), true).use { it.write(s + "\n"); it.flush() }
        } catch (_: Exception) {
        }
    }

    private fun err(e: Throwable): String {
        val sb = StringBuilder()
        var t: Throwable? = e
        var n = 0
        while (t != null && n < 3) {
            sb.append(if (n == 0) "" else " <- ").append(t.javaClass.name).append(": ").append(t.message)
            t = t.cause
            n++
        }
        return sb.toString()
    }

    private fun run() {
        log("Шаг 1: createPackageContext (код игры)")
        val loader: ClassLoader
        try {
            val ctx = createPackageContext(
                Launcher.MINECRAFT, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            loader = ctx.classLoader
            log("  ok. $loader")
        } catch (e: Throwable) {
            log("  ОШИБКА: " + err(e))
            log("КОНЕЦ")
            return
        }
        val cn = "com.mojang.minecraftpe.MainActivity"
        log("Шаг 2: загрузка класса $cn без инициализации")
        try {
            val c = Class.forName(cn, false, loader)
            val chain = ArrayList<String>()
            var s: Class<*>? = c.superclass
            while (s != null) { chain.add(s.name); s = s.superclass }
            log("  ok. Родители: " + chain.joinToString(" > "))
        } catch (e: Throwable) {
            log("  ОШИБКА: " + err(e))
            log("КОНЕЦ")
            return
        }
        log("Шаг 3: инициализация класса (тут игра грузит libminecraftpe.so)")
        try {
            Class.forName(cn, true, loader)
            log("  ok, инициализация прошла")
        } catch (e: Throwable) {
            log("  ОШИБКА: " + err(e))
        }
        log("Шаг 4: что реально загружено в процесс")
        try {
            val lines = File("/proc/self/maps").readLines().filter { it.contains("minecraftpe") || it.contains("libfmod") }
            if (lines.isEmpty()) log("  библиотеки игры в процессе не найдены")
            else lines.take(4).forEach { log("  " + it.substringAfterLast(' ')) }
            log("  всего подходящих записей: ${lines.size}")
        } catch (e: Throwable) {
            log("  ОШИБКА чтения maps: " + err(e))
        }
        log("КОНЕЦ")
    }

    companion object {
        fun reportFile(ctx: Context) = File(ctx.filesDir, "loadtest.txt")
    }
}
