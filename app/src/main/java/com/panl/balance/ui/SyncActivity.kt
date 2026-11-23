package com.panl.balance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * 「刷新与通知」页。
 *
 * 做成独立 Activity 是为了接 ColorOS 的无缝动画（设置页那一行放大成全屏）。
 * 里面的 ViewModel 是**应用级**的（见 [sharedBalanceViewModel]），和主界面共用同一个实例 ——
 * 定时器、前台服务状态不会出现两份。
 */
class SyncActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                SyncScreen(vm = sharedBalanceViewModel(), onBack = { finish() })
            }
        }
    }
}
