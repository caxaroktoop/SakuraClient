package com.sakura.client

import android.content.Context
import android.content.Intent
import android.net.Uri

object Launcher {
    const val MINECRAFT = "com.mojang.minecraftpe"
    const val SPOTIFY = "com.spotify.music"

    private fun launchIntent(ctx: Context, pkg: String): Intent? {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { return it }
        if (pkg == MINECRAFT) {
            // запасной вариант: прямой вызов главной активности Bedrock
            val i = Intent().setClassName(MINECRAFT, "com.mojang.minecraftpe.MainActivity")
            if (i.resolveActivity(ctx.packageManager) != null) return i
        }
        return null
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = launchIntent(ctx, pkg) != null

    fun open(ctx: Context, pkg: String): Boolean {
        val i = launchIntent(ctx, pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openStore(ctx: Context, pkg: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(flags))
        } catch (e: Exception) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                        .addFlags(flags)
                )
            } catch (_: Exception) {
            }
        }
    }
}
