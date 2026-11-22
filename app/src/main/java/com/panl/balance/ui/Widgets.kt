package com.panl.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGhostButton

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

/** 「标题 —— 值」的只读信息行，用于设置页与关于页。 */
@Composable
fun PuiInfoRow(
    title: String,
    value: String,
    showDivider: Boolean = true,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
