package com.panl.balance.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.Intent
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import com.panl.balance.anim.SeamlessView
import com.panl.balance.data.CurrencyBalance
import com.panl.balance.data.DeepSeekClient
import com.panl.balance.data.HistoryEntry
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiPageTitle

/** 首页：余额主卡 + 状态提示 + 账户摘要 + 最近变动。 */
@Composable
fun BalancePage(
    vm: BalanceViewModel,
    topPad: Dp,
    scroll: ScrollState,
    onManageKey: () -> Unit,
) {
    val context = LocalContext.current
    val primary = vm.snapshot?.primary
    val peak = rememberPeakState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = topPad + 12.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "DEEPSEEK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    letterSpacing = 1.6.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                PuiPageTitle("余额")
            }
            RefreshButton(loading = vm.loading) { vm.refresh() }
        }

        BalanceHero(
            snapshot = vm.snapshot,
            loading = vm.loading,
            hasKey = vm.hasKey,
            blue = vm.blueAccent,
            peak = peak
        )

        vm.error?.let { error ->
            ErrorBanner(error = error, onRetry = { vm.refresh() })
        }

        if (vm.lowAlert && primary != null && primary.total < vm.threshold) {
            LowBalanceBanner(
                total = primary.total,
                currency = primary.currency,
                threshold = vm.threshold,
                onTopUp = { openUrl(context, DeepSeekClient.TOP_UP_URL) }
            )
        }

        // 明细入口：这张卡是 ColorOS 无缝动画的源 View，点开会放大成 DetailActivity
        val activity = context.findActivity()
        val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
        // 上下被「顶部渐隐 / 悬浮导航栏」遮住的区域占屏幕的比例，交给无缝动画做末端渐隐
        val screenHdp = LocalConfiguration.current.screenHeightDp.toFloat().coerceAtLeast(1f)
        val navBottomDp = 16f + 56f +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val coverTop = (topPad.value / screenHdp).coerceIn(0f, 0.5f)
        val coverBottom = (navBottomDp / screenHdp).coerceIn(0f, 0.5f)
        SeamlessEntryCard(
            title = "余额明细",
            subtitle = "构成、多币种与全部变动记录",
            trailing = if (vm.history.isEmpty()) null else "${vm.history.size} 条",
            dark = Appearance.dark(),
            blue = vm.blueAccent,
            onOpen = { source ->
                if (activity != null) {
                    val radiusPx = 20f * activity.resources.displayMetrics.density
                    // 把卡片的位置与圆角一并带过去：预测性返回要缩回同一个落点
                    val rect = Rect().also { source.getGlobalVisibleRect(it) }
                    val intent = Intent(activity, DetailActivity::class.java).apply {
                        putExtra(DetailActivity.EXTRA_RECT, rect)
                        putExtra(DetailActivity.EXTRA_RADIUS, radiusPx)
                    }
                    SeamlessView.open(
                        activity = activity,
                        source = source,
                        radiusPx = radiusPx,
                        fillColor = cardColor,
                        intent = intent,
                        coverTop = coverTop,
                        coverBottom = coverBottom
                    )
                }
            }
        )

        // 用量统计：按余额变化推算，数据全部来自本地历史，不需要额外接口
        if (vm.hasKey) {
            UsageCard(
                history = vm.history,
                currency = primary?.currency ?: "CNY",
                peakState = peak
            )
        }

        if (!vm.hasKey) {
            OnboardingCard(
                onAddKey = onManageKey,
                onOpenConsole = { openUrl(context, DeepSeekClient.KEYS_URL) }
            )
        } else {
            PuiGroup("账户") {
                KeySummaryRow(apiKey = vm.apiKey, onManage = onManageKey)
            }
        }

        val others = vm.snapshot?.items.orEmpty()
            .filter { !it.currency.equals(primary?.currency, ignoreCase = true) }
        if (others.isNotEmpty()) {
            PuiGroup("其他币种") {
                others.forEachIndexed { index, item ->
                    CurrencyRow(item, showDivider = index != others.lastIndex)
                }
            }
        }

        if (vm.hasKey) {
            HistoryGroup(history = vm.history)
        }
    }
}

@Composable
private fun KeySummaryRow(apiKey: String, onManage: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onManage)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiIconView(PuiIcon.Key, Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "API 密钥",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                Fmt.maskKey(apiKey) + " · 已加密保存",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PuiLinkButton("管理", onManage)
    }
}

