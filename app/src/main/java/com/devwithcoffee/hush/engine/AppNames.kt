package com.devwithcoffee.hush.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.devwithcoffee.hush.core.Packages

data class AppInfo(val pkg: String, val label: String)

object AppNames {
    private val cache = HashMap<String, String>()
    private val icons = HashMap<String, Bitmap?>()

    fun label(ctx: Context, pkg: String): String = synchronized(cache) {
        cache.getOrPut(pkg) {
            Packages.LABELS[pkg]?.takeIf { pkg == "phone" } ?: runCatching {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrElse { Packages.LABELS[pkg] ?: pkg.substringAfterLast('.') }
        }
    }

    fun icon(ctx: Context, pkg: String): Bitmap? = synchronized(icons) {
        icons.getOrPut(pkg) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
        }
    }

    fun installed(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    /** Launchable apps, sorted by name. */
    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
