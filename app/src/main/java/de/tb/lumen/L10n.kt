package de.tb.lumen

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Sprache der Oberfläche. Standard ist die Systemsprache (Deutsch → values-de, sonst Englisch);
 * in Lumens Einstellungen lässt sie sich fest wählen. Texte stehen in res/values/strings.xml
 * (Englisch) und res/values-de/strings.xml (Deutsch) – weitere Sprachen: neuer values-xx-Ordner.
 *
 * Überall erreichbar über [str], auch außerhalb von Compose (Dienste, Bildschirmschoner).
 */
object L10n {
    enum class Choice(val tag: String?) { System(null), English("en"), German("de") }

    private const val KEY = "language"
    private val systemLocale: Locale = Locale.getDefault()

    @Volatile
    private var res: Resources? = null

    /** Aktuelle Sprache, z. B. für Datums-Formate. */
    @Volatile
    var locale: Locale = systemLocale
        private set

    fun choice(context: Context): Choice =
        Choice.entries.firstOrNull { it.name == Appearance.prefs(context).getString(KEY, null) } ?: Choice.System

    fun setChoice(context: Context, c: Choice) {
        Appearance.prefs(context).edit().putString(KEY, c.name).apply()
        apply(context.applicationContext)
    }

    /** Beim Start und nach jeder Änderung: Ressourcen in der gewählten Sprache bereitlegen. */
    fun apply(app: Context) {
        val tag = choice(app).tag
        locale = if (tag != null) Locale.forLanguageTag(tag) else systemLocale
        res = wrap(app).resources
    }

    /** [base] mit der gewählten Sprache – für Activity/Dienste in attachBaseContext. */
    fun wrap(base: Context): Context {
        val tag = runCatching { choice(base).tag }.getOrNull() ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val r = res ?: error("L10n.apply() wurde nicht aufgerufen")
        return if (args.isEmpty()) r.getString(id) else r.getString(id, *args)
    }
}

/** Übersetzter Text, z. B. `str(R.string.calendar_this_week)`. */
fun str(@StringRes id: Int, vararg args: Any): String = L10n.get(id, *args)

/** Lädt die Sprache, bevor irgendein Dienst oder Bildschirm startet. */
class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        L10n.apply(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        L10n.apply(this)
    }
}
