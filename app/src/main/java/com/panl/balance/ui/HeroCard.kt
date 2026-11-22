package com.panl.balance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panl.balance.data.ApiError
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.data.Prefs
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView

/**
 * 首页主卡片：品牌渐变底 + 大号余额 + 赠送/充值构成。
 *
 * 这是整个应用唯一一块「重色」区域，其余页面保持 PUI 的浅灰底白卡，
 * 让余额本身成为视觉重心。
 */
@Composable
fun BalanceHero(
    snapshot: BalanceSnapshot?,
    loading: Boolean,
    hasKey: Boolean,
    modifier: Modifier = Modifier,
    blue: Boolean = true,
    peak: PeakState? = null,
) {
    val dark = Appearance.dark()
    val primary = snapshot?.primary
    val amount = primary?.let { Fmt.amount(it.total) } ?: "—"
    val symbol = primary?.let { Fmt.symbol(it.currency) } ?: "¥"
    val integer = amount.substringBefore(".")
    val decimals = amount.substringAfter(".", "")
    // 渐变跟着主题主色走。默认（DeepSeek 蓝）用它原本的蓝→紫品牌渐变；
    // 换成自定义 ARGB / 壁纸取色时改成同色系压暗一档 —— 绿配紫、红配紫那种跨色相渐变很脏。
    val scheme = MaterialTheme.colorScheme
    val gradient = when {
        Appearance.accentMode == Prefs.MODE_DEFAULT && dark -> HeroGradientDark
        Appearance.accentMode == Prefs.MODE_DEFAULT -> HeroGradientLight
        dark -> listOf(scheme.primaryContainer, scheme.primaryContainer.dim(0.18f))
        else -> listOf(scheme.primary, scheme.primary.dim(0.18f))
    }

    val subtitle = when {
        loading && snapshot == null -> "正在同步…"
        snapshot != null -> Fmt.relative(snapshot.fetchedAt)
        !hasKey -> "配置密钥后显示余额"
        else -> "点右上角刷新重试"
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
    ) {
        // 两团柔和的高光（径向渐变收边），避免大面积纯渐变显得平
        Canvas(Modifier.matchParentSize()) {
            val topRight = Offset(size.width * 1.02f, size.height * 0.02f)
            val topRightR = size.width * 0.66f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = topRight,
                    radius = topRightR
                ),
                radius = topRightR,
                center = topRight
            )
            val bottomLeft = Offset(size.width * 0.04f, size.height * 1.04f)
            val bottomLeftR = size.width * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = bottomLeft,
                    radius = bottomLeftR
                ),
                radius = bottomLeftR,
                center = bottomLeft
            )
        }

        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "总余额",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.74f),
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = when {
                        loading -> "同步中"
                        snapshot == null -> "未获取"
                        snapshot.available -> "可用"
                        else -> "不可用"
                    },
                    dotColor = when {
                        snapshot == null -> Color.White.copy(alpha = 0.6f)
                        snapshot.available -> Color(0xFF9CF5C0)
                        else -> Color(0xFFFFC2BC)
                    }
                )
                // 高峰 / 低峰角标：决定现在调用贵不贵（低峰单价是高峰的一半）
                peak?.let {
                    Spacer(Modifier.width(6.dp))
                    StatusPill(
                        text = it.label,
                        dotColor = if (it.peak) Color(0xFFFFD48A) else Color(0xFF9CF5C0)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    symbol,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.padding(bottom = 8.dp, end = 2.dp)
                )
                Text(
                    integer,
                    style = TextStyle(
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp
                    ),
                    color = Color.White
                )
                if (decimals.isNotEmpty()) {
                    Text(
                        ".$decimals",
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 7.dp, start = 1.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            if (primary != null && primary.total > 0) {
                Spacer(Modifier.height(18.dp))
                CompositionBar(granted = primary.granted, toppedUp = primary.toppedUp, total = primary.total)
                Spacer(Modifier.height(14.dp))
                Row {
                    HeroStat(
                        label = "赠送余额",
                        value = Fmt.money(primary.granted, primary.currency),
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        label = "充值余额",
                        value = Fmt.money(primary.toppedUp, primary.currency),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (primary != null) {
                Spacer(Modifier.height(18.dp))
                Row {
                    HeroStat("赠送余额", Fmt.money(primary.granted, primary.currency), Modifier.weight(1f))
                    HeroStat("充值余额", Fmt.money(primary.toppedUp, primary.currency), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, dotColor: Color) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
private fun CompositionBar(granted: Double, toppedUp: Double, total: Double) {
    val grantedRatio = if (total > 0) (granted / total).coerceIn(0.0, 1.0).toFloat() else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        if (grantedRatio > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(grantedRatio)
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .weight((1f - grantedRatio).coerceAtLeast(0.0001f))
                .background(Color.White.copy(alpha = 0.92f))
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

/** 请求失败的提示条：错误文案 + 可选的服务端原因 + 重试。 */
@Composable
fun ErrorBanner(error: ApiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.errorContainer)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                error.message,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onErrorContainer
            )
            if (!error.detail.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    error.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onErrorContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PuiLinkButton("重试", onRetry, color = scheme.onErrorContainer)
    }
}

/** 低余额提示：琥珀色，和错误提示区分开（这是提醒不是故障）。 */
@Composable
fun LowBalanceBanner(
    total: Double,
    currency: String,
    threshold: Int,
    onTopUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = Appearance.dark()
    val bg = if (dark) WarnBgDark else WarnBgLight
    val fg = if (dark) WarnFgDark else WarnFgLight
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "余额偏低",
                style = MaterialTheme.typography.titleSmall,
                color = fg
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "剩余 ${Fmt.money(total, currency)}，低于提醒阈值 ${Fmt.money(threshold.toDouble(), currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = 0.82f)
            )
        }
        PuiLinkButton("去充值", onTopUp, color = fg)
    }
}

/** 未配置密钥时的引导卡。 */
@Composable
fun OnboardingCard(
    onAddKey: () -> Unit,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuiGroup(title = "开始使用", modifier = modifier) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PuiIconView(PuiIcon.Key, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "还没有配置 API Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "在 DeepSeek 开放平台创建一个密钥后粘贴进来，即可读取账户余额。密钥经 Android Keystore 加密，只保存在本机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PuiPrimaryButton("填写密钥", Modifier.fillMaxWidth(), onClick = onAddKey)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PuiLinkButton("打开 DeepSeek 开放平台", onOpenConsole)
            }
        }
    }
}

/** 同色系压暗：RGB 各乘一个系数，色相不变。 */
private fun Color.dim(factor: Float): Color = Color(
    red = red * (1f - factor),
    green = green * (1f - factor),
    blue = blue * (1f - factor),
    alpha = alpha,
)
