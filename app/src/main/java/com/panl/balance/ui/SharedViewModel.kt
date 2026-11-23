package com.panl.balance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panl.balance.PuiApp

/**
 * 取应用级的 [BalanceViewModel] 实例。
 *
 * 主界面（[com.panl.balance.App]）和「刷新与通知」页都走这里，拿到的是同一个实例 ——
 * 否则两个页面各自持有一份定时器与前台服务状态，会互相打架。
 */
@Composable
fun sharedBalanceViewModel(): BalanceViewModel {
    val owner = (LocalContext.current.applicationContext as PuiApp).appViewModelOwner
    return viewModel(viewModelStoreOwner = owner)
}
