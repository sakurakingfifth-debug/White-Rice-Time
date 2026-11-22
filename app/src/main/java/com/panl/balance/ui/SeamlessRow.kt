package com.panl.balance.ui

import android.content.Intent
import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.panl.balance.anim.SeamlessView

/**
 * 把任意 Compose 内容包成「无缝动画的源 View」。
 *
 * 无缝动画要求传一个真实、有尺寸的 `View`，而整棵 Compose 树只有一个 View；
 * 所以这里用 [AndroidView] 套一层 [ComposeView]：内容还是 Compose 画的（样式和别处一致），
 * 在系统看来它就是一张能放大成全屏的卡片。
 *
 * 内层是一棵独立的 composition，拿不到外层的 `MaterialTheme`，所以内容自己套 [BalanceTheme]。
 *
 * @param onOpen 点击时回调，参数是参与动画的源 View
 */
@Composable
fun SeamlessHost(
    onOpen: (View) -> Unit,
    content: @Composable (open: () -> Unit) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            val view = ComposeView(context)
            view.setContent {
                BalanceTheme {
                    content { onOpen(view) }
                }
            }
            view
        }
    )
}

/**
 * 从设置页那一行无缝放大到 [AppearanceActivity]。
 *
 * @param radiusPx 行的圆角（px）—— 系统用它决定起始裁剪形状
 * @param fillColor 行以外的补边色，取卡片底色
 */
fun openAppearanceSeamless(activity: android.app.Activity, source: View, radiusPx: Float, fillColor: Int) {
    val rect = Rect().also { source.getGlobalVisibleRect(it) }
    val intent = Intent(activity, AppearanceActivity::class.java).apply {
        putExtra(AppearanceActivity.EXTRA_RECT, rect)
        putExtra(AppearanceActivity.EXTRA_RADIUS, radiusPx)
    }
    SeamlessView.open(
        activity = activity,
        source = source,
        radiusPx = radiusPx,
        fillColor = fillColor,
        intent = intent
    )
}
