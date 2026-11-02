package de.tb.lumen.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.tb.lumen.AppArt
import de.tb.lumen.AppEntry

val TileShape = RoundedCornerShape(16.dp)

/** Werte für hellen bzw. dunklen Glas-Look. */
@Immutable
data class GlassStyle(
    val shade: Float,
    val fill: Float,
    val fillFocused: Float,
    val edgeTop: Float,
    val edgeBottom: Float,
    val sheen: Float,
)

val LightGlass = GlassStyle(shade = 0f, fill = 0.13f, fillFocused = 0.26f, edgeTop = 0.48f, edgeBottom = 0.08f, sheen = 0.13f)
val DarkGlass = GlassStyle(shade = 0.26f, fill = 0.06f, fillFocused = 0.16f, edgeTop = 0.28f, edgeBottom = 0.04f, sheen = 0.06f)
val LocalGlass = staticCompositionLocalOf { LightGlass }
val LocalDarkMode = staticCompositionLocalOf { false }

/** Aktuelles Hintergrundbild (Tageszeit + Dunkelmodus) – für das Milchglas. */
val LocalWallpaper = staticCompositionLocalOf { Wallpaper.get(DayPhase.Day, false) }

/** Text auf hellen Flächen (ausgewählter Tab, aktive Schalter). */
val InkColor = Color(0xFF1C1C1E)
val SystemBlue = Color(0xFF0A84FF)
val SystemRed = Color(0xFFFF453A)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Liquid Glass ohne Echtzeit-Unschärfe: halbtransparente Fläche, oberer Lichtreflex und eine
 * Kante, die oben links leicht glänzt und nach unten rechts ausläuft. [highlight] (0..1) = Fokus:
 * Fläche heller, Kante fast reinweiß.
 *
 * Schneidet den Inhalt bewusst NICHT ab – sonst werden vergrößerte, fokussierte Kinder und ihre
 * Schatten hart gekappt. Wer abschneiden muss (Banner), hängt selbst ein clip an.
 */
fun Modifier.glass(shape: Shape, style: GlassStyle, highlight: () -> Float = { 0f }): Modifier = this
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val sheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = style.sheen),
            0.5f to Color.Transparent,
        )
        val shade = Color.Black.copy(alpha = style.shade)
        val stroke = Stroke(width = 1.dp.toPx())
        val end = Offset(size.width, size.height)
        onDrawWithContent {
            val h = highlight()
            if (style.shade > 0f) drawOutline(outline, shade)
            drawOutline(outline, Color.White.copy(alpha = mix(style.fill, style.fillFocused, h)))
            drawOutline(outline, sheen)
            drawContent()
            val edge = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = mix(style.edgeTop, 0.95f, h)),
                    Color.White.copy(alpha = mix(style.edgeBottom, 0.85f, h)),
                ),
                start = Offset.Zero,
                end = end,
            )
            drawOutline(outline, edge, style = stroke)
        }
    }

/**
 * „Milchglas“ für Flächen, die über Inhalten liegen: zeichnet den (vorab weichgezeichneten)
 * Hintergrund an genau dieser Bildschirmposition, beschnitten auf [shape]. Wirkt wie
 * Unschärfe, kostet aber nur ein Bild-Zeichnen. Vor [glass] verwenden.
 */
