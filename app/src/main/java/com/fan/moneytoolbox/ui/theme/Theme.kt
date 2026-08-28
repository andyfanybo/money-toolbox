package com.fan.moneytoolbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 品牌主色: 青翠绿 —— 省钱 = 赚钱的绿
private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF44635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8DE),
    onSecondaryContainer = Color(0xFF021F19),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF452B05),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF4),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF7FBF4),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005233),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = Color(0xFFB0CCC2),
    onSecondary = Color(0xFF1B352E),
    secondaryContainer = Color(0xFF324B44),
    onSecondaryContainer = Color(0xFFCCE8DE),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF3F2D04),
    tertiaryContainer = Color(0xFF5B4208),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101511),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF101511),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
)

@Composable
fun MoneyBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 保持品牌绿色的一致性,默认不跟随系统动态取色
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
