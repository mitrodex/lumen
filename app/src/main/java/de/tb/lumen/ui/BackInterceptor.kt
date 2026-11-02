package de.tb.lumen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * Teile der Oberfläche (z. B. die Bildschirmtastatur) bekommen Zurück vor der normalen
 * Navigation. MainActivity fragt [handle] zuerst; der zuletzt registrierte gewinnt.
 */
object BackInterceptors {
    private val stack = ArrayDeque<() -> Unit>()

    fun handle(): Boolean {
        val top = stack.lastOrNull() ?: return false
        top()
        return true
    }

    internal fun push(f: () -> Unit) = stack.addLast(f)
    internal fun remove(f: () -> Unit) = stack.remove(f)
}

@Composable
fun InterceptBack(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val f: () -> Unit = { current.value() }
        if (enabled) BackInterceptors.push(f)
        onDispose { BackInterceptors.remove(f) }
    }
}
