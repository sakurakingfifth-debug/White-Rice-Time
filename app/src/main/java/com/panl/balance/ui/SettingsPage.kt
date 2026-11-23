package com.panl.balance.ui

import android.app.StatusBarManager
import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.panl.balance.data.DeepSeekClient
import com.panl.balance.data.UpdateClient
import com.panl.balance.anim.SeamlessView
import com.panl.balance.perf.MemoryGuard
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGhostButton
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb

/** 设置页：密钥管理 + 刷新策略 + 提醒 + 外观 + 数据。 */
@Composable
fun SettingsPage(
    vm: BalanceViewModel,
    topPad: Dp,
    scroll: ScrollState,
    onOpenOta: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    // 版本号从包里读，省得每次发版都改一遍界面文案
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "—" }
    }

    var showSeamless by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // 从首页「填写密钥」跳过来时自动聚焦输入框（等页面切完再抢焦点）
    LaunchedEffect(vm.focusKeyField) {
        if (vm.focusKeyField) {
            delay(280)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            vm.consumeKeyFocus()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = topPad + 12.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PuiPageTitle("设置")

        PuiGroup("API 密钥") {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KeyInput(
                    value = vm.keyDraft,
                    visible = vm.showKey,
                    placeholder = if (vm.hasKey) "输入新密钥以替换（留空不改）" else "sk-...",
                    onValueChange = vm::updateKeyDraft,
                    onToggleVisible = vm::toggleShowKey,
                    focusRequester = focusRequester,
                    onSubmit = {
                        vm.saveKey()
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PuiPrimaryButton(
                        text = "保存并验证",
                        modifier = Modifier.weight(1f),
                        loading = vm.verify is VerifyState.Running,
                        onClick = {
                            vm.saveKey()
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    )
                    PuiGhostButton("清除", Modifier.weight(0.42f)) { vm.clearKey() }
                }

                VerifyLine(vm)

                Text(
                    "密钥用 Android Keystore 的 AES-GCM 加密后保存在本机；请求直连 api.deepseek.com，不经过任何中转。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        PuiGroup("外观") {
            // 这一行是 ColorOS 无缝动画的源 View：点下去像「余额明细」那样放大成整页
            val activity = context.findActivity()
            val rowCardColor = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
            SeamlessHost(
                onOpen = { source ->
                    if (activity != null) {
                        val radiusPx = 20f * activity.resources.displayMetrics.density
                        openAppearanceSeamless(
                            activity = activity,
                            source = source,
                            radiusPx = radiusPx,
                            fillColor = rowCardColor
                        )
                    }
                }
            ) { open ->
                PuiNavRow(
                    icon = PuiIcon.Brush,
                    title = "外观与导航栏",
                    subtitle = "主题、主题色、模糊",
                    showDivider = false,
                    onClick = open
                )
            }
        }

        PuiGroup("刷新与通知") {
            PuiNavRow(
                icon = PuiIcon.Refresh,
                title = "自动刷新、后台与通知",
                subtitle = "轮询间隔、后台常驻、充值更新提醒",
                showDivider = false,
                onClick = onOpenSync
            )
        }

        PuiGroup("提醒") {
            PuiSwitchRow(
                icon = PuiIcon.WalletSearch,
                title = "低余额提醒",
                subtitle = "余额低于阈值时在首页显示提示",
                checked = vm.lowAlert,
                onChange = { vm.updateLowAlert(it) },
                showDivider = true
            )
            PuiSliderRow(
                icon = null,
                label = "提醒阈值",
                value = vm.threshold,
                enabled = vm.lowAlert,
                min = 1,
                max = 200,
                suffix = "元",
                showDivider = false
            ) { vm.updateThreshold(it) }
        }

        PuiGroup("数据") {
            PuiInfoRow("余额记录", "${vm.history.size} 条", icon = PuiIcon.Coin)
            PuiInfoRow(
                "上次同步",
                vm.snapshot?.let { Fmt.clock(it.fetchedAt) } ?: "无",
                showDivider = true,
                icon = PuiIcon.Refresh
            )
            // 回收站：清空后的记录先放这里，只有进去彻底删除才真删
            val activity = context.findActivity()
            val rowCardColor = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
            SeamlessHost(
                onOpen = { source ->
                    if (activity != null) {
                        openSeamless(
                            activity = activity,
                            source = source,
                            radiusPx = 20f * activity.resources.displayMetrics.density,
                            fillColor = rowCardColor,
                            target = TrashActivity::class.java
                        )
                    }
                }
            ) { open ->
                PuiNavRow(
                    icon = PuiIcon.Trash,
                    title = "回收站",
                    subtitle = if (vm.trash.isEmpty()) {
                        "清空后的余额记录会先放到这里"
                    } else {
                        "已暂存 ${vm.trash.size} 条，进去可恢复或彻底删除"
                    },
                    onClick = open
                )
            }
            Column(Modifier.padding(16.dp)) {
                PuiGhostButton("清空余额记录", Modifier.fillMaxWidth()) { confirmClear = true }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("清空余额记录？") },
                text = {
                    Column {
                        DialogWindowBlur()
                        Text(
                            "这 ${vm.history.size} 条记录会被移出「余额记录」，放到回收站里暂存。\n\n" +
                                "在回收站里可以恢复，也可以彻底删除 —— 彻底删除之后才无法恢复。"
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.clearHistory()
                        confirmClear = false
                    }) { Text("移入回收站") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("取消") }
                }
            )
        }

        if (showSeamless) {
            var memoryRows by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
            LaunchedEffect(Unit) {
                // Debug.getPss() 要读 smaps，几十毫秒级别，文档明确要求别放主线程
                memoryRows = withContext(Dispatchers.IO) { MemoryGuard.diagnose(context) }
            }
            AlertDialog(
                onDismissRequest = { showSeamless = false },
                title = { Text("自检") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DialogWindowBlur()
                        DiagnoseSection("无缝动画")
                        SeamlessView.diagnose().forEach { (key, value) ->
                            DiagnoseRow(key, value)
                        }
                        Spacer(Modifier.height(10.dp))
                        DiagnoseSection("内存")
                        (memoryRows ?: listOf("读取" to "…")).forEach { (key, value) ->
                            DiagnoseRow(key, value)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSeamless = false }) { Text("关闭") }
                }
            )
        }

        PuiGroup("关于") {
            PuiInfoRow("应用", "白饭时刻", icon = PuiIcon.Grid)
            // 「无缝动画」那一行太技术化，已隐藏；自检面板改成**长按版本行**打开
            Box(
                Modifier.combinedClickable(
                    onLongClick = { showSeamless = true },
                    onClick = {}
                )
            ) {
                PuiInfoRow("版本", versionName, icon = PuiIcon.Flag)
            }
            PuiNavRow(
                icon = PuiIcon.Code,
                title = "GitHub 项目",
                subtitle = "${UpdateClient.OWNER}/${UpdateClient.REPO}",
                onClick = { openUrl(context, UpdateClient.REPO_URL) }
            )
            PuiNavRow(
                icon = PuiIcon.Export,
                title = "检查更新",
                subtitle = "查看版本、更新日志与下载",
                onClick = onOpenOta
            )
            PuiInfoRow("接口", "api.deepseek.com", icon = PuiIcon.Chart)
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "没有自建服务器，也没有统计上报。余额数据只在你刷新时从 DeepSeek 官方接口拉取。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 和「清空余额记录」同一个样式：整宽次级按钮，不再是行内文字链
                PuiGhostButton("打开用量后台", Modifier.fillMaxWidth()) {
                    openUrl(context, DeepSeekClient.KEYS_URL)
                }
            }
        }
    }
}

