package com.panl.balance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 给弹窗背后加模糊。
 *
 * 弹窗是独立窗口（`Dialog`），内容画在它自己的 surface 上，所以不能在 Compose 里
 * 「模糊底层」—— 得让系统把弹窗**后面那个窗口**做一次背景模糊：
 * `FLAG_BLUR_BEHIND` + `setBackgroundBlurRadius()`，Android 12 起支持。
 *
 * 半径直接读 [Appearance.navBlurPx]，也就是外观页「浮窗背景模糊」那个滑杆 ——
 * 拖滑杆，弹窗背后的模糊跟着变，和底部导航栏那一层用同一个数。
 */
@Composable
fun DialogWindowBlur(dim: Float = 0.2f) {
    val view = LocalView.current
    val radiusDp = Appearance.navBlurPx
    val density = LocalDensity.current

    LaunchedEffect(radiusDp) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            // dim 调淡：默认那层灰太重会把模糊压得看不出来
            window.setDimAmount(dim)
            // 真正生效的是 LayoutParams 上的 blurBehindRadius（API 31+）
            window.attributes = window.attributes.apply {
                if (radiusDp > 0) blurBehindRadius = with(density) { radiusDp.dp.roundToPx() }
            }
        }
    }
}

/**
 * 应用统一的弹窗卡片。
 *
 * 不用 `AlertDialog`：它的按钮区是一个右对齐的 FlowRow，`fillMaxWidth` 在里面不生效，
 * 做不出「左右两个等宽实心块」的排版。这里用 `Dialog` + 自己的 `Surface` 卡片，
 * 内边距、圆角、按钮排布全部可控，视觉上仍是 Material3 的弹窗。
 */
@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "取消",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    maxContentHeight: Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // 进出场动画：从中间轻微放大弹出（带一点弹簧回弹），关闭时先反向收一下再真正关窗。
    // 关键是「真正的 onDismissRequest 要等动画放完再调」，否则父级状态一变窗口立刻没了，动画看不到。
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var pendingClose by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun closeWith(action: () -> Unit) {
        if (!visibleState.targetState) return
        pendingClose = action
        visibleState.targetState = false
    }

    Dialog(onDismissRequest = { closeWith(onDismissRequest) }) {
        // 背景模糊必须在弹窗窗口内设置，所以放在 Dialog 的最外层
        DialogWindowBlur()

        LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
            if (visibleState.isIdle && !visibleState.currentState) {
                val action = pendingClose
                pendingClose = null
                action?.invoke()
            }
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(tween(durationMillis = 150)),
            exit = scaleOut(targetScale = 0.88f, animationSpec = tween(durationMillis = 240)) +
                fadeOut(tween(durationMillis = 200))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerLowest
            ) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    // 正文自己滚，并在真正还滚得动的那一侧做渐进遮罩 —— 不留一刀切的硬边
                    val scrollState = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxContentHeight)
                            // 遮罩必须在 verticalScroll **外面**：放里面量到的是整段内容高度，
                            // 渐变会被摊平到看不见
                            .fadeEdges(scrollState)
                            .verticalScroll(scrollState)
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            content = content
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DialogButtons(
                        confirmText = confirmText,
                        onConfirm = { closeWith(onConfirm) },
                        dismissText = dismissText,
                        onDismiss = { closeWith(onDismiss ?: onDismissRequest) },
                        destructive = destructive,
                        confirmEnabled = confirmEnabled
                    )
                }
            }
        }
    }
}

/**
 * 弹窗底部按钮：左右两个等宽的圆角实心按钮（取消是浅底，确认是主题色 / 危险操作是红色）。
 *
 * 和 Material 默认的文字按钮不一样 —— 文字按钮在深色底上太轻，
 * 而且「取消」和「确认」视觉权重几乎一样，容易误点。这里用实心色块把主次分开。
 *
 * @param dismissText 传 null 就只留一个整宽按钮（例如自检弹窗的「关闭」）
 * @param destructive 危险操作（彻底删除之类）用红色确认键
 */
@Composable
fun DialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "取消",
    onDismiss: () -> Unit = {},
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (dismissText != null) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    // 用 onSurface 的极低透明度做浅底：主题里 surfaceContainer* 几档都接近纯白，
                    // 放在卡片的 surfaceContainerLowest 上几乎看不出边界
                    .background(scheme.onSurface.copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dismissText,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface
                )
            }
        }

        val bg = if (destructive) scheme.error else scheme.primary
        val fg = if (destructive) scheme.onError else scheme.onPrimary
        Box(
            Modifier
                .weight(1f)
                .height(46.dp)
                .clip(shape)
                .background(if (confirmEnabled) bg else bg.copy(alpha = 0.4f))
                .clickable(enabled = confirmEnabled, onClick = onConfirm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                confirmText,
                style = MaterialTheme.typography.titleSmall,
                color = fg.copy(alpha = if (confirmEnabled) 1f else 0.7f)
            )
        }
    }
}

/**
 * 上下边缘渐进遮罩：只在**还能滚**的那一侧淡出，滚到头的那一侧就是实边。
 *
 * 用 [BlendMode.DstIn] 拿一张竖向渐变当 alpha 蒙版；因为要按蒙版裁剪自己的绘制内容，
 * 得先 `CompositingStrategy.Offscreen` 把内容单独渲染到一层，否则会盖住弹窗外的像素。
 */
fun Modifier.fadeEdges(scroll: ScrollState, fade: Dp = 20.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val px = fade.toPx()
        val h = size.height
        if (h <= px * 2) return@drawWithContent
        val topAlpha = if (scroll.canScrollBackward) 0f else 1f
        val bottomAlpha = if (scroll.canScrollForward) 0f else 1f
        if (topAlpha == 1f && bottomAlpha == 1f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = topAlpha),
                px / h to Color.Black,
                (h - px) / h to Color.Black,
                1f to Color.Black.copy(alpha = bottomAlpha)
            ),
            blendMode = BlendMode.DstIn
        )
    }