@Composable
private fun CurrencyRow(item: CurrencyBalance, showDivider: Boolean) {
    val parts = buildList {
        if (item.granted > 0.004) add("赠送 " + Fmt.money(item.granted, item.currency))
        if (item.toppedUp > 0.004) add("充值 " + Fmt.money(item.toppedUp, item.currency))
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PuiIconView(PuiIcon.Wallet, Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    currencyName(item.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    parts.joinToString(" · ").ifEmpty { "无余额构成" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Fmt.money(item.total, item.currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) PuiDivider()
    }
}

@Composable
private fun HistoryGroup(history: List<HistoryEntry>) {
    PuiGroup("最近变动") {
        if (history.isEmpty()) {
            Text(
                "每次余额变化会记录在这里，最多 30 条。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val start = (history.size - 6).coerceAtLeast(0)
            val rows = (history.lastIndex downTo start).map { index ->
                val entry = history[index]
                entry to if (index > 0) entry.total - history[index - 1].total else null
            }
            rows.forEachIndexed { rowIndex, pair ->
                val (entry, delta) = pair
                HistoryRow(entry = entry, delta = delta, showDivider = rowIndex != rows.lastIndex)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, delta: Double?, showDivider: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    Fmt.clock(entry.at),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface
                )
                Text(
                    "余额 " + Fmt.money(entry.total, entry.currency),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                delta?.let { Fmt.delta(it, entry.currency) } ?: "首次记录",
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    delta == null -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
                    delta > 0.005 -> scheme.primary
                    else -> scheme.onSurfaceVariant
                }
            )
        }
        if (showDivider) PuiDivider()
    }
}

private fun currencyName(code: String): String = when (code.uppercase()) {
    "CNY" -> "人民币 CNY"
    "USD" -> "美元 USD"
    "EUR" -> "欧元 EUR"
    "HKD" -> "港币 HKD"
    "JPY" -> "日元 JPY"
    else -> code.uppercase()
}

// ---------------- 用量统计 ----------------

/**
 * 用量统计：从本地余额历史里按天累计**消耗金额**。
 *
 * DeepSeek 没有开放 token 用量接口（官方只有 `/user/balance`、`/models` 这几个），所以这里统计的是
 * 「余额下降」：相邻两条记录之间少掉多少，就记到后一条所属的那一天。充值、赠送导致的上涨不计入。
 * 记录条数越多越准，只有一条时全是 0（卡片会提示「记录满两条后开始统计」）。
 */
@Composable
private fun UsageCard(history: List<HistoryEntry>, currency: String, peakState: PeakState) {
    val scheme = MaterialTheme.colorScheme
    val daily = remember(history) { usageByDay(history, DAYS) }
    val labels = remember { dayLabels(DAYS) }
    val today = daily.lastOrNull() ?: 0.0
    val week = daily.sum()
    val peak = daily.maxOrNull() ?: 0.0
    val dim = scheme.onSurfaceVariant.copy(alpha = 0.7f)

    PuiGroup("用量统计") {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "今日消耗",
                        style = MaterialTheme.typography.labelMedium,
                        color = dim
                    )
                    Text(
                        Fmt.money(today, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "近 $DAYS 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = dim
                    )
                    Text(
                        Fmt.money(week, currency),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                daily.forEachIndexed { index, value ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(barHeight(value, peak))
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (index == daily.lastIndex) scheme.primary
                                else scheme.primary.copy(alpha = 0.22f)
                            )
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index, label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == labels.lastIndex) scheme.onSurfaceVariant else dim,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 高峰 / 低峰：决定「现在调用贵不贵」，和上面的柱状图一起看才有意义
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (peakState.peak) Color(0xFFE8A33D) else scheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    peakState.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (peakState.peak) scheme.onSurfaceVariant else scheme.primary
                )
            }

            Text(
                if (history.size < 2) {
                    "记录满两条后开始统计。只累计余额下降，充值与赠送不计入。"
                } else {
                    "按余额下降累计，充值与赠送不计入。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = dim
            )
        }
    }
}

private const val DAYS = 7

/** 近 [days] 天每天的消耗，最后一位是今天。 */
private fun usageByDay(history: List<HistoryEntry>, days: Int): List<Double> {
    val result = MutableList(days) { 0.0 }
    if (history.size < 2) return result

    val starts = (days - 1 downTo 0).map { offset ->
        Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -offset)
        }.timeInMillis
    }

    for (index in 1 until history.size) {
        val drop = history[index - 1].total - history[index].total
        if (drop <= 0.0) continue          // 充值 / 赠送导致的上涨，不算消耗
        val at = history[index].at
        val bucket = starts.indexOfLast { at >= it }
        if (bucket >= 0) result[bucket] += drop
    }
    return result
}

/** 柱高：没有数据时留 4dp 的「地平线」，有数据按峰值等比缩放。 */
private fun barHeight(value: Double, peak: Double): Dp =
    if (peak <= 0.0) 4.dp else (4 + 52 * (value / peak)).dp

/** 近 [days] 天的轴标签，单字，最后一个是「今」。 */
private fun dayLabels(days: Int): List<String> {
    val marks = "日一二三四五六"
    return (days - 1 downTo 0).map { offset ->
        if (offset == 0) {
            "今"
        } else {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            marks[cal.get(Calendar.DAY_OF_WEEK) - 1].toString()
        }
    }
}