@Composable
private fun KeyInput(
    value: String,
    visible: Boolean,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurface,
            fontSize = 13.sp
        ),
        cursorBrush = SolidColor(scheme.primary),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceContainerHighest)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleVisible),
                    contentAlignment = Alignment.Center
                ) {
                    PuiIconView(
                        icon = if (visible) PuiIcon.EyeOff else PuiIcon.Eye,
                        modifier = Modifier.size(20.dp),
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun VerifyLine(vm: BalanceViewModel) {
    val scheme = MaterialTheme.colorScheme
    val text: String
    val color: androidx.compose.ui.graphics.Color
    when (val state = vm.verify) {
        VerifyState.Running -> {
            text = "正在验证…"
            color = scheme.primary
        }
        is VerifyState.Ok -> {
            text = state.text
            color = scheme.primary
        }
        is VerifyState.Err -> {
            text = state.text
            color = scheme.error
        }
        VerifyState.Idle -> {
            text = if (vm.hasKey) "已保存 · " + Fmt.maskKey(vm.apiKey) else "尚未配置密钥"
            color = scheme.onSurfaceVariant
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(start = 4.dp)
    )
}




/** 自检面板的分组标题。 */
@Composable
private fun DiagnoseSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** 自检面板的一行：`key：value`，长值折行显示。 */
@Composable
private fun DiagnoseRow(key: String, value: String) {
    Text(
        "$key：$value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
