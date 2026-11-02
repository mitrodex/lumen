package de.tb.lumen.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Hintergrund mit langsam wandernden Farbflächen (wie der Bildschirmschoner). Wird in kleiner
 * Auflösung gemalt und hochskaliert; die Zeit wird nur in der Zeichenphase gelesen, die übrige
 * Oberfläche wird also nicht neu aufgebaut. ~15 Bilder/s, pausiert, solange Lumen unsichtbar ist.
 */
@Composable
fun AnimatedWallpaper(phase: DayPhase, dark: Boolean) {
    val bitmap = remember { Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888) }
    val canvas = remember { AndroidCanvas(bitmap) }
    val image = remember { bitmap.asImageBitmap() }
    var seconds by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                seconds = (SystemClock.elapsedRealtime() - start) / 1000f
                delay(66)
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        Wallpaper.paint(canvas, bitmap.width, bitmap.height, phase, dark, drift = seconds + 1f)
        bitmap.prepareToDraw()
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
