package com.panl.balance.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.data.CurrencyBalance
import com.panl.balance.data.HistoryEntry
import com.panl.balance.data.Prefs
import com.panl.balance.notify.BalanceSync
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGroup
import com.panl.pui.PuiTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 余额明细页。
 *
 * 从首页的「明细」卡通过 ColorOS View 无缝动画放大进来（见 `anim/SeamlessView.kt`），
 * 返回时系统会把这一页缩回那张卡片。数据直接读 [Prefs] 缓存，不在页内做轮询。
 *
 * **不做预测性返回**（4.8）：清单里 `enableOnBackInvokedCallback="false"`，
 * 本页也不注册任何返回回调。手势只走系统默认的返回，关闭动画就是 Activity 自己的转场 ——
 * 也就是 `SeamlessView` 配好的「缩回卡片」。
 *
 * 前面几轮试过「注册回调 + 自己画预览」（3.5 / 4.7），在 ColorOS 上会和系统转场叠成
 * 双重收缩 / 两页重影；也试过「注册回调但不画」（4.5），结果是手势完全没有反馈。
 * 结论：要干净的无缝关闭，就别碰预测性返回。
 */
class DetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = Prefs(this)
        setContent {
            val dark = isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            var snapshot by remember { mutableStateOf(prefs.lastSnapshot) }
            var history by remember { mutableStateOf(prefs.history) }
            var loading by remember { mutableStateOf(false) }
            val blue = prefs.blueAccent

            val scheme = when {
                !blue -> null
                dark -> DeepSeekDarkColors
                else -> DeepSeekLightColors
            }

            PuiTheme(
                darkTheme = dark,
                dynamicAccent = prefs.wallpaperIcons,
                colorScheme = scheme
            ) {
                val primary = snapshot?.primary
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .systemBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PuiCircleIconButton(onClick = { finish() }) {
                                BackGlyph(MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "余额明细",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            PuiCircleIconButton(enabled = !loading, onClick = {
                                if (loading) return@PuiCircleIconButton
                                loading = true
                                scope.launch {
                                    runCatching { BalanceSync.fetchAndStore(applicationContext) }
                                    snapshot = prefs.lastSnapshot
                                    history = prefs.history
                                    loading = false
                                }
                            }) {
                                Text(
                                    if (loading) "…" else "刷新",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SummaryCard(primary = primary, blue = blue, dark = dark, snapshot = snapshot)

                        val others = snapshot?.items.orEmpty()
                            .filter { !it.currency.equals(primary?.currency, ignoreCase = true) }
                        if (others.isNotEmpty()) {
                            PuiGroup("其他币种") {
                                others.forEachIndexed { index, item ->
                                    CurrencyRow(item, index != others.lastIndex)
                                }
                            }
                        }

                        PuiGroup("最近变动") {
                            if (history.isEmpty()) {
                                Text(
                                    "每次余额变化会记录在这里，最多 30 条。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                val rows = (history.lastIndex downTo 0).map { index ->
                                    val entry = history[index]
                                    entry to if (index > 0) entry.total - history[index - 1].total else null
                                }
                                rows.forEachIndexed { rowIndex, (entry, delta) ->
                                    HistoryRow(entry, delta, rowIndex != rows.lastIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_RECT = "detail_card_rect"
        const val EXTRA_RADIUS = "detail_card_radius"
    }
}

@Composable
private fun SummaryCard(
    primary: CurrencyBalance?,
    blue: Boolean,
    dark: Boolean,
    snapshot: BalanceSnapshot?,
) {
    val gradient = when {
        blue && dark -> HeroGradientDark
        blue -> HeroGradientLight
        dark -> HeroGreenDark
        else -> HeroGreenLight
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.width * 0.60f,
                center = Offset(size.width * 1.04f, size.height * 0.02f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = size.width * 0.44f,
                center = Offset(size.width * 0.06f, size.height * 1.02f)
            )
        }
        Column(Modifier.padding(20.dp)) {
            Text(
                "总余额",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    primary?.let { Fmt.symbol(it.currency) } ?: "¥",
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    primary?.let { Fmt.amount(it.total) } ?: "—",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Text(
                snapshot?.let { Fmt.updated(it.fetchedAt) } ?: "尚未同步",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            if (primary != null) {
                Spacer(Modifier.height(18.dp))
                CompositionBar(granted = primary.granted, total = primary.total)
                Spacer(Modifier.height(14.dp))
                Row {
                    Stat("赠送余额", Fmt.money(primary.granted, primary.currency), Modifier.weight(1f))
                    Stat("充值余额", Fmt.money(primary.toppedUp, primary.currency), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (primary.total > 0) "共 ${snapshot?.items?.size ?: 1} 个币种账户" else "余额为 0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun CompositionBar(granted: Double, total: Double) {
    val grantedWeight = if (total > 0) (granted / total).toFloat().coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        if (grantedWeight > 0f) {
            Box(
                Modifier
                    .weight(grantedWeight)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
        if (grantedWeight < 1f) {
            Box(
                Modifier
                    .weight(1f - grantedWeight)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

@Composable
private fun CurrencyRow(item: CurrencyBalance, showDivider: Boolean) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    currencyName(item.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    buildList {
                        if (item.granted > 0.004) add("赠送 " + Fmt.money(item.granted, item.currency))
                        if (item.toppedUp > 0.004) add("充值 " + Fmt.money(item.toppedUp, item.currency))
                    }.joinToString(" · ").ifEmpty { "无余额构成" },
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

/** 返回箭头：两段线组成的 chevron，避免为一个图标改动主题库。 */
@Composable
private fun BackGlyph(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val stroke = w * 0.09f
        drawLine(
            color = tint,
            start = Offset(w * 0.62f, w * 0.22f),
            end = Offset(w * 0.36f, w * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, w * 0.50f),
            end = Offset(w * 0.62f, w * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun currencyName(code: String): String = when (code.uppercase(Locale.ROOT)) {
    "CNY" -> "人民币 CNY"
    "USD" -> "美元 USD"
    "EUR" -> "欧元 EUR"
    "HKD" -> "港币 HKD"
    "JPY" -> "日元 JPY"
    else -> code.uppercase(Locale.ROOT)
}
