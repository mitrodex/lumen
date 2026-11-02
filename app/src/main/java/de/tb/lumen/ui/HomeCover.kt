package de.tb.lumen.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Sofort-Abdeckung über der Amazon-Startseite, bis Lumen gezeichnet ist (meist ~0,4 s).
 *
 * - [snapshot] gesetzt (Home, während Lumen schon vorne war): zeigt genau das letzte Bild von
 *   Lumen – man sieht Lumen einfach weiter, nichts flackert.
 * - sonst: nur der Lumen-Hintergrund, ohne Uhr o. Ä., damit beim Übergang nichts springt.
 *
 * Schluckt Tasten, damit nichts „blind“ in der Amazon-Oberfläche passiert.
 * Bewusst klassische Views: im Service gibt es keinen Lifecycle für Compose.
 */
class HomeCover(context: Context, dark: Boolean, snapshot: Bitmap? = null) : FrameLayout(context) {

    init {
        background = BitmapDrawable(resources, snapshot ?: Wallpaper.bitmap(Wallpaper.phase(), dark)).apply {
            paint.isFilterBitmap = true
            paint.flags = paint.flags or Paint.FILTER_BITMAP_FLAG
        }
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = true
}
