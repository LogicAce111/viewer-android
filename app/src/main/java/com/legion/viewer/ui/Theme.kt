package com.legion.viewer.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.legion.viewer.model.AppSettings
import com.legion.viewer.model.AppTheme

private val ViewerDarkColors = darkColorScheme(
    primary = Color(0xFF8293FF),
    onPrimary = Color(0xFF10205F),
    primaryContainer = Color(0xFF24366F),
    secondary = Color(0xFF9DB9FF),
    background = Color(0xFF0B1019),
    surface = Color(0xFF111824),
    surfaceVariant = Color(0xFF1B2637),
    outline = Color(0xFF35445B),
)

private val ViewerLightColors = lightColorScheme(
    primary = Color(0xFF4F63DB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E6FF),
    secondary = Color(0xFF49658F),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFE8ECF5),
    outline = Color(0xFFCBD3E2),
)

private val ViewerShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun ViewerTheme(settings: State<AppSettings>, content: @Composable () -> Unit) {
    val dark = settings.value.appTheme == AppTheme.Dark
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) ViewerDarkColors else ViewerLightColors,
        shapes = ViewerShapes,
        typography = Typography(),
        content = content,
    )
}
