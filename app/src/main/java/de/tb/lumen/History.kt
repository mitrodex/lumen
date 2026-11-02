package de.tb.lumen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ein Eintrag „Zuletzt geschaut“ – [artFile] liegt im App-Speicher (oder fehlt). */
data class HistoryEntry(val info: NowPlayingInfo, val artFile: String?)

/**
 * Verlauf pro App für das Favoriten-Popup: je App die letzten [PER_APP] Inhalte, neueste zuerst.
 * Bei Serien zählt nur die zuletzt gesehene Folge (wie „Weiterschauen“ bei Netflix & Co.).
 */
object History {
    private const val PER_APP = 4
    private const val FILE = "history.json"

    @Synchronized
    fun load(context: Context): Map<String, List<HistoryEntry>> = read(context).groupBy { it.info.packageName }

    fun loadArt(context: Context, entry: HistoryEntry): Bitmap? =
        entry.artFile?.let { File(context.filesDir, it) }?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Aufnehmen bzw. denselben Inhalt aktualisieren (zweiter Aufruf mit TMDB-Daten). */
    @Synchronized
    fun add(context: Context, info: NowPlayingInfo, art: Bitmap?) {
        val all = read(context).toMutableList()
        val key = keyOf(info)
        val old = all.firstOrNull { keyOf(it.info) == key }
        all.removeAll { keyOf(it.info) == key }

        val artName = "hist_${key.hashCode().toUInt().toString(16)}.jpg"
        val artFile = File(context.filesDir, artName)
        val keepArt = when {
            art != null -> runCatching { artFile.outputStream().use { art.compress(Bitmap.CompressFormat.JPEG, 85, it) } }.isSuccess
            // Folgebild der vorigen Folge passt nicht mehr – nur bei gleichem Titel behalten.
            old?.artFile != null && old.info.title == info.title -> true
            else -> { artFile.delete(); false }
        }
        all.add(0, HistoryEntry(info, if (keepArt) artName else null))

        // Pro App kürzen, Bilder der rausgefallenen Einträge löschen.
        val kept = mutableListOf<HistoryEntry>()
        for ((_, list) in all.groupBy { it.info.packageName }) {
            kept += list.take(PER_APP)
            list.drop(PER_APP).forEach { e -> e.artFile?.let { File(context.filesDir, it).delete() } }
        }
        write(context, kept.sortedByDescending { it.info.time })
    }

    /** Serie: eine Zeile pro Serie; sonst pro Titel. */
    private fun keyOf(i: NowPlayingInfo) =
        if (i.isSeries && i.subtitle != null) "${i.packageName}|s|${i.subtitle}" else "${i.packageName}|t|${i.title}"

    private fun read(context: Context): List<HistoryEntry> = runCatching {
        val a = JSONArray(File(context.filesDir, FILE).readText())
        List(a.length()) { idx ->
            val o = a.getJSONObject(idx)
            HistoryEntry(
                NowPlayingInfo(
                    packageName = o.getString("pkg"),
                    title = o.getString("title"),
                    subtitle = o.optString("sub").ifEmpty { null },
                    time = o.getLong("time"),
                    isSeries = o.optBoolean("series"),
                    season = o.optInt("season", -1).takeIf { it >= 0 },
                    episode = o.optInt("episode", -1).takeIf { it >= 0 },
                    mediaId = o.optString("media").ifEmpty { null },
                ),
                o.optString("art").ifEmpty { null },
            )
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, list: List<HistoryEntry>) {
        val a = JSONArray()
        for (e in list) {
            val i = e.info
            a.put(
                JSONObject()
                    .put("pkg", i.packageName).put("title", i.title).put("sub", i.subtitle ?: "")
                    .put("time", i.time).put("series", i.isSeries)
                    .put("season", i.season ?: -1).put("episode", i.episode ?: -1)
                    .put("media", i.mediaId ?: "").put("art", e.artFile ?: ""),
            )
        }
        runCatching { File(context.filesDir, FILE).writeText(a.toString()) }
    }
}
