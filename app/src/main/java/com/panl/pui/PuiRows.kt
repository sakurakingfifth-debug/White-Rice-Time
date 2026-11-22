package com.panl.pui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * PUI 设置页的「页面骨架」组件。
 *
 * 一套典型用法：
 *
 * ```kotlin
 * Column(Modifier.padding(horizontal = 16.dp)) {
 *     PuiPageTitle("任务栏客制化")
 *     Spacer(Modifier.height(12.dp))
 *     PuiGroup("尺寸") {
 *         PuiSwitchRow(PuiIcon.Height, "自定义尺寸", "开启后可调高度", on, { on = it }, showDivider = true)
 *         PuiSliderRow(PuiIcon.Height, "高度", height, on, 20, 100, "dp", showDivider = false) { height = it }
 *     }
 * }
 * ```
 *
 * 分隔线由调用方控制（`showDivider`）：**末行传 false，其余传 true**。
 * 增删行时必须同时改相邻行的取值，否则会多一条或少一条线。
 */

/** 页面大标题。左边距 4dp 是相对页面 16dp 内边距的补偿，视觉上对齐卡片内容。 */
@Composable
fun PuiPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/**
 * 分组：灰色小标题 + 一张白卡。
 *
 * 卡片底色用 `surfaceContainerLowest`（亮色纯白 / 暗色 `#1A1A1A`），
 * 圆角走 `MaterialTheme.shapes.large`（PUI = 20dp）。
 */
@Composable
fun PuiGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (!title.isNullOrEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            // 不要再套 clip()：Card 自己按 shape 裁了，多一层就是一个多余图层
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

/** 行首图标：24dp，颜色取壁纸动态主色（见 [LocalWallpaperAccent]）。 */
@Composable
fun PuiRowIcon(icon: PuiIcon, modifier: Modifier = Modifier) {
    PuiIconView(icon = icon, modifier = modifier.size(24.dp))
}

/** 行间分隔线。默认缩进 54dp（让开 24dp 图标 + 14dp 间距 + 16dp 内边距）。 */
@Composable
fun PuiDivider(startIndent: Dp = 54.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
}

/**
 * 开关行：图标 + 标题 + 副标题 + 开关。
 *
 * @param enabled 置灰时整行降到 alpha 0.45、开关不可点（[PuiSwitch] 内部会拦掉点击和触感）。
 *   注意**只联动 UI，不改状态** —— 关掉父级不会清掉子项的值。
 * @param showDivider 末行传 false。
 */
@Composable
fun PuiSwitchRow(
    icon: PuiIcon?,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean,
    enabled: Boolean = true
) {
    val dim = if (enabled) 1f else 0.45f
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PuiRowIcon(icon, Modifier.alpha(dim))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            PuiSwitch(checked = checked, onChange = onChange, enabled = enabled)
        }
        if (showDivider) PuiDivider(if (icon == null) 16.dp else 54.dp)
    }
}

/**
 * 滑块行：图标 + 标签 + 右侧数值 + 滑块。
 *
 * @param suffix 数值后缀（`"dp"` / `"%"`），直接拼在数值后面。
 * @param enabled 置灰时数值与轨道变灰、滑块不可拖。
 * @param step 保留给调用方的刻度语义提示，不参与内部计算（取值吸附由 `roundToInt` + `min/max` 保证）。
 * @param showDivider 末行传 false。
 */
@Composable
fun PuiSliderRow(
    icon: PuiIcon?,
    label: String,
    value: Int,
    enabled: Boolean,
    min: Int,
    max: Int,
    suffix: String,
    showDivider: Boolean,
    step: Int = 10,
    onChange: (Int) -> Unit
) {
    val accent by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pui-slider-accent"
    )
    // 置灰时整行减淡（和 PuiSwitchRow 一致）：只变数值颜色的话，行看起来还是「可操作」，
    // 和旁边的置灰开关行不在一个语义上。
    val dim = if (enabled) 1f else 0.45f
    Column {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(dim)
            ) {
                if (icon != null) {
                    PuiRowIcon(icon)
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$value $suffix",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
            }
            PuiSlider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(dim)
            )
        }
        if (showDivider) PuiDivider(if (icon == null) 16.dp else 54.dp)
    }
}

/** 幽灵按钮：浅底 + 无边框，用于「重置」「强制停止」这类次级操作。高度 46dp、圆角 16dp。 */
@Composable
fun PuiGhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
