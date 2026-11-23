package com.panl.balance.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
