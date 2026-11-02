package de.tb.lumen

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Dunkelmodus: [Mode.Auto] = dunkel von [darkFrom] bis [darkUntil] Uhr (Standard 19–7).
 * Der Schalter im Kontrollzentrum überstimmt die Automatik bis zum nächsten Umschaltpunkt.
 */
object Appearance {
    enum class Mode { Auto, Always, Never }

    private const val KEY_MODE = "dark_mode"
    private const val KEY_FROM = "dark_from"
    private const val KEY_UNTIL = "dark_until"
    private const val KEY_VALUE = "dark_override"
    private const val KEY_OVERRIDE_UNTIL = "dark_override_until"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    fun mode(p: SharedPreferences) = runCatching { Mode.valueOf(p.getString(KEY_MODE, null)!!) }.getOrDefault(Mode.Auto)
    fun darkFrom(p: SharedPreferences) = p.getInt(KEY_FROM, 19)
    fun darkUntil(p: SharedPreferences) = p.getInt(KEY_UNTIL, 7)

    fun setMode(p: SharedPreferences, mode: Mode) =
        p.edit().putString(KEY_MODE, mode.name).remove(KEY_OVERRIDE_UNTIL).apply()

    fun setHours(p: SharedPreferences, from: Int, until: Int) =
        p.edit().putInt(KEY_FROM, from.mod(24)).putInt(KEY_UNTIL, until.mod(24)).remove(KEY_OVERRIDE_UNTIL).apply()

    fun isDark(context: Context): Boolean = isDark(prefs(context), System.currentTimeMillis())

    fun isDark(p: SharedPreferences, nowMs: Long): Boolean {
        when (mode(p)) {
            Mode.Always -> return true
            Mode.Never -> return false
            Mode.Auto -> Unit
        }
        if (nowMs < p.getLong(KEY_OVERRIDE_UNTIL, 0)) return p.getBoolean(KEY_VALUE, false)
        return autoDark(zoned(nowMs).hour, darkFrom(p), darkUntil(p))
    }

    private fun autoDark(hour: Int, from: Int, until: Int) =
        if (from <= until) hour in from until until else hour >= from || hour < until

    /** Schalter im Kontrollzentrum: bei Auto bis zum nächsten Umschaltpunkt, sonst Modus wechseln. */
    fun toggle(p: SharedPreferences, nowMs: Long) {
        val dark = isDark(p, nowMs)
        when (mode(p)) {
            Mode.Auto -> p.edit()
                .putBoolean(KEY_VALUE, !dark)
                .putLong(KEY_OVERRIDE_UNTIL, nextSwitch(p, nowMs))
                .apply()
            else -> setMode(p, if (dark) Mode.Never else Mode.Always)
        }
    }

    private fun nextSwitch(p: SharedPreferences, nowMs: Long): Long {
        val now = zoned(nowMs)
        val today = now.toLocalDate()
        return listOf(darkUntil(p), darkFrom(p))
            .flatMap { h -> listOf(today.atTime(h, 0), today.plusDays(1).atTime(h, 0)) }
            .map { it.atZone(now.zone) }
            .filter { it.isAfter(now) }
            .minOf { it }
            .toInstant().toEpochMilli()
    }

    private fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
}
