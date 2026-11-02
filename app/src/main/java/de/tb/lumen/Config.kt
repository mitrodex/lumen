package de.tb.lumen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Private Einstellungen (TMDB-Schlüssel, Kalender-Links). Vorrang hat, was per ADB gesetzt wurde –
 * so funktioniert auch eine fertige APK ohne eigenen Build. Sonst gelten die Werte aus
 * local.properties (BuildConfig), sonst ist die Funktion einfach aus.
 *
 *   adb shell am broadcast -a de.tb.lumen.CONFIG -n de.tb.lumen/.ConfigReceiver \
 *       --es tmdb_key "<schlüssel>" --es calendar_urls "webcal://…,webcal://…"
 *
 * Ein leerer Wert ("") löscht die Einstellung wieder.
 */
object Config {
    const val TMDB_KEY = "tmdb_key"
    const val CALENDAR_URLS = "calendar_urls"

    fun tmdbKey(context: Context): String =
        Appearance.prefs(context).getString(TMDB_KEY, null) ?: BuildConfig.TMDB_KEY

    fun calendarUrls(context: Context): List<String> =
        parseUrls(Appearance.prefs(context).getString(CALENDAR_URLS, null) ?: BuildConfig.CALENDAR_URLS)

    /** „a, b,,c“ → [a, b, c]. */
    fun parseUrls(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Nimmt die Einstellungen von ADB entgegen. Geschützt durch WRITE_SECURE_SETTINGS: die hat die
 * ADB-Shell, normale Apps nicht – fremde Apps können hier also nichts setzen.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val edit = Appearance.prefs(context).edit()
        for (key in listOf(Config.TMDB_KEY, Config.CALENDAR_URLS)) {
            val value = intent.getStringExtra(key) ?: continue
            if (value.isBlank()) edit.remove(key) else edit.putString(key, value.trim())
            Log.i("Lumen", "Config: $key ${if (value.isBlank()) "gelöscht" else "gesetzt"}")
        }
        edit.apply()
        // Kalender beim nächsten Blick sofort neu laden.
        Calendar.invalidate(context)
        // Sprache: --es language en|de|system (wirkt beim nächsten Aufbau des Bildschirms).
        intent.getStringExtra("language")?.let { tag ->
            val choice = L10n.Choice.entries.firstOrNull { it.tag == tag.lowercase() } ?: L10n.Choice.System
            L10n.setChoice(context, choice)
            Log.i("Lumen", "Config: language $choice")
        }
    }
}
