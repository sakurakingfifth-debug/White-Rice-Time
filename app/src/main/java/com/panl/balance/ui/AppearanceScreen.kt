package com.panl.balance.ui

import android.app.StatusBarManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.panl.balance.data.Prefs
import com.panl.balance.qs.BalanceTileService
import com.panl.pui.PuiGhostButton
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitchRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import com.panl.pui.progressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import com.panl.pui.PuiTopFade
import com.panl.pui.PuiPageTitle
import androidx.compose.foundation.layout.offset

/**
 * 外观与导航栏：明暗、主题色、模糊，参考 PUI 安装器的同名页面。
 *
 * 三项都是「改完立刻生效」：状态放在 [Appearance] 里，`BalanceTheme` 直接读它重组。
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showArgbDialog by remember { mutableStateOf(false) }
    var tileHint by remember { mutableStateOf<String?>(null) }
    val custom = Appearance.accentMode == Prefs.MODE_CUSTOM

    // 完全照主页（设置页）的做法：大标题跟着列表滚，顶部模糊带从屏幕顶端算起，
    // 内容滚进带子里逐渐虚化。标题用 PuiPageTitle，和「设置」同一个字号。
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val blurHeight = rememberPuiTopBlurHeight()
    val density = LocalDensity.current
    val topBlurRadius = with(density) { Appearance.topBlurPx.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (Appearance.blurEnabled) {
                        Modifier.progressiveTopBlur(blurHeight, maxRadius = topBlurRadius)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = blurHeight + 4.dp, bottom = 32.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .offset(x = (-8).dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Chevron(size = 18.dp, color = scheme.onSurface, direction = -1)
                }
                PuiPageTitle("外观与导航栏")
            }
            PuiGroup("控制中心") {
                PuiInfoRow(
                    "余额磁贴",
                    "",
                    showDivider = false
                )
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PuiGhostButton("添加到控制中心", Modifier.fillMaxWidth()) {
                            // 有的 ROM 收下请求却从不回调，先给一句「已发起」，别让按钮看起来没反应
                            tileHint = "已向系统发起添加请求…"
                            BalanceTileService.requestAdd(context) { result ->
                                tileHint = tileResultText(result)
                            }
                        }
                    }
                    Text(
                        tileHint ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            "也可以在控制中心下拉后点「编辑」手动添加。"
                        } else {
                            "在控制中心下拉后点「编辑」，把「余额」磁贴拖进常用区域。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
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



        }

        PuiTopFade(height = blurHeight, topAlpha = 0.5f)
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

/** 系统「把磁贴加到控制中心」回调的文案（API 33 只有三个结果码，失败统一兜底）。 */
private fun tileResultText(result: Int): String = when (result) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加到控制中心"
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "磁贴已经在控制中心里了"
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "没有添加，可以下拉控制中心手动添加"
    StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> "应用不在前台，回到应用里再点一次"
    else -> "没有添加成功（结果码 $result），可下拉控制中心手动添加"
}
