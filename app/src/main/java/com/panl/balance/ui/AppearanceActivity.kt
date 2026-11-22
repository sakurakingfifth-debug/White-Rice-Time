package com.panl.balance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * 「外观与导航栏」页。
 *
 * 单独一个 Activity 而不是盖在主页面上的浮层，是因为要接 ColorOS 的无缝动画 ——
 * 那套动画的前提是「一张卡片 View 放大成全屏 Activity」，浮层做不到。
 * 转场由设置页那一行通过 [com.panl.balance.anim.SeamlessView.open] 发起，
 * 返回时系统自己缩回那一行。
 */
class AppearanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                AppearanceScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        /** 起始行的位置与圆角：留给预测性返回缩回同一个落点。 */
        const val EXTRA_RECT = "appearance_row_rect"
        const val EXTRA_RADIUS = "appearance_row_radius"
    }
}
