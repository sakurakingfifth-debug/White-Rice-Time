package com.panl.balance.ui

import android.Manifest
import android.app.StatusBarManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.LifecycleEventEffect
import com.panl.balance.data.DeepSeekClient
import com.panl.balance.data.UpdateClient
import com.panl.balance.anim.SeamlessView
import com.panl.balance.perf.MemoryGuard
import com.panl.balance.qs.BalanceTileService
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

/** 设置页：密钥管理 + 刷新策略 + 提醒 + 外观 + 数据。 */
@Composable
fun SettingsPage(
    vm: BalanceViewModel,
    topPad: Dp,
    scroll: ScrollState,
    onOpenOta: () -> Unit,
    onOpenAppearance: () -> Unit,
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

    // 通知权限：开启任一通知开关时按需申请
    var permGranted by remember { mutableStateOf(NotificationPermission.granted(context)) }
    var pendingToggle by remember { mutableStateOf(PendingToggle.None) }
    var showSeamless by remember { mutableStateOf(false) }
    var hideConfirm by remember { mutableStateOf(false) }
    var tileHint by remember { mutableStateOf<String?>(null) }

    fun applyToggle(kind: PendingToggle, enabled: Boolean) {
        when (kind) {
            PendingToggle.Live -> vm.updateLiveNotification(enabled)
            PendingToggle.Normal -> vm.updateNormalNotification(enabled)
            PendingToggle.Background -> vm.updateBackgroundRun(enabled)
            PendingToggle.None -> Unit
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permGranted = granted
        applyToggle(pendingToggle, granted)
        pendingToggle = PendingToggle.None
    }

    fun requestToggle(kind: PendingToggle) {
        when {
            NotificationPermission.granted(context) -> applyToggle(kind, true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                pendingToggle = kind
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> applyToggle(kind, true)
        }
    }

    // 从系统设置返回后刷新授权状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permGranted = NotificationPermission.granted(context)
    }

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

        PuiGroup("刷新") {
            PuiSwitchRow(
                icon = PuiIcon.Refresh,
                title = "自动刷新",
                subtitle = "回到前台时同步，并按间隔轮询",
                checked = vm.autoRefresh,
                onChange = { vm.updateAutoRefresh(it) },
                showDivider = true
            )
            PuiSliderRow(
                icon = null,
                label = "刷新间隔",
                value = vm.intervalSec,
                // 后台常驻也用这个间隔，所以任一开关开着就允许拖动
                enabled = vm.autoRefresh || vm.backgroundRun,
                min = 30,
                max = 300,
                suffix = "s",
                showDivider = false
            ) { vm.updateIntervalSec(it) }
        }

        PuiGroup("后台") {
            PuiSwitchRow(
                icon = PuiIcon.Bolt,
                title = "允许一直在后台运行",
                // 间隔直接写进副标题：和上面「刷新间隔」滑杆、下面「后台同步」状态保持同一个数字
                subtitle = if (vm.hasKey) {
                    "前台服务每 ${vm.intervalSec} 秒同步一次，锁屏后余额、通知与插件也保持最新"
                } else {
                    "先在上方填好 API 密钥"
                },
                checked = vm.backgroundRun,
                onChange = { on ->
                    if (on) requestToggle(PendingToggle.Background) else vm.updateBackgroundRun(false)
                },
                enabled = vm.hasKey,
                showDivider = false
            )
            // 这段讲的是「系统弹窗 + 常驻通知」，属于开关本身的补充说明，紧跟在开关下面。
            // 原来放在「后台同步」状态行之后，读起来像在解释那一行，且和间隔对不上。
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Text(
                    "首次开启时系统会询问「要允许应用一直在后台运行吗」，选「允许」；" +
                        "通知栏会多一条「后台同步」常驻通知，点通知上的「停止」即可关闭。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            PuiDivider(16.dp)
            PuiSwitchRow(
                icon = PuiIcon.EyeOff,
                title = "后台隐藏",
                subtitle = "退到后台后不在「最近任务」保留卡片",
                checked = vm.hideFromRecents,
                onChange = { on ->
                    // 打开前先讲清代价；关掉不需要确认，之后就不再摘卡片
                    if (on) hideConfirm = true else vm.updateHideFromRecents(false)
                },
                showDivider = false
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Text(
                    "退到后台就把卡片摘掉，任务列表里不会留下余额缩略图，也不会被误滑掉；" +
                        "代价是下次打开相当于重新进入（会回到上次停留的页面），" +
                        "且 ColorOS 等机型在卡片消失后没法再给应用加锁后台，可能影响上面的「允许一直在后台运行」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            PuiDivider(16.dp)
            PuiInfoRow(
                "后台同步",
                if (vm.backgroundRun) "运行中" else "已关闭",
                showDivider = false
            )
        }

        PuiGroup("提醒") {
            PuiSwitchRow(
                icon = PuiIcon.Glow,
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

        PuiGroup("通知") {
            PuiSwitchRow(
                icon = PuiIcon.Bell,
                title = "实时更新",
                subtitle = "Android 16+ 显示为状态栏实时动态，低版本为常驻通知",
                checked = vm.liveNotification,
                onChange = { on ->
                    if (on) requestToggle(PendingToggle.Live) else vm.updateLiveNotification(false)
                },
                showDivider = true
            )
            PuiSwitchRow(
                icon = PuiIcon.Bell,
                title = "余额通知",
                subtitle = "常驻通知，滑不走；展开可看赠送 / 充值并手动刷新",
                checked = vm.normalNotification,
                onChange = { on ->
                    if (on) requestToggle(PendingToggle.Normal) else vm.updateNormalNotification(false)
                },
                showDivider = true
            )
            PuiInfoRow(
                "通知权限",
                if (permGranted) "已允许" else "未允许",
                showDivider = !permGranted
            )
            if (!permGranted) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    PuiGhostButton("去系统设置开启", Modifier.fillMaxWidth()) {
                        openNotificationSettings(context)
                    }
                }
            }
        }

        PuiGroup("控制中心") {
            PuiInfoRow(
                "余额磁贴",
                "显示余额（精确到分），点一下打开应用",
                showDivider = false
            )
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PuiGhostButton("添加到控制中心", Modifier.fillMaxWidth()) {
                        BalanceTileService.requestAdd(context) { result -> tileHint = tileResultText(result) }
                    }
                }
                Text(
                    tileHint ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "也可以在控制中心下拉后点「编辑」手动添加。"
                    } else {
                        "在控制中心下拉后点「编辑」，把「余额」磁贴拖进常用区域。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        PuiGroup("外观") {
            PuiNavRow(
                icon = PuiIcon.Brush,
                title = "外观与导航栏",
                subtitle = "主题、主题色、模糊",
                showDivider = false,
                onClick = onOpenAppearance
            )
        }

        PuiGroup("数据") {
            PuiInfoRow("余额记录", "${vm.history.size} 条")
            PuiInfoRow(
                "上次同步",
                vm.snapshot?.let { Fmt.clock(it.fetchedAt) } ?: "无",
                showDivider = true
            )
            Column(Modifier.padding(16.dp)) {
                PuiGhostButton("清空余额记录", Modifier.fillMaxWidth()) { vm.clearHistory() }
            }
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

        if (hideConfirm) {
            AlertDialog(
                onDismissRequest = { hideConfirm = false },
                title = { Text("开启后台隐藏？") },
                text = {
                    Text(
                        "开启后，应用整体退到后台时「最近任务」里的卡片会直接消失，" +
                            "余额缩略图不会留在任务列表里，也不会被误滑掉。\n\n" +
                            "代价：下次打开相当于重新进入（会回到上次停留的页面）；" +
                            "部分机型（含 ColorOS）在卡片消失后没法再给应用加锁后台，" +
                            "后台常驻可能因此更容易被系统清掉。建议先给卡片加锁再开启；" +
                            "已加锁或不需要加锁的话直接继续即可。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.updateHideFromRecents(true)
                        hideConfirm = false
                    }) { Text("继续") }
                },
                dismissButton = {
                    TextButton(onClick = { hideConfirm = false }) { Text("取消") }
                }
            )
        }

        PuiGroup("关于") {
            PuiInfoRow("应用", "白饭时刻")
            // 「无缝动画」那一行太技术化，已隐藏；自检面板改成**长按版本行**打开
            Box(
                Modifier.combinedClickable(
                    onLongClick = { showSeamless = true },
                    onClick = {}
                )
            ) {
                PuiInfoRow("版本", versionName)
            }
            PuiNavRow(
                icon = PuiIcon.Code,
                title = "GitHub 项目",
                subtitle = "${UpdateClient.OWNER}/${UpdateClient.REPO}",
                onClick = { openUrl(context, UpdateClient.REPO_URL) }
            )
            PuiNavRow(
                icon = PuiIcon.Refresh,
                title = "检查更新",
                subtitle = "查看版本、更新日志与下载",
                onClick = onOpenOta
            )
            PuiInfoRow("接口", "api.deepseek.com")
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "没有自建服务器，也没有统计上报。余额数据只在你刷新时从 DeepSeek 官方接口拉取。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PuiLinkButton("打开用量后台", { openUrl(context, DeepSeekClient.KEYS_URL) })
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

private enum class PendingToggle { None, Live, Normal, Background }

/** 系统「把磁贴加到控制中心」回调的文案（API 33 只有三个结果码，失败走 error 常量）。 */
private fun tileResultText(result: Int): String = when (result) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加到控制中心"
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "磁贴已经在控制中心里了"
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "没有添加，可以下拉控制中心手动添加"
    StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> "应用不在前台，回到应用里再点一次"
    else -> "没有添加成功（结果码 $result），可下拉控制中心手动添加"
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
