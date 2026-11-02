package de.tb.lumen

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/** Ein „Weiterschauen“-Eintrag einer App samt Klick-Link genau in diesen Inhalt. */
data class ContinueRec(
    val key: String,
    val packageName: String,
    val head: String,
    val sub: String?,
    val art: Bitmap?,
    val intent: PendingIntent,
    val rank: Int,
)

/**
 * „Weiterschauen“ aus den Fire-TV-Empfehlungen: Apps wie Netflix legen ihre Zeilen für die
 * Amazon-Startseite als Benachrichtigungen (category=recommendation) ab – mit Bild und einem Link,
 * der direkt in den Inhalt führt. Der [MediaWatcher] liest sie als Benachrichtigungs-Leser mit;
 * hier landen nur die Gruppen fürs Weiterschauen (z. B. Netflix „continueWatching“).
 */
object Recommendations {
    private val recs = ConcurrentHashMap<String, ContinueRec>()
    private val main = Handler(Looper.getMainLooper())

    /** Wird (auf dem Main-Thread) nach jeder Änderung aufgerufen. */
    @Volatile
    var onChange: (() -> Unit)? = null

    fun forPackage(pkg: String): List<ContinueRec> =
        recs.values.filter { it.packageName == pkg }.sortedBy { it.rank }

    fun posted(context: Context, sbn: StatusBarNotification) {
        val rec = parse(context, sbn)
        val changed = if (rec != null) recs.put(sbn.key, rec) != rec else recs.remove(sbn.key) != null
        if (changed) notifyChange()
    }

    fun removed(sbn: StatusBarNotification) {
        if (recs.remove(sbn.key) != null) notifyChange()
    }

    private fun notifyChange() = main.post { onChange?.invoke() }

    private fun parse(context: Context, sbn: StatusBarNotification): ContinueRec? {
        val n = sbn.notification
        if (n.category != Notification.CATEGORY_RECOMMENDATION) return null
        val group = n.group.orEmpty()
        if (!CONTINUE.containsMatchIn(group)) return null
        val intent = n.contentIntent ?: return null
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = x.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        val (head, sub) = splitRecommendation(title, text)
        if (head.isEmpty()) return null

        // Android entfernt große Bilder aus Benachrichtigungen („reduced.images“) – dann das
        // Hintergrundbild nehmen, das die App per eigener Adresse bereitstellt (Netflix).
        val art = runCatching {
            (x.getParcelable<Icon>(Notification.EXTRA_LARGE_ICON)?.loadDrawable(context) as? BitmapDrawable)?.bitmap
        }.getOrNull() ?: x.getString(EXTRA_BACKGROUND_URI)?.let { uri ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull()
        }
        val rank = x.getInt("com.amazon.extra.RANK", 100)
        return ContinueRec(sbn.key, sbn.packageName, head, sub, art, intent, rank)
    }

    private const val EXTRA_BACKGROUND_URI = "android.backgroundImageUri"
    private val CONTINUE = Regex("continue|resume|weiter", RegexOption.IGNORE_CASE)
}