@Composable
fun Modifier.frosted(shape: Shape): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val view = LocalView.current
    val image = LocalWallpaper.current
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawBehind {
                clipPath(path) {
                    translate(-origin.x, -origin.y) {
                        drawImage(
                            image,
                            srcSize = IntSize(image.width, image.height),
                            dstSize = IntSize(view.width, view.height),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }
}

/** Blendet eine scrollende Reihe links und rechts weich aus, statt sie hart abzuschneiden. */
fun Modifier.fadeEdges(width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                f to Color.Black,
                1f - f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Fokussierbare Glas-Kachel. Fokus: anheben (Scale + Schatten), Fläche/Kante heller.
 * [shadow] nur bei undurchsichtigem Inhalt – sonst scheint der Schatten durchs Glas.
 */
@Composable
fun GlassTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TileShape,
    focusedScale: Float = 1.1f,
    shadow: Boolean = false,
    onKey: ((KeyEvent) -> Boolean)? = null,
    /** Fortschritt 0..1 eines einmaligen Lichtschimmers quer über die Kachel; außerhalb = keiner. */
    shimmer: (() -> Float)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val style = LocalGlass.current
    var focused by remember { mutableStateOf(false) }
    val t = animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "focus")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val v = t.value
                val s = 1f + (focusedScale - 1f) * v
                scaleX = s
                scaleY = s
                this.shape = shape
                clip = false
                if (shadow) shadowElevation = 14.dp.toPx() * v
            }
            .onFocusChanged { focused = it.isFocused }
            .then(if (onKey != null) Modifier.onKeyEvent(onKey) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glass(shape, style) { t.value }
            // Nur der Inhalt (z. B. Banner) wird auf die Form beschnitten, die Kante liegt darüber.
            .clip(shape)
            .then(if (shimmer != null) Modifier.drawWithContent { drawContent(); drawShimmer(shimmer()) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(focused)
    }
}

/** Schräges helles Lichtband, das bei [p] = 0..1 von links nach rechts über die Fläche zieht. */
private fun DrawScope.drawShimmer(p: Float) {
    if (p <= 0f || p >= 1f) return
    val band = size.width * 0.7f
    val x = -band + (size.width + 2 * band) * p
    // Wie eine Spiegelung auf Glas: helle Mitte mit dunklen Säumen – so ist der Schimmer auch
    // auf weißen Bannern (Netflix) deutlich zu sehen, nicht nur auf dunklen.
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.25f to Color.Black.copy(alpha = 0.28f),
            0.42f to Color.White.copy(alpha = 0.35f),
            0.5f to Color.White.copy(alpha = 0.95f),
            0.58f to Color.White.copy(alpha = 0.35f),
            0.75f to Color.Black.copy(alpha = 0.28f),
            1f to Color.Transparent,
            start = Offset(x - band / 2, 0f),
            end = Offset(x + band / 2, size.height * 0.6f),
        ),
    )
}

@Composable
fun LText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    alpha: Float = 1f,
    color: Color = Color.White,
    align: TextAlign = TextAlign.Start,
    tracking: TextUnit = TextUnit.Unspecified,
    /** Nur für große Schrift direkt auf dem Hintergrund – auf Glas nie. */
    shadow: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color.copy(alpha = color.alpha * alpha),
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            letterSpacing = tracking,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.16f), Offset(0f, 1f), 12f) else null,
        ),
    )
}

/** Kleine Abschnitts-Überschrift im Apple-Stil (Versalien, gesperrt). */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    LText(text.uppercase(), 12.sp, modifier, FontWeight.SemiBold, alpha = 0.7f, tracking = 1.2.sp)
}

/** Play/Pause auf einer App-Kachel schaltet den Favoriten um. */
private fun favoriteKey(onToggle: () -> Unit): (KeyEvent) -> Boolean = { e ->
    if (e.key == Key.MediaPlayPause) {
        if (e.type == KeyEventType.KeyUp) onToggle()
        true
    } else {
        false
    }
}

/**
 * App-Kachel (Banner bevorzugt). Mit [showLabel] erscheint der Name wie bei tvOS nur unter der
 * fokussierten Kachel; im Dock (wie bei iPadOS) ohne Namen.
 */
@Composable
fun AppTile(
    app: AppEntry,
    art: AppArt?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    tileModifier: Modifier = Modifier,
    showLabel: Boolean = true,
    shimmer: (() -> Float)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha = animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "label")
    Column(modifier.onFocusChanged { focused = it.hasFocus }, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassTile(
            onClick = onClick,
            modifier = tileModifier.fillMaxWidth().aspectRatio(16f / 9f),
            shadow = art?.isBanner == true,
            onKey = favoriteKey(onToggleFavorite),
            shimmer = shimmer,
        ) {
            if (art != null && art.isBanner) {
                Image(art.forMode(LocalDarkMode.current), app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (art != null) Image(art.image, null, Modifier.size(40.dp))
                    Spacer(Modifier.height(6.dp))
                    LText(app.label, 12.sp, weight = FontWeight.Medium)
                }
            }
            if (isFavorite) {
                Image(
                    LumenIcons.Star, null,
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)),
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(10.dp))
            LText(
                app.label, 13.sp,
                Modifier.fillMaxWidth().graphicsLayer { alpha = labelAlpha.value },
                FontWeight.Medium, align = TextAlign.Center,
            )
        }
    }
}
