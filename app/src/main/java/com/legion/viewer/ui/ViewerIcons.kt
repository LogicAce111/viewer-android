package com.legion.viewer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Viewer 的本地线性图标。统一使用接近 Windows Segoe Fluent Icons 的圆角细线风格，
 * 避免底部导航与分类入口呈现为 Android 默认实心图标。
 */
object ViewerIcons {
    val Home: ImageVector by lazy {
        lineIcon("Viewer.Home") {
            moveTo(3.5f, 10.5f)
            lineTo(12f, 3.8f)
            lineTo(20.5f, 10.5f)
            moveTo(5.5f, 9.2f)
            lineTo(5.5f, 19.2f)
            curveTo(5.5f, 19.9f, 6.1f, 20.5f, 6.8f, 20.5f)
            lineTo(17.2f, 20.5f)
            curveTo(17.9f, 20.5f, 18.5f, 19.9f, 18.5f, 19.2f)
            lineTo(18.5f, 9.2f)
            moveTo(9.4f, 20.5f)
            lineTo(9.4f, 14.7f)
            curveTo(9.4f, 14f, 10f, 13.4f, 10.7f, 13.4f)
            lineTo(13.3f, 13.4f)
            curveTo(14f, 13.4f, 14.6f, 14f, 14.6f, 14.7f)
            lineTo(14.6f, 20.5f)
        }
    }

    val Video: ImageVector by lazy {
        lineIcon("Viewer.Video") {
            moveTo(5.2f, 6.2f)
            lineTo(14.2f, 6.2f)
            curveTo(15.2f, 6.2f, 16f, 7f, 16f, 8f)
            lineTo(16f, 16f)
            curveTo(16f, 17f, 15.2f, 17.8f, 14.2f, 17.8f)
            lineTo(5.2f, 17.8f)
            curveTo(4.2f, 17.8f, 3.4f, 17f, 3.4f, 16f)
            lineTo(3.4f, 8f)
            curveTo(3.4f, 7f, 4.2f, 6.2f, 5.2f, 6.2f)
            close()
            moveTo(16f, 10f)
            lineTo(19.4f, 7.9f)
            curveTo(20f, 7.5f, 20.6f, 7.9f, 20.6f, 8.6f)
            lineTo(20.6f, 15.4f)
            curveTo(20.6f, 16.1f, 20f, 16.5f, 19.4f, 16.1f)
            lineTo(16f, 14f)
        }
    }

    val Music: ImageVector by lazy {
        lineIcon("Viewer.Music") {
            moveTo(8.8f, 17.6f)
            curveTo(8.8f, 19.1f, 7.4f, 20.3f, 5.7f, 20.3f)
            curveTo(4f, 20.3f, 2.8f, 19.4f, 2.8f, 18.1f)
            curveTo(2.8f, 16.7f, 4.2f, 15.6f, 5.9f, 15.6f)
            curveTo(7.6f, 15.6f, 8.8f, 16.4f, 8.8f, 17.6f)
            close()
            moveTo(18.4f, 15.2f)
            curveTo(18.4f, 16.7f, 17f, 17.9f, 15.3f, 17.9f)
            curveTo(13.6f, 17.9f, 12.4f, 17f, 12.4f, 15.7f)
            curveTo(12.4f, 14.3f, 13.8f, 13.2f, 15.5f, 13.2f)
            curveTo(17.2f, 13.2f, 18.4f, 14f, 18.4f, 15.2f)
            close()
            moveTo(8.8f, 17.6f)
            lineTo(8.8f, 6.5f)
            lineTo(18.4f, 4.1f)
            lineTo(18.4f, 15.2f)
            moveTo(8.8f, 9.2f)
            lineTo(18.4f, 6.8f)
        }
    }

    val Text: ImageVector by lazy {
        lineIcon("Viewer.Text") {
            moveTo(6.2f, 3.5f)
            lineTo(14.3f, 3.5f)
            lineTo(18.8f, 8f)
            lineTo(18.8f, 19.2f)
            curveTo(18.8f, 19.9f, 18.2f, 20.5f, 17.5f, 20.5f)
            lineTo(6.2f, 20.5f)
            curveTo(5.5f, 20.5f, 4.9f, 19.9f, 4.9f, 19.2f)
            lineTo(4.9f, 4.8f)
            curveTo(4.9f, 4.1f, 5.5f, 3.5f, 6.2f, 3.5f)
            close()
            moveTo(14.3f, 3.5f)
            lineTo(14.3f, 8f)
            lineTo(18.8f, 8f)
            moveTo(8.2f, 11.2f)
            lineTo(15.8f, 11.2f)
            moveTo(8.2f, 14.5f)
            lineTo(15.8f, 14.5f)
            moveTo(8.2f, 17.8f)
            lineTo(13.7f, 17.8f)
        }
    }

