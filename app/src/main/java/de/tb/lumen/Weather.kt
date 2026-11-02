package de.tb.lumen

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class HourForecast(val hour: Int, val tempC: Int, val code: Int, val isDay: Boolean)

data class WeatherNow(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val place: String,
    val time: Long,
    /** Die nächsten Stunden (ohne die laufende), für die Vorschau. */
    val hourly: List<HourForecast> = emptyList(),
)

/**
 * Aktuelles Wetter von Open-Meteo (kostenlos, ohne Konto/Schlüssel). Der Ort wird einmal per
 * Name gesetzt (Geocoding), danach werden nur noch die Koordinaten abgefragt – höchstens alle
 * 30 Minuten. Netzwerk-Aufrufe blockieren: nur aus einem Hintergrund-Thread aufrufen.
 */
object Weather {
    private const val KEY_LAT = "weather_lat"
    private const val KEY_LON = "weather_lon"
    private const val KEY_PLACE = "weather_place"
    private const val KEY_TEMP = "weather_temp"
    private const val KEY_CODE = "weather_code"
    private const val KEY_DAY = "weather_day"
    private const val KEY_TIME = "weather_time"
    private const val KEY_HOURLY = "weather_hourly"
    private const val HOURS = 5
    private const val MAX_AGE_MS = 30 * 60_000L

    fun cached(context: Context): WeatherNow? {
        val p = Appearance.prefs(context)
        if (!p.contains(KEY_TIME)) return null
        return WeatherNow(
            p.getInt(KEY_TEMP, 0), p.getInt(KEY_CODE, 0), p.getBoolean(KEY_DAY, true),
            p.getString(KEY_PLACE, "") ?: "", p.getLong(KEY_TIME, 0),
            parseHourly(p.getString(KEY_HOURLY, null)),
        )
    }

    /** Gespeichert als „Stunde,Temp,Code,Tag;…“. */
    private fun parseHourly(s: String?): List<HourForecast> =
        s.orEmpty().split(';').mapNotNull { e ->
            val f = e.split(',')
            if (f.size != 4) null
            else runCatching { HourForecast(f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3] == "1") }.getOrNull()
        }

    private fun encodeHourly(list: List<HourForecast>) =
        list.joinToString(";") { "${it.hour},${it.tempC},${it.code},${if (it.isDay) 1 else 0}" }

    fun hasPlace(context: Context) = Appearance.prefs(context).contains(KEY_LAT)

    /** Sucht den Ort und speichert ihn. false, wenn nichts gefunden wurde. */
    fun setPlace(context: Context, name: String): Boolean {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=de&format=json")
            ?: return false
        val hit = json.optJSONArray("results")?.optJSONObject(0) ?: return false
        Appearance.prefs(context).edit()
            .putFloat(KEY_LAT, hit.getDouble("latitude").toFloat())
            .putFloat(KEY_LON, hit.getDouble("longitude").toFloat())
            .putString(KEY_PLACE, hit.optString("name", name))
            .remove(KEY_TIME)
            .apply()
        return true
    }

    /** Lädt neu, wenn der Stand älter als 30 Minuten ist (oder [force]). */
    fun refreshIfDue(context: Context, force: Boolean = false): WeatherNow? {
        val p = Appearance.prefs(context)
        if (!p.contains(KEY_LAT)) return null
        val cached = cached(context)
        val fresh = cached != null && System.currentTimeMillis() - cached.time < MAX_AGE_MS && p.contains(KEY_HOURLY)
        if (!force && fresh) return cached

        val lat = p.getFloat(KEY_LAT, 0f)
        val lon = p.getFloat(KEY_LON, 0f)
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day&forecast_hours=%d&timezone=auto",
            lat, lon, HOURS + 1,
        )
        val json = get(url) ?: return cached
        val current = json.optJSONObject("current") ?: return cached
        val now = WeatherNow(
            tempC = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            place = p.getString(KEY_PLACE, "") ?: "",
            time = System.currentTimeMillis(),
            hourly = hourlyFrom(json),
        )
        p.edit()
            .putInt(KEY_TEMP, now.tempC)
            .putInt(KEY_CODE, now.code)
            .putBoolean(KEY_DAY, now.isDay)
            .putLong(KEY_TIME, now.time)
            .putString(KEY_HOURLY, encodeHourly(now.hourly))
            .apply()
        return now
    }

    /** Stündliche Werte; die erste Stunde ist die laufende und wird übersprungen. */
    private fun hourlyFrom(json: JSONObject): List<HourForecast> = runCatching {
        val h = json.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val days = h.optJSONArray("is_day")
        (1 until times.length()).map { i ->
            HourForecast(
                hour = times.getString(i).substringAfter('T').take(2).toInt(),
                tempC = Math.round(temps.getDouble(i)).toInt(),
                code = codes.getInt(i),
                isDay = days?.optInt(i, 1) != 0,
            )
        }.take(HOURS)
    }.getOrDefault(emptyList())

    /** WMO-Wettercode -> Kurzbeschreibung in der Sprache der Oberfläche. */
    fun describe(code: Int, isDay: Boolean): String = when (code) {
        0 -> str(if (isDay) R.string.wx_sunny else R.string.wx_clear)
        1 -> str(if (isDay) R.string.wx_mostly_sunny else R.string.wx_mostly_clear)
        2 -> str(R.string.wx_partly_cloudy)
        3 -> str(R.string.wx_overcast)
        45, 48 -> str(R.string.wx_fog)
        51, 53, 55, 56, 57 -> str(R.string.wx_drizzle)
        61, 63, 65, 66, 67 -> str(R.string.wx_rain)
        71, 73, 75, 77 -> str(R.string.wx_snow)
        80, 81, 82 -> str(R.string.wx_showers)
        85, 86 -> str(R.string.wx_snow_showers)
        95, 96, 99 -> str(R.string.wx_thunder)
        else -> "—"
    }

    private fun get(url: String): JSONObject? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        try {
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
