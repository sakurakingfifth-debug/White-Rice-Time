package com.panl.balance.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.panl.pui.PuiDarkColors
import com.panl.pui.PuiLightColors

/**
 * 品牌配色。
 *
 * PUI 的骨架（浅灰底 / 白卡 / 20dp 圆角 / 克制字阶）保持不变，只把强调色从 OPPO 绿
 * 换成 DeepSeek 蓝。派生规则和 PUI 一致：`primary` 是主色，`primaryContainer` 是浅底，
 * `onPrimaryContainer` 是浅底上的深字。
 */
val DeepSeekLightColors: ColorScheme = PuiLightColors.copy(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0A1B66),
    secondary = Color(0xFF4A5B8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E6FF),
    onSecondaryContainer = Color(0xFF101A3A),
    tertiary = Color(0xFF6E56F8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF21005E),
)

val DeepSeekDarkColors: ColorScheme = PuiDarkColors.copy(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF0A1F73),
    primaryContainer = Color(0xFF2A3FB0),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFBBC6EA),
    onSecondary = Color(0xFF1B2745),
    secondaryContainer = Color(0xFF2E3A5C),
    onSecondaryContainer = Color(0xFFDCE3FF),
    tertiary = Color(0xFFC9B8FF),
    onTertiary = Color(0xFF2A0F73),
    tertiaryContainer = Color(0xFF432A99),
    onTertiaryContainer = Color(0xFFE7DEFF),
)

/** 顶部主卡片的品牌渐变。暗色下压暗一档，避免刺眼。 */
val HeroGradientLight = listOf(Color(0xFF4D6BFE), Color(0xFF6A4DF6))
val HeroGradientDark = listOf(Color(0xFF3446C8), Color(0xFF4C35C0))

/** 切到 PUI 绿主色时，主卡片同步换成绿色系，避免「绿色界面 + 蓝色主卡」的割裂。 */
val HeroGreenLight = listOf(Color(0xFF00A05A), Color(0xFF0E8F6B))
val HeroGreenDark = listOf(Color(0xFF00794A), Color(0xFF00695C))

/** 低余额提示的琥珀色，亮暗各一套。 */
val WarnBgLight = Color(0xFFFFF3E0)
val WarnFgLight = Color(0xFF8A4B00)
val WarnBgDark = Color(0xFF2E2213)
val WarnFgDark = Color(0xFFFFD8A8)
