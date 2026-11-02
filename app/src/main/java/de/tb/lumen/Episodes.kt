package de.tb.lumen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class EpisodeMatch(val season: Int, val episode: Int, val still: Bitmap?)

/**
 * Schlägt zu „Serie + Folgentitel“ Staffel, Folge und Standbild bei TMDB nach – unabhängig von
 * der App, die abspielt. Deutsche Titel zuerst, dann englische. Ohne Schlüssel ([Config.tmdbKey])
 * passiert nichts. Netzwerk: nur aus einem Hintergrund-Thread aufrufen.
 */
object Episodes {
    private const val API = "https://api.themoviedb.org/3"

    /** Mit [season]/[episode] (meldet z. B. Disney+) wird direkt diese Folge geholt, sonst per Titel gesucht. */
    fun lookup(key: String, series: String, episodeTitle: String, season: Int? = null, episode: Int? = null): EpisodeMatch? {
        if (key.isBlank()) return null
        val api = Tmdb(key)
        val q = URLEncoder.encode(series, "UTF-8")
        val show = api.get("$API/search/tv?query=$q&language=de-DE")?.optJSONArray("results")?.optJSONObject(0) ?: return null
        val id = show.getInt("id")
        if (season != null && episode != null) {
            val e = api.get("$API/tv/$id/season/$season/episode/$episode?language=de-DE") ?: return EpisodeMatch(season, episode, null)
            return EpisodeMatch(season, episode, stillOf(e))
        }
        val seasons = api.get("$API/tv/$id?language=de-DE")?.optJSONArray("seasons") ?: return null
        // Reguläre Staffeln zuerst, Specials (Staffel 0) zuletzt.
        val numbers = (0 until seasons.length()).map { seasons.getJSONObject(it).getInt("season_number") }
            .sortedBy { if (it == 0) Int.MAX_VALUE else it }
        val want = normTitle(episodeTitle)
        for (lang in listOf("de-DE", "en-US")) {
            for (n in numbers) {
                val eps = api.get("$API/tv/$id/season/$n?language=$lang")?.optJSONArray("episodes") ?: continue
                for (i in 0 until eps.length()) {
                    val e = eps.getJSONObject(i)
                    if (normTitle(e.optString("name")) != want) continue
                    return EpisodeMatch(n, e.getInt("episode_number"), stillOf(e))
                }
            }
        }
        return null
    }

    private fun stillOf(e: JSONObject): Bitmap? =
        e.optString("still_path").takeIf { it.startsWith("/") }?.let { image("https://image.tmdb.org/t/p/w300$it") }

    private class Tmdb(private val key: String) {
        /** v3-Schlüssel als Parameter, v4-Lesetoken (lang) als Bearer. */
        private val bearer = key.length > 40

        fun get(url: String): JSONObject? = runCatching {
            val withKey = if (bearer) url else url + (if ('?' in url) "&" else "?") + "api_key=$key"
            val conn = URL(withKey).openConnection() as HttpURLConnection
            if (bearer) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            try {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun image(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
