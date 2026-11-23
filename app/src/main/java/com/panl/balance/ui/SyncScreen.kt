package com.panl.balance.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGhostButton
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitchRow
import com.panl.pui.PuiTopFade
import com.panl.pui.progressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 「刷新与通知」：把原来设置页上的**刷新 / 后台 / 通知**三个分组收成一页。
 *
 * 为什么是主页面里的整页浮层、而不是像外观页那样开独立 Activity：
 * 这些开关连着 `BalanceViewModel` 里的定时器与前台服务 —— 独立 Activity 会拿到**另一个**
 * ViewModel 实例，两边各跑一份轮询，状态还会互相覆盖。浮层用的是同一个实例，天然一致。
 */
@Composable
fun SyncScreen(vm: BalanceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val blurHeight = rememberPuiTopBlurHeight(extra = TopBlurExtra)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var permGranted by remember { mutableStateOf(NotificationPermission.granted(context)) }
    var pendingToggle by remember { mutableStateOf(PendingToggle.None) }
    var hideConfirm by remember { mutableStateOf(false) }

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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permGranted = NotificationPermission.granted(context)
    }

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
                        Modifier.progressiveTopBlur(blurHeight, maxRadius = 16.dp)
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
                PuiPageTitle("刷新与通知")
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
                    subtitle = if (vm.hasKey) {
                        "前台服务每 ${vm.intervalSec} 秒同步一次，锁屏后余额、通知与插件也保持最新"
                    } else {
                        "先回设置页填好 API 密钥"
                    },
                    checked = vm.backgroundRun,
                    onChange = { on ->
                        if (on) requestToggle(PendingToggle.Background) else vm.updateBackgroundRun(false)
                    },
                    enabled = vm.hasKey,
                    showDivider = false
                )
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text(
                        "首次开启时系统会询问「要允许应用一直在后台运行吗」，选「允许」；" +
                            "通知栏会多一条「后台同步」常驻通知，点通知上的「停止」即可关闭。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f)
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
                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                PuiDivider(16.dp)
                PuiInfoRow(
                    "后台同步",
                    if (vm.backgroundRun) "运行中" else "已关闭",
                    showDivider = false,
                    icon = PuiIcon.Bolt
                )
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
                    showDivider = !permGranted,
                    icon = PuiIcon.Bell
                )
                if (!permGranted) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        PuiGhostButton("去系统设置开启", Modifier.fillMaxWidth()) {
                            openNotificationSettings(context)
                        }
                    }
                }
            }
        }

        PuiTopFade(height = blurHeight, topAlpha = 0.5f)
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
}

/** 需要通知权限的三个开关：同意后再落值。 */
internal enum class PendingToggle { None, Live, Normal, Background }
