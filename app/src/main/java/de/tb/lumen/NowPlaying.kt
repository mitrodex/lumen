package de.tb.lumen

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Zuletzt gespielter Inhalt einer App (aus deren Media-Session). */
data class NowPlayingInfo(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val time: Long,
    /** [subtitle] ist der Serienname (z. B. Anime bei Crunchyroll) → im Widget als Überschrift. */
    val isSeries: Boolean = false,
    val season: Int? = null,
    val episode: Int? = null,
    /** MEDIA_ID der App – damit springt „Weiterschauen“ per Direktlink in genau diese Folge. */
    val mediaId: String? = null,
) {
    /** „S3 F12“, wenn bekannt. */
    val episodeLabel: String? get() = if (season != null && episode != null) "S$season F$episode" else null
}

/**
 * Speichert, was zuletzt lief – Titel/Untertitel in den Prefs, das Bild als Datei. Der
 * Direkt-Link in den Player (sessionActivity) lässt sich nicht speichern und lebt nur im Speicher.
 */
object NowPlaying {
    /** Inhalte mitschreiben (MediaWatcher) – für das „Zuletzt geschaut“-Popup der Favoriten. */
    const val CAPTURE = true

    /**
     * „Weiterschauen“-Widget auf der Startseite – vorerst aus, war zu unzuverlässig
     * (an seiner Stelle steht der Kalender). Auf true setzen, um es zurückzuholen.
     */
    const val ENABLED = false

    private const val KEY_PKG = "np_pkg"
    private const val KEY_TITLE = "np_title"
    private const val KEY_SUB = "np_sub"
    private const val KEY_SERIES = "np_series"
    private const val KEY_SEASON = "np_season"
    private const val KEY_EPISODE = "np_episode"
    private const val KEY_MEDIA = "np_media"

    /** Direktlinks in den Player, je App ({id} = MEDIA_ID aus der Media-Session). */
    private val DEEP_LINKS = mapOf(
        "com.crunchyroll.crunchyroid" to "crunchyroll://episode/{id}",
        // Detailseite mit „Fortsetzen“ (Prime öffnet per Link nicht direkt den Player).
        PrimeVideo.PACKAGE to "https://app.primevideo.com/detail?gti={id}",
        // Serien-/Filmseite (ID per JustWatch, siehe DisneyPlus).
        DisneyPlus.PACKAGE to "https://www.disneyplus.com/browse/entity-{id}",
    )
    private const val KEY_TIME = "np_time"
    const val KEY_LAST_LAUNCHED = "last_launched"

    /** Lässt sich genau dieser Inhalt per Direktlink öffnen (nicht nur die App)? */
    fun canDeepLink(info: NowPlayingInfo) = info.mediaId != null && info.packageName in DEEP_LINKS

    @Volatile
    private var resumeIntent: Pair<String, PendingIntent>? = null

    fun artFile(context: Context) = File(context.filesDir, "now_playing.jpg")

    fun save(context: Context, info: NowPlayingInfo, art: Bitmap?, sessionActivity: PendingIntent?) {
        resumeIntent = sessionActivity?.let { info.packageName to it }
        History.add(context, info, art)
        val file = artFile(context)
        if (art != null) {
            runCatching { file.outputStream().use { art.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        } else {
            file.delete()
        }
        // Zuletzt geschrieben, damit der Model-Listener das neue Bild schon vorfindet.
        Appearance.prefs(context).edit()
            .putString(KEY_PKG, info.packageName)
            .putString(KEY_TITLE, info.title)
            .putString(KEY_SUB, info.subtitle)
            .putBoolean(KEY_SERIES, info.isSeries)
            .putInt(KEY_SEASON, info.season ?: -1)
            .putInt(KEY_EPISODE, info.episode ?: -1)
            .putString(KEY_MEDIA, info.mediaId)
            .putLong(KEY_TIME, info.time)
            .putString(KEY_LAST_LAUNCHED, info.packageName)
            .apply()
    }

    fun load(context: Context): NowPlayingInfo? {
        val p = Appearance.prefs(context)
        val pkg = p.getString(KEY_PKG, null) ?: return null
        val title = p.getString(KEY_TITLE, null) ?: return null
        return NowPlayingInfo(
            pkg, title, p.getString(KEY_SUB, null), p.getLong(KEY_TIME, 0), p.getBoolean(KEY_SERIES, false),
            p.getInt(KEY_SEASON, -1).takeIf { it >= 0 }, p.getInt(KEY_EPISODE, -1).takeIf { it >= 0 },
            p.getString(KEY_MEDIA, null),
        )
    }

    fun loadArt(context: Context): Bitmap? =
        artFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /**
     * Springt direkt in den Player zurück: per Direktlink der App (überlebt Neustarts), sonst
     * über den Link der Session, solange die noch lebt.
     */
    fun resume(context: Context, info: NowPlayingInfo?, packageName: String, allowSession: Boolean = true): Boolean {
        if (info != null && info.packageName == packageName && info.mediaId != null) {
            val template = DEEP_LINKS[packageName]
            if (template != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(template.replace("{id}", Uri.encode(info.mediaId))))
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isSuccess) return true
            }
        }
        if (!allowSession) return false
        val (pkg, intent) = resumeIntent ?: return false
        if (pkg != packageName) return false
        return runCatching { intent.send() }.isSuccess
    }

    fun isKey(key: String?) = key == KEY_PKG || key == KEY_TITLE || key == KEY_TIME
}