    val Comics: ImageVector by lazy {
        lineIcon("Viewer.Comics") {
            moveTo(5.2f, 4.5f)
            lineTo(18.8f, 4.5f)
            curveTo(19.7f, 4.5f, 20.5f, 5.3f, 20.5f, 6.2f)
            lineTo(20.5f, 17.8f)
            curveTo(20.5f, 18.7f, 19.7f, 19.5f, 18.8f, 19.5f)
            lineTo(5.2f, 19.5f)
            curveTo(4.3f, 19.5f, 3.5f, 18.7f, 3.5f, 17.8f)
            lineTo(3.5f, 6.2f)
            curveTo(3.5f, 5.3f, 4.3f, 4.5f, 5.2f, 4.5f)
            close()
            moveTo(9.5f, 9f)
            curveTo(9.5f, 10f, 8.7f, 10.8f, 7.7f, 10.8f)
            curveTo(6.7f, 10.8f, 5.9f, 10f, 5.9f, 9f)
            curveTo(5.9f, 8f, 6.7f, 7.2f, 7.7f, 7.2f)
            curveTo(8.7f, 7.2f, 9.5f, 8f, 9.5f, 9f)
            close()
            moveTo(4.7f, 17.6f)
            lineTo(9.8f, 12.2f)
            lineTo(13f, 15.3f)
            lineTo(15.5f, 12.6f)
            lineTo(19.3f, 17.1f)
        }
    }

