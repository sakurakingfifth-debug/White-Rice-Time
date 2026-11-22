package com.panl.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.panl.balance.data.Prefs
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitchRow

/**
 * 外观与导航栏：明暗、主题色、模糊，参考 PUI 安装器的同名页面。
 *
 * 三项都是「改完立刻生效」：状态放在 [Appearance] 里，`BalanceTheme` 直接读它重组。
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var showArgbDialog by remember { mutableStateOf(false) }
    val custom = Appearance.accentMode == Prefs.MODE_CUSTOM

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Chevron(size = 18.dp, color = scheme.onSurface, direction = -1)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "外观与导航栏",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            PuiGroup(title = null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "主题",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    PuiSegmented(
                        items = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = when (Appearance.themeMode) {
                            Prefs.MODE_LIGHT -> 1
                            Prefs.MODE_DARK -> 2
                            else -> 0
                        },
                        onSelect = { index ->
                            Appearance.updateThemeMode(
                                when (index) {
                                    1 -> Prefs.MODE_LIGHT
                                    2 -> Prefs.MODE_DARK
                                    else -> Prefs.MODE_SYSTEM
                                }
                            )
                        }
                    )
                }

                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
                    Text(
                        "主题色",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                }

                PuiChoiceRow(
                    icon = PuiIcon.Brush,
                    title = "自定义 ARGB",
                    subtitle = if (custom) {
                        "当前 #%08X，点击重新输入".format(Appearance.accentArgb)
                    } else {
                        "输入 #AARRGGBB 应用主题色"
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (custom) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(Appearance.accentArgb))
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Chevron()
                        }
                    },
                    onClick = { showArgbDialog = true }
                )

                PuiChoiceRow(
                    icon = PuiIcon.Glow,
                    title = "跟随系统壁纸",
                    subtitle = "用系统从壁纸取的色作为主题色",
                    trailing = {
                        if (Appearance.accentMode == Prefs.MODE_WALLPAPER) CheckMark()
                    },
                    onClick = { Appearance.updateAccentMode(Prefs.MODE_WALLPAPER) }
                )

                PuiChoiceRow(
                    icon = PuiIcon.Refresh,
                    title = "恢复默认主题色",
                    subtitle = "回到设计系统自带的 DeepSeek 蓝",
                    trailing = {
                        if (Appearance.accentMode == Prefs.MODE_DEFAULT) CheckMark()
                    },
                    onClick = { Appearance.updateAccentMode(Prefs.MODE_DEFAULT) }
                )

                PuiSwitchRow(
                    icon = PuiIcon.Settings,
                    title = "顶部模糊与毛玻璃",
                    subtitle = "标题滚到状态栏下方时逐渐虚化",
                    checked = Appearance.blurEnabled,
                    onChange = { Appearance.updateBlurEnabled(it) },
                    showDivider = true
                )
                PuiSliderRow(
                    icon = null,
                    label = "顶部模糊深度",
                    value = Appearance.topBlurPx,
                    enabled = Appearance.blurEnabled,
                    min = 2,
                    max = 100,
                    suffix = "px",
                    showDivider = true,
                    onChange = { Appearance.updateTopBlurPx(it) }
                )
                PuiSliderRow(
                    icon = null,
                    label = "浮窗背景模糊",
                    value = Appearance.navBlurPx,
                    enabled = Appearance.blurEnabled,
                    min = 2,
                    max = 100,
                    suffix = "px",
                    showDivider = false,
                    onChange = { Appearance.updateNavBlurPx(it) }
                )
            }

            Text(
                "主题色只影响主色（按钮、开关、图标、导航栏选中态），底与卡片的中性灰阶不跟着变，" +
                    "否则深浅色下会脏。模糊关闭后顶栏是纯色渐变，不再实时采样。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showArgbDialog) {
        ArgbDialog(
            initial = Appearance.accentArgb,
            onDismiss = { showArgbDialog = false },
            onApply = {
                Appearance.updateAccentArgb(it)
                showArgbDialog = false
            }
        )
    }
}

/** 输入 #AARRGGBB（或 #RRGGBB），带实时预览；非法输入时「确定」不可用。 */
@Composable
private fun ArgbDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var text by remember { mutableStateOf("#%08X".format(initial)) }
    val parsed = parseArgb(text)
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义主题色") },
        text = {
            Column {
                Text(
                    "输入 8 位 ARGB（如 #FF4D6BFE），或省略透明度写 6 位（如 #4D6BFE）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(parsed?.let { Color(it) } ?: scheme.surfaceContainerHighest)
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(9) },
                        singleLine = true,
                        label = { Text("ARGB") },
                        isError = parsed == null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xFF4D6BFE, 0xFF6E56F8, 0xFF00A05A, 0xFFE0A106, 0xFFE2564B)
                        .take(5)
                        .forEach { argb ->
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb.toInt()))
                                    .clickable { text = "#%08X".format(argb.toInt()) }
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onApply) }
            ) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
