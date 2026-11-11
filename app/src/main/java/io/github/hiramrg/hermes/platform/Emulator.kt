package io.github.hiramrg.hermes.platform

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.view.Display

/** Which physical screen is which. On the AYN Thor the top panel is Android's default display. */
data class ScreenRoles(val top: Int, val bottom: Int?)

object Screens {
    fun roles(context: Context, swap: Boolean): ScreenRoles {
        val dm = context.getSystemService(DisplayManager::class.java)
        val default = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return ScreenRoles(Display.DEFAULT_DISPLAY, null)
        val second = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }.minByOrNull { d ->
            // The Thor's bottom panel is 1240x1080; prefer the display closest to that over e.g. HDMI.
            val m = d.mode
            kotlin.math.abs(m.physicalWidth.toLong() * m.physicalHeight - 1240L * 1080L)
        } ?: return ScreenRoles(default.displayId, null)
        return if (swap) ScreenRoles(second.displayId, default.displayId) else ScreenRoles(default.displayId, second.displayId)
    }

    fun options(displayId: Int?) = displayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
}

/** Talks to M64Plus FZ (Pro or free). */
class Emulator(private val context: Context) {

    companion object {
        val PACKAGES = listOf(
            "org.mupen64plusae.v3.fzurita.pro",
            "org.mupen64plusae.v3.fzurita",
            "org.mupen64plusae.v3.alpha",
        )
        const val SPLASH = "paulscode.android.mupen64plusae.SplashActivity"
        const val STORE_PACKAGE = "org.mupen64plusae.v3.fzurita"
    }

    fun installedPackage(): String? = PACKAGES.firstOrNull { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    }

    val isInstalled: Boolean get() = installedPackage() != null

    private fun start(intent: Intent, displayId: Int?): Boolean = try {
        context.startActivity(intent, Screens.options(displayId)); true
    } catch (_: Exception) {
        // Some firmwares refuse cross-display launches; fall back to the default screen.
        try { context.startActivity(intent); true } catch (_: Exception) { false }
    }

    /**
     * Starts [rom] in M64Plus FZ. Its SplashActivity takes a VIEW intent with the ROM; games started
     * this way always boot fresh (no auto-resume), so Stadium reads the cartridge now in the slot.
     */
    fun launch(rom: Uri, displayId: Int?): Boolean {
        val pkg = installedPackage() ?: return false
        runCatching { context.grantUriPermission(pkg, rom, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(pkg, SPLASH)
            setDataAndType(rom, "application/octet-stream")
            clipData = ClipData.newRawUri("rom", rom)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(intent, displayId)
    }

    /** Opens M64Plus FZ's game list (for the one-time setup), or returns to a running game. */
    fun open(displayId: Int?): Boolean {
        val pkg = installedPackage() ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        return start(intent, displayId)
    }

    fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$STORE_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$STORE_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(market) } catch (_: Exception) { runCatching { context.startActivity(web) } }
    }
}