    val Back: ImageVector by lazy {
        lineIcon("Viewer.Back") {
            moveTo(19.5f, 12f); lineTo(4.5f, 12f)
            moveTo(10f, 6.5f); lineTo(4.5f, 12f); lineTo(10f, 17.5f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        lineIcon("Viewer.ChevronRight") { moveTo(9f, 5.5f); lineTo(15.5f, 12f); lineTo(9f, 18.5f) }
    }

    val ChevronDown: ImageVector by lazy {
        lineIcon("Viewer.ChevronDown") { moveTo(5.5f, 9f); lineTo(12f, 15.5f); lineTo(18.5f, 9f) }
    }

    val Refresh: ImageVector by lazy {
        lineIcon("Viewer.Refresh") {
            moveTo(20f, 12f)
            curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
            curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
            curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
            curveTo(15.1f, 4f, 17.8f, 5.8f, 19.1f, 8.4f)
            moveTo(19.5f, 4.4f); lineTo(19.2f, 8.6f); lineTo(15f, 8.2f)
        }
    }

    val Settings: ImageVector by lazy {
        lineIcon("Viewer.Settings") {
            moveTo(9.5f, 3.4f); lineTo(14.5f, 3.4f); lineTo(15f, 5.5f)
            lineTo(16.8f, 6.5f); lineTo(18.8f, 5.9f); lineTo(21.3f, 10.2f)
            lineTo(19.8f, 11.7f); lineTo(19.8f, 13.7f); lineTo(21.3f, 15.2f)
            lineTo(18.8f, 19.5f); lineTo(16.8f, 18.9f); lineTo(15f, 19.9f)
            lineTo(14.5f, 22f); lineTo(9.5f, 22f); lineTo(9f, 19.9f)
            lineTo(7.2f, 18.9f); lineTo(5.2f, 19.5f); lineTo(2.7f, 15.2f)
            lineTo(4.2f, 13.7f); lineTo(4.2f, 11.7f); lineTo(2.7f, 10.2f)
            lineTo(5.2f, 5.9f); lineTo(7.2f, 6.5f); lineTo(9f, 5.5f); close()
            moveTo(15.2f, 12.7f)
            curveTo(15.2f, 14.5f, 13.8f, 15.9f, 12f, 15.9f)
            curveTo(10.2f, 15.9f, 8.8f, 14.5f, 8.8f, 12.7f)
            curveTo(8.8f, 10.9f, 10.2f, 9.5f, 12f, 9.5f)
            curveTo(13.8f, 9.5f, 15.2f, 10.9f, 15.2f, 12.7f); close()
        }
    }

    val LightTheme: ImageVector by lazy {
        lineIcon("Viewer.LightTheme") {
            moveTo(12f, 2.5f); lineTo(12f, 4.5f)
            moveTo(12f, 19.5f); lineTo(12f, 21.5f)
            moveTo(2.5f, 12f); lineTo(4.5f, 12f)
            moveTo(19.5f, 12f); lineTo(21.5f, 12f)
            moveTo(5.3f, 5.3f); lineTo(6.7f, 6.7f)
            moveTo(17.3f, 17.3f); lineTo(18.7f, 18.7f)
            moveTo(18.7f, 5.3f); lineTo(17.3f, 6.7f)
            moveTo(6.7f, 17.3f); lineTo(5.3f, 18.7f)
            moveTo(16.8f, 12f)
            curveTo(16.8f, 14.7f, 14.7f, 16.8f, 12f, 16.8f)
            curveTo(9.3f, 16.8f, 7.2f, 14.7f, 7.2f, 12f)
            curveTo(7.2f, 9.3f, 9.3f, 7.2f, 12f, 7.2f)
            curveTo(14.7f, 7.2f, 16.8f, 9.3f, 16.8f, 12f); close()
        }
    }

    val DarkTheme: ImageVector by lazy {
        lineIcon("Viewer.DarkTheme") {
            moveTo(15.7f, 3.8f)
            curveTo(11.8f, 4.5f, 9.1f, 8f, 9.8f, 11.9f)
            curveTo(10.6f, 16.4f, 15.4f, 18.8f, 19.5f, 16.7f)
            curveTo(17.8f, 20.4f, 13.3f, 22.1f, 9.3f, 20.3f)
            curveTo(4.7f, 18.3f, 2.7f, 12.8f, 4.8f, 8.2f)
            curveTo(6.7f, 4.2f, 11.4f, 2.4f, 15.7f, 3.8f); close()
        }
    }

    val Folder: ImageVector by lazy {
        lineIcon("Viewer.Folder") {
            moveTo(3.5f, 7.3f)
            curveTo(3.5f, 6.5f, 4.2f, 5.8f, 5f, 5.8f)
            lineTo(10f, 5.8f); lineTo(12f, 8f); lineTo(19f, 8f)
            curveTo(19.8f, 8f, 20.5f, 8.7f, 20.5f, 9.5f)
            lineTo(20.5f, 17.8f)
            curveTo(20.5f, 18.6f, 19.8f, 19.3f, 19f, 19.3f)
            lineTo(5f, 19.3f)
            curveTo(4.2f, 19.3f, 3.5f, 18.6f, 3.5f, 17.8f); close()
        }
    }

    val Delete: ImageVector by lazy {
        lineIcon("Viewer.Delete") {
            moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(9f, 6.5f); lineTo(9.7f, 4f); lineTo(14.3f, 4f); lineTo(15f, 6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.4f, 19.2f)
            curveTo(7.5f, 20f, 8.2f, 20.6f, 9f, 20.6f)
            lineTo(15f, 20.6f)
            curveTo(15.8f, 20.6f, 16.5f, 20f, 16.6f, 19.2f)
            lineTo(17.5f, 6.5f)
            moveTo(10f, 10f); lineTo(10.3f, 17f)
            moveTo(14f, 10f); lineTo(13.7f, 17f)
        }
    }

    val Info: ImageVector by lazy {
        lineIcon("Viewer.Info") {
            moveTo(20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f); close()
            moveTo(12f, 10.7f); lineTo(12f, 16.2f)
            moveTo(12f, 7.5f); lineTo(12.01f, 7.5f)
        }
    }

    val BrokenImage: ImageVector by lazy {
        lineIcon("Viewer.BrokenImage") {
            moveTo(5f, 4.5f); lineTo(19f, 4.5f)
            curveTo(19.8f, 4.5f, 20.5f, 5.2f, 20.5f, 6f)
            lineTo(20.5f, 18f)
            curveTo(20.5f, 18.8f, 19.8f, 19.5f, 19f, 19.5f)
            lineTo(5f, 19.5f)
            curveTo(4.2f, 19.5f, 3.5f, 18.8f, 3.5f, 18f)
            lineTo(3.5f, 6f)
            curveTo(3.5f, 5.2f, 4.2f, 4.5f, 5f, 4.5f); close()
            moveTo(4.8f, 17.5f); lineTo(9.2f, 12.8f); lineTo(12.2f, 15.7f)
            moveTo(14f, 13.8f); lineTo(16.2f, 11.5f); lineTo(19.2f, 15.1f)
            moveTo(8.2f, 4.5f); lineTo(15.8f, 19.5f)
        }
    }

    val Tune: ImageVector by lazy {
        lineIcon("Viewer.Tune") {
            moveTo(4f, 6.5f); lineTo(8f, 6.5f)
            moveTo(12f, 6.5f); lineTo(20f, 6.5f)
            moveTo(10f, 4.5f); lineTo(10f, 8.5f)
            moveTo(4f, 12f); lineTo(14f, 12f)
            moveTo(18f, 12f); lineTo(20f, 12f)
            moveTo(16f, 10f); lineTo(16f, 14f)
            moveTo(4f, 17.5f); lineTo(7f, 17.5f)
            moveTo(11f, 17.5f); lineTo(20f, 17.5f)
            moveTo(9f, 15.5f); lineTo(9f, 19.5f)
        }
    }

    val Play: ImageVector by lazy {
        lineIcon("Viewer.Play") {
            moveTo(8f, 5.2f); lineTo(19f, 12f); lineTo(8f, 18.8f); close()
        }
    }

    val Pause: ImageVector by lazy {
        lineIcon("Viewer.Pause") {
            moveTo(8.5f, 5.5f); lineTo(8.5f, 18.5f)
            moveTo(15.5f, 5.5f); lineTo(15.5f, 18.5f)
        }
    }

    val Previous: ImageVector by lazy {
        lineIcon("Viewer.Previous") {
            moveTo(6f, 5.5f); lineTo(6f, 18.5f)
            moveTo(18f, 5.8f); lineTo(8.5f, 12f); lineTo(18f, 18.2f); close()
        }
    }

    val Next: ImageVector by lazy {
        lineIcon("Viewer.Next") {
            moveTo(18f, 5.5f); lineTo(18f, 18.5f)
            moveTo(6f, 5.8f); lineTo(15.5f, 12f); lineTo(6f, 18.2f); close()
        }
    }

    val ReplayFive: ImageVector by lazy {
        lineIcon("Viewer.ReplayFive", 2f) {
            moveTo(7.2f, 7.6f); lineTo(3.6f, 7.6f); lineTo(3.6f, 4f)
            moveTo(4f, 7.2f)
            curveTo(5.7f, 4.7f, 8.6f, 3.2f, 11.7f, 3.2f)
            curveTo(16.6f, 3.2f, 20.6f, 7.2f, 20.6f, 12.1f)
            curveTo(20.6f, 17f, 16.7f, 20.8f, 11.8f, 20.8f)
            curveTo(7.8f, 20.8f, 4.4f, 18.2f, 3.5f, 14.7f)
            moveTo(14.3f, 9.1f); lineTo(9.8f, 9.1f); lineTo(9.4f, 12.1f)
            curveTo(12.8f, 10.9f, 14.9f, 12.2f, 14.7f, 14.5f)
            curveTo(14.4f, 16.9f, 11.2f, 17.6f, 9.3f, 15.9f)
        }
    }

    val ForwardFive: ImageVector by lazy {
        lineIcon("Viewer.ForwardFive", 2f) {
            moveTo(16.8f, 7.6f); lineTo(20.4f, 7.6f); lineTo(20.4f, 4f)
            moveTo(20f, 7.2f)
            curveTo(18.3f, 4.7f, 15.4f, 3.2f, 12.3f, 3.2f)
            curveTo(7.4f, 3.2f, 3.4f, 7.2f, 3.4f, 12.1f)
            curveTo(3.4f, 17f, 7.3f, 20.8f, 12.2f, 20.8f)
            curveTo(16.2f, 20.8f, 19.6f, 18.2f, 20.5f, 14.7f)
            moveTo(14.3f, 9.1f); lineTo(9.8f, 9.1f); lineTo(9.4f, 12.1f)
            curveTo(12.8f, 10.9f, 14.9f, 12.2f, 14.7f, 14.5f)
            curveTo(14.4f, 16.9f, 11.2f, 17.6f, 9.3f, 15.9f)
        }
    }

    val VolumeOn: ImageVector by lazy {
        lineIcon("Viewer.VolumeOn") {
            moveTo(4f, 10f); lineTo(7.5f, 10f); lineTo(12f, 6.2f); lineTo(12f, 17.8f)
            lineTo(7.5f, 14f); lineTo(4f, 14f); close()
            moveTo(15f, 9f); curveTo(16.5f, 10.7f, 16.5f, 13.3f, 15f, 15f)
            moveTo(17.8f, 6.5f); curveTo(21f, 9.5f, 21f, 14.5f, 17.8f, 17.5f)
        }
    }

    val VolumeOff: ImageVector by lazy {
        lineIcon("Viewer.VolumeOff") {
            moveTo(4f, 10f); lineTo(7.5f, 10f); lineTo(12f, 6.2f); lineTo(12f, 17.8f)
            lineTo(7.5f, 14f); lineTo(4f, 14f); close()
            moveTo(15.5f, 9.5f); lineTo(20f, 14.5f)
            moveTo(20f, 9.5f); lineTo(15.5f, 14.5f)
        }
    }

    val Fullscreen: ImageVector by lazy {
        lineIcon("Viewer.Fullscreen") {
            moveTo(9f, 4f); lineTo(4f, 4f); lineTo(4f, 9f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(4f, 15f); lineTo(4f, 20f); lineTo(9f, 20f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        }
    }

    val FullscreenExit: ImageVector by lazy {
        lineIcon("Viewer.FullscreenExit") {
            moveTo(4f, 9f); lineTo(9f, 9f); lineTo(9f, 4f)
            moveTo(20f, 9f); lineTo(15f, 9f); lineTo(15f, 4f)
            moveTo(4f, 15f); lineTo(9f, 15f); lineTo(9f, 20f)
            moveTo(20f, 15f); lineTo(15f, 15f); lineTo(15f, 20f)
        }
    }

    val Sequential: ImageVector by lazy {
        lineIcon("Viewer.Sequential") {
            moveTo(6.8f, 5.8f); lineTo(16f, 5.8f)
            curveTo(18.2f, 5.8f, 19.8f, 7.4f, 19.8f, 9.6f); lineTo(19.8f, 11.2f)
            moveTo(17.2f, 8.6f); lineTo(19.8f, 11.2f); lineTo(22.1f, 8.6f)
            moveTo(17.2f, 18.2f); lineTo(8f, 18.2f)
            curveTo(5.8f, 18.2f, 4.2f, 16.6f, 4.2f, 14.4f); lineTo(4.2f, 12.8f)
            moveTo(6.8f, 15.4f); lineTo(4.2f, 12.8f); lineTo(1.9f, 15.4f)
        }
    }

    val RepeatOne: ImageVector by lazy {
        lineIcon("Viewer.RepeatOne") {
            moveTo(6.8f, 5.8f); lineTo(16f, 5.8f)
            curveTo(18.2f, 5.8f, 19.8f, 7.4f, 19.8f, 9.6f); lineTo(19.8f, 11.2f)
            moveTo(17.2f, 8.6f); lineTo(19.8f, 11.2f); lineTo(22.1f, 8.6f)
            moveTo(17.2f, 18.2f); lineTo(8f, 18.2f)
            curveTo(5.8f, 18.2f, 4.2f, 16.6f, 4.2f, 14.4f); lineTo(4.2f, 12.8f)
            moveTo(6.8f, 15.4f); lineTo(4.2f, 12.8f); lineTo(1.9f, 15.4f)
            moveTo(12f, 9.6f); lineTo(12f, 14.4f)
            moveTo(10.6f, 10.7f); lineTo(12f, 9.6f)
        }
    }

    val Shuffle: ImageVector by lazy {
        lineIcon("Viewer.Shuffle") {
            moveTo(4f, 7f); lineTo(6.5f, 7f)
            curveTo(11f, 7f, 13f, 17f, 17.5f, 17f); lineTo(20f, 17f)
            moveTo(17.5f, 14.5f); lineTo(20f, 17f); lineTo(17.5f, 19.5f)
            moveTo(4f, 17f); lineTo(6.5f, 17f)
            curveTo(11f, 17f, 13f, 7f, 17.5f, 7f); lineTo(20f, 7f)
            moveTo(17.5f, 4.5f); lineTo(20f, 7f); lineTo(17.5f, 9.5f)
        }
    }
}

private fun lineIcon(name: String, strokeWidth: Float = 1.7f, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }.build()
