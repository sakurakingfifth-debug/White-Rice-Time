package com.panl.balance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGhostButton
import com.panl.pui.PuiIcon
import com.panl.pui.PuiRowIcon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.panl.pui.PuiIconView

/*
 * 应用层通用小件：按钮、信息行、说明文字、状态条。
 * 骨架组件（PuiGroup / PuiSwitchRow / PuiSliderRow / PuiGhostButton）来自 PUI 库，
 * 这里只补 PUI 没有的「主按钮 / 文字链接 / 提示条」。
 */

/** 主按钮：实心主色、46dp 高、16dp 圆角，和 PUI 的幽灵按钮同高，并排时不会错位。 */
@Composable
fun PuiPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) scheme.primary else scheme.surfaceContainerHighest)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = scheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
            )
        }
    }
}

/** 文字链接：13sp、主色、8dp 圆角点击区。 */
@Composable
fun PuiLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/** 说明文字：比正文更淡，用于卡片下方的补充说明。 */
@Composable
fun PuiNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/** 「标题 —— 值」的只读信息行，用于设置页与关于页；[icon] 不传就是纯文字行。 */
@Composable
fun PuiInfoRow(
    title: String,
    value: String,
    showDivider: Boolean = true,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: PuiIcon? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PuiRowIcon(icon)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                textAlign = TextAlign.End
            )
        }
        if (showDivider) PuiDivider(startIndent = 16.dp)
    }
}

/** 与 PuiGhostButton 同尺寸的次级按钮，但可传 modifier 参与 weight 分配。 */
@Composable
fun PuiSecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PuiGhostButton(text, modifier, onClick)
}

/**
 * 可点击的导航行：图标 + 标题 + 副标题 + 右侧箭头。
 *
 * 视觉完全照 PuiSwitchRow 的排布（同样的图标位、14dp 间距、14dp 纵向内边距），
 * 只是把开关换成箭头 —— 这样「GitHub 项目 / 检查更新」和其它设置项在同一张卡里不会错位。
 */
@Composable
fun PuiNavRow(
    icon: PuiIcon?,
    title: String,
    subtitle: String = "",
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PuiRowIcon(icon)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Chevron()
        }
        if (showDivider) PuiDivider(if (icon == null) 16.dp else 54.dp)
    }
}

/** 行尾箭头：两段圆头短线，跟 PUI 图标的 1.6dp 视觉重量对齐。 */
@Composable
fun Chevron(
    size: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    /** 朝左用 -1（返回箭头）。 */
    direction: Int = 1,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.8.dp.toPx()
        val tipX = if (direction > 0) w * 0.66f else w * 0.34f
        val tailX = if (direction > 0) w * 0.36f else w * 0.64f
        drawLine(color, Offset(tailX, h * 0.22f), Offset(tipX, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(tipX, h * 0.5f), Offset(tailX, h * 0.78f), stroke, StrokeCap.Round)
    }
}

/** 选中对勾（两段圆头线），比引一个 Material 图标轻。 */
@Composable
fun CheckMark(
    size: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.42f, h * 0.72f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.72f), Offset(w * 0.80f, h * 0.30f), stroke, StrokeCap.Round)
    }
}

/**
 * 可点击的「选择行」：图标 + 标题 + 副标题 + 任意尾随内容（对勾 / 箭头 / 色块）。
 * 和 [PuiNavRow] 同一套排布，只是尾巴交给调用方。
 */
@Composable
fun PuiChoiceRow(
    icon: PuiIcon?,
    title: String,
    subtitle: String = "",
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PuiRowIcon(icon)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (showDivider) PuiDivider(if (icon == null) 16.dp else 54.dp)
    }
}

/**
 * 分段控件：胶囊轨道 + 一块滑动的选中底板。
 *
 * 三段（跟随系统 / 浅色 / 深色）这种「互斥且数量少」的选择用它，
 * 比三个 radio 行省空间。位置用 [Animatable] 而不是 `animateDpAsState`，
 * 中途改选时从当前位置接着滑，不会先跳回旧位置。
 */
@Composable
fun PuiSegmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pad = 3.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHighest)
    ) {
        val itemWidth = (maxWidth - pad * 2) / items.size
        val pos = remember { Animatable(selectedIndex.toFloat()) }
        LaunchedEffect(selectedIndex) {
            pos.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.78f, stiffness = 480f))
        }
        Box(
            Modifier
                .offset(x = pad + itemWidth * pos.value, y = pad)
                .width(itemWidth)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.surfaceContainerLowest)
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) scheme.onSurface else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 细描边圆形图标按钮（刷新用）。 */
@Composable
fun PuiCircleIconButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerLowest)
            .border(0.8.dp, scheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * 圆形刷新按钮：正常是刷新图标，加载时自己转起来。
 *
 * 首页标题栏和「余额明细」页共用同一个 —— 之前明细页是个圆圈里写「刷新」两个字，
 * 和首页那颗图标按钮不是一套。
 */
@Composable
fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh-angle"
    )

    PuiCircleIconButton(enabled = !loading, onClick = onClick) {
        PuiIconView(
            icon = PuiIcon.Refresh,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = if (loading) angle else 0f },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
