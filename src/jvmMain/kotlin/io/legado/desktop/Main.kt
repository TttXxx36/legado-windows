package io.legado.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import io.legado.desktop.ui.AppShell
import io.legado.desktop.ui.theme.LegadoTheme

object LegadoTrayIcon : Painter() {
    override val intrinsicSize: Size
        get() = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        drawCircle(color = Color(0xFF6750A4))
        drawRect(
            color = Color.White,
            topLeft = Offset(8f, 8f),
            size = Size(16f, 16f)
        )
    }
}

fun main() = application {
    val windowState = rememberWindowState(
        width = 1100.dp,
        height = 750.dp
    )

    var darkTheme by remember { mutableStateOf(false) }
    var isWindowVisible by remember { mutableStateOf(true) }

    val trayState = rememberTrayState()

    val appIcon = try {
        useResource("icon.png") { stream ->
            BitmapPainter(loadImageBitmap(stream))
        }
    } catch (_: Exception) {
        LegadoTrayIcon
    }

    Tray(
        state = trayState,
        icon = appIcon,
        tooltip = "Legado (阅读) - Windows 原生桌面版",
        onAction = {
            isWindowVisible = true
        },
        menu = {
            Item("显示主界面", onClick = { isWindowVisible = true })
            Item("隐藏到托盘", onClick = { isWindowVisible = false })
            Separator()
            Item("退出程序", onClick = ::exitApplication)
        }
    )

    Window(
        onCloseRequest = {
            // 最小化到系统托盘，保证后台 TTS 朗读与局域网 Web 服务不中断
            isWindowVisible = false
            try {
                trayState.sendNotification(
                    Notification(
                        title = "Legado (阅读)",
                        message = "程序已最小化到系统通知区域，后台朗读与局域网服务将继续运行。"
                    )
                )
            } catch (_: Exception) {}
        },
        visible = isWindowVisible,
        state = windowState,
        icon = appIcon,
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
