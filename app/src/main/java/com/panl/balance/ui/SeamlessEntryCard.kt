package com.panl.balance.ui

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.panl.pui.PuiIcon
import com.panl.pui.PuiRowIcon
import com.panl.pui.PuiTheme

/**
 * 「余额明细」入口卡，同时是 ColorOS 无缝动画的**源 View**。
 *
 * 无缝动画要求传入一个真实、有内容、有尺寸的 `View`，而 Compose 整棵树只是一个 `View`。
 * 所以这里用 [AndroidView] 套一层 [ComposeView]：卡片仍然是 Compose 画的（样式和别处一致），
 * 但在系统看来它就是一张可以放大成全屏的卡片。
 *
 * 卡片内容随主题变化，用 [EntryHost.state] 单向驱动内部那棵组合，避免重建 composition。
 */
private data class EntryState(
    val title: String,
    val subtitle: String,
    val trailing: String?,
    val dark: Boolean,
    val blue: Boolean,
    val icon: PuiIcon,
)

@Composable
fun SeamlessEntryCard(
    title: String,
    subtitle: String,
    trailing: String?,
    dark: Boolean,
    blue: Boolean,
    onOpen: (View) -> Unit,
    modifier: Modifier = Modifier,
    icon: PuiIcon = PuiIcon.Wallet,
) {
    // 内部那棵组合由这个 State 单向驱动，输入变化时只重组、不重建 ComposeView。
    val state = remember {
        mutableStateOf(EntryState(title, subtitle, trailing, dark, blue, icon))
    }

    // 4.10 去掉了 4.0 加的「返回后重建宿主 View」：它在 ON_RESUME 时销毁旧的 ComposeView，
    // 而 ON_RESUME 可能正好落在返回动画中间，源 View 被销毁后系统就退回默认的淡出转场 ——
    // 这是「无缝动画没了」的嫌疑之一。改用文档里的 BUNDLE_LIST_COVER 处理遮挡问题。
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            val view = ComposeView(context)
            view.setContent {
                val s = state.value
                PuiTheme(
                    darkTheme = s.dark,
                    dynamicAccent = false,
                    colorScheme = when {
                        !s.blue -> null
                        s.dark -> DeepSeekDarkColors
                        else -> DeepSeekLightColors
                    }
                ) {
                    EntryContent(state = s, onOpen = { onOpen(view) })
                }
            }
            view
        },
        update = {
            state.value = EntryState(title, subtitle, trailing, dark, blue, icon)
        }
    )
}

@Composable
private fun EntryContent(state: EntryState, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiRowIcon(state.icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            Text(
                state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
        if (!state.trailing.isNullOrBlank()) {
            Text(
                state.trailing,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(Modifier.width(8.dp))
        }
        Chevron(scheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

/** 右向 chevron，与详情页返回箭头同一套线宽。 */
@Composable
private fun Chevron(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val stroke = w * 0.1f
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, w * 0.20f),
            end = Offset(w * 0.64f, w * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.64f, w * 0.50f),
            end = Offset(w * 0.36f, w * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
