package de.tb.lumen

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    /** true = über LEANBACK_LAUNCHER gefunden (echte TV-App). */
    val isTv: Boolean,
)

/** [darkImage]: Variante heller Banner (weißer Grund) für den Dunkelmodus, sonst null. */
class AppArt(val image: ImageBitmap, val isBanner: Boolean, val darkImage: ImageBitmap? = null) {
    fun forMode(dark: Boolean): ImageBitmap = if (dark) darkImage ?: image else image
}

object AppRepository {
    const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
    const val FIRE_SETTINGS = "com.amazon.tv.settings.v2"

    private val hiddenPackages = setOf(AMAZON_LAUNCHER, "com.android.settings")

    fun loadApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()

        fun collect(category: String, isTv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo
                val pkg = ai.packageName
                if (pkg == context.packageName || pkg in hiddenPackages || pkg.startsWith("com.amazon.dummy")) continue
                if (pkg in result) continue
                result[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), ComponentName(pkg, ai.name), isTv)
            }
        }

        // TV-Apps zuerst, damit deren Leanback-Activity (mit Banner) gewinnt.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isTv = true)
        collect(Intent.CATEGORY_LAUNCHER, isTv = false)
        return result.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Banner bevorzugt, sonst Icon. Standard 352×198 (Dock/Raster); das große „Weiterschauen“-
     * Widget lädt mit [scale] = 2 eine schärfere Fassung nur für sich (spart Speicher).
     */
    fun loadArt(context: Context, app: AppEntry, scale: Int = 1): AppArt? {
        val pm = context.packageManager
        return try {
            val banner = pm.getActivityBanner(app.component) ?: pm.getApplicationBanner(app.packageName)
            if (banner != null) {
                val bmp = banner.toBitmap(352 * scale, 198 * scale, Bitmap.Config.ARGB_8888)
                AppArt(bmp.asImageBitmap(), isBanner = true, darkImage = BannerTint.darkVariant(bmp)?.asImageBitmap())
            } else {
                AppArt(pm.getActivityIcon(app.component).toBitmap(144, 144).asImageBitmap(), isBanner = false)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun launch(context: Context, app: AppEntry): Boolean {
        val category = if (app.isTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Fire-TV-Einstellungen. Für Fremd-Apps zeigt ACTION_SETTINGS nur auf eine Attrappe
     * (CTSDummySettingsHandler); die echten Einstellungen sind ein Tab der Amazon-Startseite.
     * Deshalb die HomeWatcher-Umleitung für diese Sitzung aussetzen.
     */
    fun openFireSettings(context: Context) {
        HomeWatcherService.instance?.beginSettingsSession()
        val intent = Intent().setComponent(ComponentName(AMAZON_LAUNCHER, "$AMAZON_LAUNCHER.ui.SettingsActivity"))
        if (!start(context, intent)) start(context, Intent(android.provider.Settings.ACTION_SETTINGS))
    }

    /**
     * Öffnet eine Einstellungsseite; fällt auf die Fire-TV-Einstellungen zurück.
     * Viele Fire-Seiten (z. B. Display & Töne) verlangen die Amazon-Systemberechtigung
     * LAUNCHER_SETTINGS -> SecurityException.
     */
    fun openSettings(context: Context, action: String) {
        if (!start(context, Intent(action))) openFireSettings(context)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
