package com.panl.balance.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.panl.balance.data.Prefs
import com.panl.pui.PuiTheme

/**
 * 外观状态：明暗、主题色、模糊。
 *
 * 用 Compose 的 `mutableStateOf` 而不是 SharedPreferences 直接读 —— 设置页一改就要立刻
 * 让整棵树重组（主题色、模糊深度都是即时生效的）。同一个对象也负责落盘，避免两处写值。
 *
 * [load] 在 `PuiApp.onCreate` 里调一次；DetailActivity 这类独立进程入口直接读这里的值即可。
 */
object Appearance {

    var themeMode by mutableStateOf(Prefs.MODE_SYSTEM)
        private set

    var accentMode by mutableStateOf(Prefs.MODE_DEFAULT)
        private set

    var accentArgb by mutableIntStateOf(Prefs.DEFAULT_ACCENT_ARGB)
        private set

    var blurEnabled by mutableStateOf(true)
        private set

    var topBlurPx by mutableIntStateOf(60)
        private set

    var navBlurPx by mutableIntStateOf(48)
        private set

    private var prefs: Prefs? = null

    fun load(context: Context) {
        val p = Prefs(context.applicationContext)
        prefs = p
        themeMode = p.themeMode
        accentMode = p.accentMode
        accentArgb = p.accentArgb
        blurEnabled = p.blurEnabled
        topBlurPx = p.topBlurPx
        navBlurPx = p.navBlurPx
    }

    fun updateThemeMode(value: String) {
        themeMode = value
        prefs?.themeMode = value
    }

    fun updateAccentMode(value: String) {
        accentMode = value
        prefs?.accentMode = value
    }

    fun updateAccentArgb(value: Int) {
        accentArgb = value
        prefs?.accentArgb = value
        // 选了颜色就顺手切到「自定义」，和输入框的直觉一致
        updateAccentMode(Prefs.MODE_CUSTOM)
    }

    fun updateBlurEnabled(value: Boolean) {
        blurEnabled = value
        prefs?.blurEnabled = value
    }

    fun updateTopBlurPx(value: Int) {
        topBlurPx = value
        prefs?.topBlurPx = value
    }

    fun updateNavBlurPx(value: Int) {
        navBlurPx = value
        prefs?.navBlurPx = value
    }

    /** 当前是否深色：明暗三态里 `System` 才看系统。 */
    @Composable
    fun dark(): Boolean = when (themeMode) {
        Prefs.MODE_LIGHT -> false
        Prefs.MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
}

/**
 * 用强调色重算一套配色。
 *
 * 只动 primary 这一族：容器色按混色推出来（浅色模式往白里混、深色往底色里混），
 * 其余（surface / outline / error）保持 DeepSeek 主题原样 —— 它们和强调色无关，
 * 重算反而会把中性的灰阶弄脏。
 */
fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF10131A) else Color.White
    val container = if (dark) accent.mix(Color(0xFF15171C), 0.62f) else accent.mix(Color.White, 0.84f)
    val onContainer = if (dark) accent.mix(Color.White, 0.74f) else accent.mix(Color(0xFF0B1020), 0.62f)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
    )
}

/** 把 [base] 以 [t] 的比例混进自己（t=0 原色，t=1 完全是 base）。 */
private fun Color.mix(base: Color, t: Float): Color = Color(
    red = red + (base.red - red) * t,
    green = green + (base.green - green) * t,
    blue = blue + (base.blue - blue) * t,
    alpha = 1f,
)

/** 解析 `#AARRGGBB` / `#RRGGBB` / `AARRGGBB`，失败返回 null。 */
fun parseArgb(text: String): Int? {
    val raw = text.trim().removePrefix("#")
    if (raw.length != 8 && raw.length != 6) return null
    if (!raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) (0xFF000000L or value).toInt() else value.toInt()
}

/** 当前生效的强调色：[Appearance] 的三种来源最后落到一个颜色上。 */
@Composable
fun accentColor(): Color {
    val context = LocalContext.current
    val dark = Appearance.dark()
    return when (Appearance.accentMode) {
        Prefs.MODE_CUSTOM -> Color(Appearance.accentArgb)
        Prefs.MODE_WALLPAPER -> dynamicPrimary(context, dark) ?: defaultPrimary(dark)
        else -> defaultPrimary(dark)
    }
}

private fun defaultPrimary(dark: Boolean) =
    if (dark) DeepSeekDarkColors.primary else DeepSeekLightColors.primary

private fun dynamicPrimary(context: Context, dark: Boolean): Color? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        }.getOrNull()
    } else {
        null
    }

/**
 * 应用主题：明暗三态 + 主题色三态，接到 PUI 上。
 *
 * `dynamicAccent = false` 是有意的：图标统一跟 `colorScheme.primary` 走，
 * 而这里的 primary 已经是用户选的那个强调色 —— 不再需要单独的「图标跟随壁纸」开关。
 */
@Composable
fun BalanceTheme(content: @Composable () -> Unit) {
    val dark = Appearance.dark()
    val base = if (dark) DeepSeekDarkColors else DeepSeekLightColors
    val accent = when (Appearance.accentMode) {
        Prefs.MODE_CUSTOM -> Color(Appearance.accentArgb)
        Prefs.MODE_WALLPAPER -> null // 交给下面按壁纸取色
        else -> null
    }
    val scheme = when {
        accent != null -> base.withAccent(accent, dark)
        Appearance.accentMode == Prefs.MODE_WALLPAPER -> {
            val context = LocalContext.current
            val wallpaper = dynamicPrimary(context, dark)
            if (wallpaper != null) base.withAccent(wallpaper, dark) else base
        }
        else -> base
    }
    PuiTheme(darkTheme = dark, dynamicAccent = false, colorScheme = scheme, content = content)
}
