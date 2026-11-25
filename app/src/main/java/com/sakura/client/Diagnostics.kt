package com.sakura.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

/** Отчёт: можно ли вообще подключаться к установленному Minecraft на этом телефоне. Ничего не меняет и не запускает. */
object Diagnostics {
    fun run(ctx: Context): String {
        val sb = StringBuilder()
        fun line(s: String) { sb.append(s).append('\n') }
        line("== Телефон ==")
        line("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("ABI: " + Build.SUPPORTED_ABIS.joinToString())
        line("Sakura: " + try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" })
        line("")
        line("== Minecraft ==")
        val pm = ctx.packageManager
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(Launcher.MINECRAFT, PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.getPackageInfo(Launcher.MINECRAFT, 0)
        } catch (e: Exception) {
            line("Не найден: ${e.javaClass.simpleName}")
            return sb.toString()
        }
        val ai = info.applicationInfo
        line("Версия: ${info.versionName}")
        @Suppress("DEPRECATION")
        line("versionCode: ${info.versionCode}")
        if (ai == null) {
            line("applicationInfo недоступен")
            return sb.toString()
        }
        line("targetSdk: ${ai.targetSdkVersion}")
        line("Установщик: " + try {
            if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(Launcher.MINECRAFT).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(Launcher.MINECRAFT)
        } catch (_: Exception) { "?" })
        line("Основной ABI игры: " + (try { ai.javaClass.getField("primaryCpuAbi").get(ai) } catch (_: Exception) { "?" }))
        line("Debuggable: " + ((ai.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0))
        line("")
        line("== Файлы игры ==")
        val base = File(ai.sourceDir)
        line("base.apk: ${base.length() / 1048576} МБ, читается: ${base.canRead()}")
        val splits = ai.splitSourceDirs
        line("Сплитов: ${splits?.size ?: 0}")
        splits?.forEach { line("  ${File(it).name}: ${File(it).length() / 1048576} МБ, читается: ${File(it).canRead()}") }
        val nat = File(ai.nativeLibraryDir)
        line("nativeLibraryDir: ${nat.path}")
        line("  существует: ${nat.exists()}, файлов: ${nat.list()?.size ?: "нет доступа"}")
        nat.list()?.sorted()?.take(15)?.forEach { line("  $it") }
        val apks = ArrayList<String>().apply { add(ai.sourceDir); splits?.let { addAll(it) } }
        for (a in apks) {
            try {
                ZipFile(a).use { z ->
                    val libs = z.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.toList()
                    if (libs.isNotEmpty()) {
                        line("В ${File(a).name} библиотек: ${libs.size}")
                        libs.take(12).forEach { line("  ${it.name} (${it.size / 1024} КБ, сжатие=${it.method})") }
                        if (libs.any { it.name.endsWith("libminecraftpe.so") }) line("  → libminecraftpe.so найдена")
                    }
                }
            } catch (e: Exception) {
                line("Не удалось прочитать ${File(a).name}: ${e.javaClass.simpleName}")
            }
        }
        line("")
        line("== Данные игры ==")
        val ext = File("/storage/emulated/0/Android/data/${Launcher.MINECRAFT}/files")
        line("Android/data/…/files: существует=${ext.exists()}, список=${ext.list()?.size ?: "нет доступа"}")
        return sb.toString()
    }
}
