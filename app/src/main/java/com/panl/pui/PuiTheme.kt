package com.panl.pui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * PUI 设计系统的主题层。
 *
 * 视觉基调：浅灰底 + 纯白卡片 + OPPO 绿主色，圆角克制、字阶克制，
 * 接近 ColorOS 原生设置页的观感，而不是 Material 3 Expressive 那种「大字大圆角」。
 *
 * 只有一套主题（PUI），没有「两套风格切换」的概念 —— 需要暗色就靠系统深色模式。
 */

/**
 * 壁纸取色（系统动态主色）。
 *
 * 主题本身用固定的绿色调色板，但 [PuiIconView] 的图标要「跟随壁纸颜色」，
 * 所以单独把 `dynamicLight/DarkColorScheme().primary` 透出来。
 * 系统不支持动态取色（API < 31）或调用方关掉时退回 [Color.Unspecified]，
 * 组件内部会用 `colorScheme.primary` 兜底。
 */
val LocalWallpaperAccent = staticCompositionLocalOf { Color.Unspecified }

// ---------------- 字阶 ----------------

/**
 * PUI 字阶：标题比 Material 3 默认更克制，行高略紧。
 *
 * 只覆盖实际用到的档位，其余沿用 Material 3 默认值。
 */
val PuiTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp)
)

// ---------------- 形状 ----------------

/**
 * PUI 形状：卡片圆角 **20dp**（`large`），比 Material 3 Expressive 的 28dp 小一档。
 *
 * 组件里凡是 `MaterialTheme.shapes.large` 的地方（卡片、提示卡）都走这一档，
 * 改这一个值等于改所有卡片。
 */
val PuiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---------------- 配色 ----------------

/** PUI 品牌绿（亮色主色）。 */
val PuiGreen = Color(0xFF00A05A)

/** 亮色配色：浅灰背景 `#F6F8F7`，卡片纯白（`surfaceContainer*` 全为 `#FFFFFF`）。 */
val PuiLightColors: ColorScheme = lightColorScheme(
    primary = PuiGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4EFDB),
    onPrimaryContainer = Color(0xFF00391C),
    secondary = Color(0xFF4E6355),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8D9),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F29),
    // 亮色：中性浅灰底 #F0F1F3 + 纯白卡片
    background = Color(0xFFF0F1F3),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF0F1F3),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE9EEE9),
    outline = Color(0xFF707A73),
    outlineVariant = Color(0xFFDDE4DE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/** 暗色配色：背景纯黑，卡片 `#1A1A1A`。 */
val PuiDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5ADB9A),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = Color(0xFF77F8B4),
    secondary = Color(0xFFB7CCBC),
    onSecondary = Color(0xFF23342A),
    secondaryContainer = Color(0xFF394B3F),
    onSecondaryContainer = Color(0xFFD3E8D9),
    tertiary = Color(0xFFA5CCDD),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF899389),
    outlineVariant = Color(0xFF3F4943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * 主题入口。
 *
 * ```kotlin
 * PuiTheme {
 *     // 你的界面
 * }
 * ```
 *
 * @param darkTheme 是否暗色，默认跟随系统。
 * @param dynamicAccent 是否用壁纸动态取色作为 [PuiIconView] 的图标色（需要 API 31+）。
 *   关掉则图标用 `colorScheme.primary`（PUI 绿）。注意这**只影响图标**，
 *   界面主色始终是 PUI 调色板，不会被壁纸带跑。
 * @param colorScheme 可选：替换整套配色（品牌主色等）。传 null 用 PUI 自带的两套调色板，
 *   字阶与形状仍然走 [PuiTypography] / [PuiShapes]，观感不变。
 */
@Composable
fun PuiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAccent: Boolean = true,
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val scheme = colorScheme ?: if (darkTheme) PuiDarkColors else PuiLightColors

    val wallpaperAccent =
        if (dynamicAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(ctx).primary else dynamicLightColorScheme(ctx).primary
        } else {
            scheme.primary
        }

    CompositionLocalProvider(LocalWallpaperAccent provides wallpaperAccent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PuiTypography,
            shapes = PuiShapes,
            content = content
        )
    }
}
