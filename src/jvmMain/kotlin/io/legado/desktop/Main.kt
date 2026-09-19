package io.legado.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.legado.desktop.ui.AppShell
import io.legado.desktop.ui.theme.LegadoTheme

fun main() = application {
    val windowState = rememberWindowState(
        width = 1100.dp,
        height = 750.dp
    )

    var darkTheme by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Legado (阅读) - Windows Native"
    ) {
        LegadoTheme(darkTheme = darkTheme) {
            AppShell(
                darkTheme = darkTheme,
                onToggleTheme = { darkTheme = !darkTheme }
            )
        }
    }
}
