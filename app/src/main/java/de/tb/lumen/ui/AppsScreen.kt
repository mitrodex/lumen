package de.tb.lumen.ui

import de.tb.lumen.R
import de.tb.lumen.str

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.LauncherModel

/** App-Raster; mit [search] zusätzlich ein Suchfeld, das beim Öffnen den Fokus bekommt. */
@Composable
fun AppsScreen(model: LauncherModel, search: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var keyboard by remember { mutableStateOf(false) }
    val q = if (search) query.trim() else ""
    val apps = model.apps.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
    val searchFocus = remember { FocusRequester() }

    // Zurück schließt zuerst die Tastatur und lässt den Fokus aufs Suchfeld zurückfallen.
    InterceptBack(keyboard) {
        keyboard = false
        runCatching { searchFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        if (search) {
            // Kein echtes Textfeld: Die (eigene) Tastatur öffnet sich erst bei OK.
            SearchField(
                query,
                Modifier.align(Alignment.CenterHorizontally).width(520.dp).focusRequester(searchFocus),
            ) { keyboard = true }
            androidx.compose.animation.AnimatedVisibility(
                visible = keyboard,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
            ) {
                GlassKeyboard(
                    onChar = { query += it },
                    onDelete = { query = query.dropLast(1) },
                    onDone = {
                        keyboard = false
                        runCatching { searchFocus.requestFocus() }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        Row(Modifier.padding(start = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Caption(if (search && q.isNotEmpty()) str(R.string.apps_results, apps.size) else str(R.string.apps_all))
            Spacer(Modifier.width(14.dp))
            LText(str(R.string.apps_favorite_hint), 12.sp, alpha = 0.55f)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 24.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    art = model.art[app.packageName],
                    isFavorite = app.packageName in model.favorites,
                    onClick = { model.launch(app) },
                    onToggleFavorite = { model.toggleFavorite(app.packageName) },
                )
            }
        }
    }

    if (search) {
        LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
    }
}

/** Suchfeld als Glas-Pille; zeigt nur den Text, OK öffnet die Tastatur. */
@Composable
private fun SearchField(query: String, modifier: Modifier, onOpen: () -> Unit) {
    GlassTile(onClick = onOpen, modifier = modifier.height(56.dp), shape = RoundedCornerShape(28.dp), focusedScale = 1.03f) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(LumenIcons.Search, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.8f)))
            Spacer(Modifier.width(12.dp))
            if (query.isEmpty()) {
                LText(if (focused) str(R.string.search_press_ok) else str(R.string.search_apps), 19.sp, alpha = 0.55f)
            } else {
                LText(query, 19.sp, weight = FontWeight.Medium)
                // Schreibmarke
                Box(Modifier.padding(start = 2.dp).width(2.dp).height(22.dp).background(Color.White.copy(alpha = 0.8f)))
            }
        }
    }
}