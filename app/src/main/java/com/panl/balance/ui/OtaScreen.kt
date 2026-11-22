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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.panl.balance.data.ReleaseInfo
import com.panl.balance.data.UpdateClient
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView

/** 「软件更新」页的状态。 */
private sealed interface OtaState {
    data object Loading : OtaState
    data class Done(val info: ReleaseInfo) : OtaState
    data class Failed(val message: String) : OtaState
}

/**
 * 软件更新页：版本对照 + 更新日志 + 检查更新 / GitHub 两个入口。
 *
 * 和 PUI 安装器的同名页面保持一致的信息结构（当前版本 / 最新版本 / 状态 / 当前版本日志），
 * 视觉沿用本项目自己的 PUI 主题，没有引入新组件。
 *
 * 版本信息来自 GitHub Releases 的 `latest` 接口，未鉴权调用（60 次/小时/IP），
 * 打开页面时自动查一次，底部「检查更新」可再查。
 */
@Composable
fun OtaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val client = remember { UpdateClient() }

    val currentVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "—" }
    }

    var state by remember { mutableStateOf<OtaState>(OtaState.Loading) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        state = OtaState.Loading
        state = client.latest().fold(
            onSuccess = { OtaState.Done(it) },
            onFailure = { OtaState.Failed(it.message?.takeIf { m -> m.isNotBlank() } ?: "网络异常") }
        )
    }

    val latest = (state as? OtaState.Done)?.info
    val hasUpdate = latest != null && UpdateClient.compare(latest.version, currentVersion) > 0

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding()
    ) {
        // 顶栏：返回 + 标题
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
                "软件更新",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Hero()

            PuiGroup(title = null) {
                PuiInfoRow("当前版本", currentVersion)
                PuiInfoRow("最新版本", latest?.version ?: "—")
                PuiInfoRow(
                    "状态",
                    when (val s = state) {
                        OtaState.Loading -> "检查中…"
                        is OtaState.Failed -> "检查失败"
                        is OtaState.Done -> if (hasUpdate) "发现 ${s.info.version}" else "已是最新"
                    },
                    showDivider = false,
                    valueColor = when {
                        hasUpdate -> scheme.primary
                        state is OtaState.Failed -> scheme.error
                        else -> scheme.onSurfaceVariant
                    }
                )
            }

            PuiGroup(title = "当前版本日志") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = when (val s = state) {
                            OtaState.Loading -> "正在读取 GitHub 上的版本信息…"
                            is OtaState.Failed -> "读取失败：${s.message}\n\n点下方的「检查更新」重试，或直接去 GitHub 查看。"
                            is OtaState.Done -> s.info.changelog.ifBlank {
                                if (s.info.apiError != null) {
                                    "没有拿到更新说明（${s.info.apiError}），点下方 GitHub 查看完整日志。"
                                } else {
                                    "这个版本没有写更新说明。"
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (hasUpdate && latest != null) {
                PuiPrimaryButton(
                    text = "下载 ${latest.version}",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openUrl(context, latest.apkUrl ?: latest.pageUrl) }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OtaAction(PuiIcon.Refresh, "检查更新", primary = true) { attempt++ }
                Spacer(Modifier.width(44.dp))
                OtaAction(PuiIcon.Code, "GitHub") { openUrl(context, UpdateClient.REPO_URL) }
            }
        }
    }
}

/** 头图：主色渐变 + 应用名，纯装饰。 */
@Composable
private fun Hero() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary,
                        scheme.primary.copy(alpha = 0.78f),
                        scheme.tertiary.copy(alpha = 0.92f),
                    )
                )
            )
    ) {
        // 一道斜向高光，避免整块纯色显得平
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                "白饭时刻",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "PuiBalance · 余额查看器",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

/** 圆形的次要操作入口：图标 + 下方文字。 */
@Composable
private fun OtaAction(
    icon: PuiIcon,
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (primary) scheme.primary else scheme.surfaceContainerHighest)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            PuiIconView(
                icon = icon,
                modifier = Modifier.size(22.dp),
                tint = if (primary) scheme.onPrimary else scheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
    }
}
