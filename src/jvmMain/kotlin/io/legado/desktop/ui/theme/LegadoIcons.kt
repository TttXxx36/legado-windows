package io.legado.desktop.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LegadoIcons {

    private fun buildIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathBuilder = pathBuilder
            )
        }.build()
    }

    val Book: ImageVector by lazy {
        buildIcon("Book") {
            moveTo(18f, 2f)
            lineTo(6f, 2f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(4f)
            curveTo(20f, 2.9f, 19.1f, 2f, 18f, 2f)
            close()
            moveTo(6f, 4f)
            horizontalLineTo(11f)
            verticalLineTo(12f)
            lineTo(8.5f, 10.5f)
            lineTo(6f, 12f)
            verticalLineTo(4f)
            close()
        }
    }

    val Explore: ImageVector by lazy {
        buildIcon("Explore") {
            moveTo(12f, 10.9f)
            curveTo(11.39f, 10.9f, 10.9f, 11.39f, 10.9f, 12f)
            curveTo(10.9f, 12.61f, 11.39f, 13.1f, 12f, 13.1f)
            curveTo(12.61f, 13.1f, 13.1f, 12.61f, 13.1f, 12f)
            curveTo(13.1f, 11.39f, 12.61f, 10.9f, 12f, 10.9f)
            close()
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(14.19f, 14.19f)
            lineTo(6f, 18f)
            lineTo(9.81f, 9.81f)
            lineTo(18f, 6f)
            lineTo(14.19f, 14.19f)
            close()
        }
    }

    val LibraryBooks: ImageVector by lazy {
        buildIcon("LibraryBooks") {
            moveTo(4f, 6f)
            horizontalLineTo(2f)
            verticalLineTo(20f)
            curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            close()
            moveTo(20f, 2f)
            horizontalLineTo(8f)
            curveTo(6.9f, 2f, 6f, 2.9f, 6f, 4f)
            verticalLineTo(16f)
            curveTo(6f, 17.1f, 6.9f, 18f, 8f, 18f)
            horizontalLineTo(20f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
            moveTo(19f, 11f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(19f)
            verticalLineTo(11f)
            close()
            moveTo(19f, 7f)
            horizontalLineTo(9f)
            verticalLineTo(5f)
            horizontalLineTo(19f)
            verticalLineTo(7f)
            close()
        }
    }

    val Settings: ImageVector by lazy {
        buildIcon("Settings") {
            moveTo(19.14f, 12.94f)
            curveTo(19.17f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.17f, 11.36f, 19.13f, 11.06f)
            lineTo(21.26f, 9.4f)
            curveTo(21.45f, 9.25f, 21.51f, 8.97f, 21.39f, 8.76f)
            lineTo(19.39f, 5.29f)
            curveTo(19.27f, 5.08f, 19.01f, 4.99f, 18.79f, 5.08f)
            lineTo(16.27f, 6.09f)
            curveTo(15.75f, 5.69f, 15.19f, 5.37f, 14.58f, 5.12f)
            lineTo(14.2f, 2.45f)
            curveTo(14.16f, 2.2f, 13.95f, 2f, 13.7f, 2f)
            horizontalLineTo(9.7f)
            curveTo(9.45f, 2f, 9.24f, 2.2f, 9.2f, 2.45f)
            lineTo(8.82f, 5.12f)
            curveTo(8.21f, 5.37f, 7.65f, 5.69f, 7.13f, 6.09f)
            lineTo(4.61f, 5.08f)
            curveTo(4.39f, 4.99f, 4.13f, 5.08f, 4.01f, 5.29f)
            lineTo(2.01f, 8.76f)
            curveTo(1.89f, 8.97f, 1.95f, 9.25f, 2.14f, 9.4f)
            lineTo(4.27f, 11.06f)
            curveTo(4.23f, 11.36f, 4.2f, 11.68f, 4.2f, 12f)
            curveTo(4.2f, 12.33f, 4.23f, 12.64f, 4.27f, 12.94f)
            lineTo(2.14f, 14.6f)
            curveTo(1.95f, 14.75f, 1.89f, 15.03f, 2.01f, 15.24f)
            lineTo(4.01f, 18.71f)
            curveTo(4.13f, 18.92f, 4.39f, 19.01f, 4.61f, 18.92f)
            lineTo(7.13f, 17.91f)
            curveTo(7.65f, 18.31f, 8.21f, 18.63f, 8.82f, 18.88f)
            lineTo(9.2f, 21.55f)
            curveTo(9.24f, 21.8f, 9.45f, 22f, 9.7f, 22f)
            horizontalLineTo(13.7f)
            curveTo(13.95f, 22f, 14.16f, 21.8f, 14.2f, 21.55f)
            lineTo(14.58f, 18.88f)
            curveTo(15.19f, 18.63f, 15.75f, 18.31f, 16.27f, 17.91f)
            lineTo(18.79f, 18.92f)
            curveTo(19.01f, 19.01f, 19.27f, 18.92f, 19.39f, 18.71f)
            lineTo(21.39f, 15.24f)
            curveTo(21.51f, 15.03f, 21.45f, 14.75f, 21.26f, 14.6f)
            lineTo(19.14f, 12.94f)
            close()
            moveTo(12f, 15.5f)
            curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
            curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
            curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
            curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
            close()
        }
    }

    val LightMode: ImageVector by lazy {
        buildIcon("LightMode") {
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
            moveTo(12f, 2f)
            curveTo(11.45f, 2f, 11f, 2.45f, 11f, 3f)
            verticalLineTo(5f)
            curveTo(11f, 5.55f, 11.45f, 6f, 12f, 6f)
            curveTo(12.55f, 6f, 13f, 5.55f, 13f, 5f)
            verticalLineTo(3f)
            curveTo(13f, 2.45f, 12.55f, 2f, 12f, 2f)
            close()
            moveTo(12f, 18f)
            curveTo(11.45f, 18f, 11f, 18.45f, 11f, 19f)
            verticalLineTo(21f)
            curveTo(11f, 21.55f, 11.45f, 22f, 12f, 22f)
            curveTo(12.55f, 22f, 13f, 21.55f, 13f, 21f)
            verticalLineTo(19f)
            curveTo(13f, 18.45f, 12.55f, 18f, 12f, 18f)
            close()
        }
    }

    val DarkMode: ImageVector by lazy {
        buildIcon("DarkMode") {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 11.54f, 20.96f, 11.08f, 20.9f, 10.63f)
            curveTo(19.83f, 11.5f, 18.48f, 12f, 17f, 12f)
            curveTo(13.13f, 12f, 10f, 8.87f, 10f, 5f)
            curveTo(10f, 3.52f, 10.5f, 2.17f, 11.37f, 1.1f)
            curveTo(10.92f, 1.04f, 10.46f, 1f, 10f, 1f)
            close()
        }
    }

    val MenuBook: ImageVector by lazy {
        buildIcon("MenuBook") {
            moveTo(21f, 5f)
            curveTo(19.89f, 4.65f, 18.67f, 4.5f, 17.5f, 4.5f)
            curveTo(15.55f, 4.5f, 13.45f, 4.9f, 12f, 6f)
            curveTo(10.55f, 4.9f, 8.45f, 4.5f, 6.5f, 4.5f)
            curveTo(5.33f, 4.5f, 4.11f, 4.65f, 3f, 5f)
            verticalLineTo(19.65f)
            curveTo(3f, 20.01f, 3.34f, 20.31f, 3.7f, 20.22f)
            curveTo(4.71f, 19.97f, 5.76f, 19.8f, 6.5f, 19.8f)
            curveTo(8.45f, 19.8f, 10.55f, 20.2f, 12f, 21.3f)
            curveTo(13.45f, 20.2f, 15.55f, 19.8f, 17.5f, 19.8f)
            curveTo(18.24f, 19.8f, 19.29f, 19.97f, 20.3f, 20.22f)
            curveTo(20.66f, 20.31f, 21f, 20.01f, 21f, 19.65f)
            verticalLineTo(5f)
            close()
            moveTo(19f, 18.5f)
            curveTo(18.51f, 18.45f, 18.01f, 18.4f, 17.5f, 18.4f)
            curveTo(15.74f, 18.4f, 13.91f, 18.84f, 13f, 19.5f)
            verticalLineTo(7.5f)
            curveTo(13.91f, 6.84f, 15.74f, 6.4f, 17.5f, 6.4f)
            curveTo(18.01f, 6.4f, 18.51f, 6.45f, 19f, 6.5f)
            verticalLineTo(18.5f)
            close()
        }
    }

    val Download: ImageVector by lazy {
        buildIcon("Download") {
            moveTo(19f, 9f)
            horizontalLineTo(15f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            lineTo(12f, 16f)
            lineTo(19f, 9f)
            close()
            moveTo(5f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            horizontalLineTo(5f)
            close()
        }
    }

    val CloudUpload: ImageVector by lazy {
        buildIcon("CloudUpload") {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
            moveTo(14f, 13f)
            verticalLineTo(17f)
            horizontalLineTo(10f)
            verticalLineTo(13f)
            horizontalLineTo(7f)
            lineTo(12f, 8f)
            lineTo(17f, 13f)
            horizontalLineTo(14f)
            close()
        }
    }

    val CloudDownload: ImageVector by lazy {
        buildIcon("CloudDownload") {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
            moveTo(17f, 13f)
            lineTo(12f, 18f)
            lineTo(7f, 13f)
            horizontalLineTo(10f)
            verticalLineTo(9f)
            horizontalLineTo(14f)
            verticalLineTo(13f)
            horizontalLineTo(17f)
            close()
        }
    }

    val Language: ImageVector by lazy {
        buildIcon("Language") {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(9.22f, 20f, 6.82f, 18.36f, 5.72f, 16f)
            horizontalLineTo(8.65f)
            curveTo(9.02f, 17.79f, 9.88f, 19.34f, 12f, 20f)
            close()
        }
    }

    val Bookmark: ImageVector by lazy {
        buildIcon("Bookmark") {
            moveTo(17f, 3f)
            horizontalLineTo(7f)
            curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
            verticalLineTo(21f)
            lineTo(12f, 18f)
            lineTo(19f, 21f)
            verticalLineTo(5f)
            curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
            close()
        }
    }

    val BookmarkBorder: ImageVector by lazy {
        buildIcon("BookmarkBorder") {
            moveTo(17f, 3f)
            horizontalLineTo(7f)
            curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
            verticalLineTo(21f)
            lineTo(12f, 18f)
            lineTo(19f, 21f)
            verticalLineTo(5f)
            curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
            close()
            moveTo(17f, 18f)
            lineTo(12f, 15.82f)
            lineTo(7f, 18f)
            verticalLineTo(5f)
            horizontalLineTo(17f)
            verticalLineTo(18f)
            close()
        }
    }

    val BookmarkAdd: ImageVector by lazy {
        buildIcon("BookmarkAdd") {
            moveTo(17f, 3f)
            horizontalLineTo(7f)
            curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
            verticalLineTo(21f)
            lineTo(12f, 18f)
            lineTo(19f, 21f)
            verticalLineTo(5f)
            curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
            close()
            moveTo(13f, 11f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(9f)
            verticalLineTo(11f)
            horizontalLineTo(7f)
            verticalLineTo(9f)
            horizontalLineTo(9f)
            verticalLineTo(7f)
            horizontalLineTo(11f)
            verticalLineTo(9f)
            horizontalLineTo(13f)
            verticalLineTo(11f)
            close()
        }
    }

    val VolumeUp: ImageVector by lazy {
        buildIcon("VolumeUp") {
            moveTo(3f, 9f)
            verticalLineTo(15f)
            horizontalLineTo(7f)
            lineTo(12f, 20f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(16.5f, 12f)
            curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
            verticalLineTo(16.02f)
            curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
            close()
        }
    }

    val Headphones: ImageVector by lazy {
        buildIcon("Headphones") {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            verticalLineTo(19f)
            curveTo(3f, 20.66f, 4.34f, 22f, 6f, 22f)
            horizontalLineTo(8f)
            verticalLineTo(14f)
            horizontalLineTo(5f)
            verticalLineTo(12f)
            curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
            curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
            verticalLineTo(14f)
            horizontalLineTo(16f)
            verticalLineTo(22f)
            horizontalLineTo(18f)
            curveTo(19.66f, 22f, 21f, 20.66f, 21f, 19f)
            verticalLineTo(12f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()
        }
    }

    val PhotoLibrary: ImageVector by lazy {
        buildIcon("PhotoLibrary") {
            moveTo(22f, 16f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            horizontalLineTo(8f)
            curveTo(6.9f, 2f, 6f, 2.9f, 6f, 4f)
            verticalLineTo(16f)
            curveTo(6f, 17.1f, 6.9f, 18f, 8f, 18f)
            horizontalLineTo(20f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            close()
            moveTo(2f, 6f)
            verticalLineTo(20f)
            curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            horizontalLineTo(2f)
            close()
        }
    }

    val ViewAgenda: ImageVector by lazy {
        buildIcon("ViewAgenda") {
            moveTo(20f, 3f)
            horizontalLineTo(4f)
            curveTo(3.45f, 3f, 3f, 3.45f, 3f, 4f)
            verticalLineTo(10f)
            curveTo(3f, 10.55f, 3.45f, 11f, 4f, 11f)
            horizontalLineTo(20f)
            curveTo(20.55f, 11f, 21f, 10.55f, 21f, 10f)
            verticalLineTo(4f)
            curveTo(21f, 3.45f, 20.55f, 3f, 20f, 3f)
            close()
            moveTo(20f, 13f)
            horizontalLineTo(4f)
            curveTo(3.45f, 13f, 3f, 13.45f, 3f, 14f)
            verticalLineTo(20f)
            curveTo(3f, 20.55f, 3.45f, 21f, 4f, 21f)
            horizontalLineTo(20f)
            curveTo(20.55f, 21f, 21f, 20.55f, 21f, 20f)
            verticalLineTo(14f)
            curveTo(21f, 13.45f, 20.55f, 13f, 20f, 13f)
            close()
        }
    }

    val AutoStories: ImageVector by lazy {
        buildIcon("AutoStories") {
            moveTo(19f, 1f)
            horizontalLineTo(5f)
            curveTo(3.9f, 1f, 3f, 1.9f, 3f, 3f)
            verticalLineTo(21f)
            lineTo(12f, 17f)
            lineTo(21f, 21f)
            verticalLineTo(3f)
            curveTo(21f, 1.9f, 20.1f, 1f, 19f, 1f)
            close()
        }
    }

    val Tune: ImageVector by lazy {
        buildIcon("Tune") {
            moveTo(3f, 17f)
            verticalLineTo(19f)
            horizontalLineTo(9f)
            verticalLineTo(17f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineTo(19f)
            horizontalLineTo(21f)
            verticalLineTo(17f)
            horizontalLineTo(13f)
            verticalLineTo(15f)
            horizontalLineTo(11f)
            verticalLineTo(21f)
            horizontalLineTo(13f)
            close()
        }
    }

    val Pause: ImageVector by lazy {
        buildIcon("Pause") {
            moveTo(6f, 19f)
            horizontalLineTo(10f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            close()
            moveTo(14f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            close()
        }
    }

    val NavigateBefore: ImageVector by lazy {
        buildIcon("NavigateBefore") {
            moveTo(15.41f, 7.41f)
            lineTo(14f, 6f)
            lineTo(8f, 12f)
            lineTo(14f, 18f)
            lineTo(15.41f, 16.59f)
            lineTo(10.83f, 12f)
            close()
        }
    }

    val NavigateNext: ImageVector by lazy {
        buildIcon("NavigateNext") {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineTo(16f, 12f)
            close()
        }
    }

    val Image: ImageVector by lazy {
        buildIcon("Image") {
            moveTo(21f, 19f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            close()
            moveTo(8.5f, 13.5f)
            lineTo(11f, 16.51f)
            lineTo(14.5f, 12f)
            lineTo(19f, 18f)
            horizontalLineTo(5f)
            lineTo(8.5f, 13.5f)
            close()
        }
    }

    val ArrowBack: ImageVector by lazy {
        buildIcon("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineTo(11f)
            close()
        }
    }

    val Menu: ImageVector by lazy {
        buildIcon("Menu") {
            moveTo(3f, 18f)
            horizontalLineTo(21f)
            verticalLineTo(16f)
            horizontalLineTo(3f)
            verticalLineTo(18f)
            close()
            moveTo(3f, 13f)
            horizontalLineTo(21f)
            verticalLineTo(11f)
            horizontalLineTo(3f)
            verticalLineTo(13f)
            close()
            moveTo(3f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(21f)
            verticalLineTo(6f)
            horizontalLineTo(3f)
            close()
        }
    }

    val Close: ImageVector by lazy {
        buildIcon("Close") {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }

    val Add: ImageVector by lazy {
        buildIcon("Add") {
            moveTo(19f, 13f)
            horizontalLineTo(13f)
            verticalLineTo(19f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(11f)
            verticalLineTo(5f)
            horizontalLineTo(13f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            verticalLineTo(13f)
            close()
        }
    }

    val Delete: ImageVector by lazy {
        buildIcon("Delete") {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            horizontalLineTo(16f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            close()
            moveTo(19f, 4f)
            horizontalLineTo(15.5f)
            lineTo(14.5f, 3f)
            horizontalLineTo(9.5f)
            lineTo(8.5f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(19f)
            verticalLineTo(4f)
            close()
        }
    }

    val Clear: ImageVector by lazy {
        Close
    }

    val Search: ImageVector by lazy {
        buildIcon("Search") {
            moveTo(15.5f, 14f)
            horizontalLineTo(14.71f)
            lineTo(14.43f, 13.73f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
            curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineTo(14f, 14.71f)
            verticalLineTo(15.5f)
            lineTo(19f, 20.49f)
            lineTo(20.49f, 19f)
            lineTo(15.5f, 14f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
            curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
            curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        buildIcon("Check") {
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineTo(3.41f, 13.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineTo(19.59f, 5.59f)
            lineTo(9f, 16.17f)
            close()
        }
    }

    val PlayArrow: ImageVector by lazy {
        buildIcon("PlayArrow") {
            moveTo(8f, 5f)
            verticalLineTo(19f)
            lineTo(19f, 12f)
            lineTo(8f, 5f)
            close()
        }
    }
}
