package com.panl.balance

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panl.balance.ui.BalancePage
import com.panl.balance.ui.BalanceViewModel
import com.panl.balance.ui.Appearance
import com.panl.balance.ui.AppearanceScreen
import com.panl.balance.ui.BalanceTheme
import com.panl.balance.ui.OtaScreen
import com.panl.balance.ui.SettingsPage
import com.panl.balance.ui.TopBlurExtra
import com.panl.pui.PuiFloatingNavBar
import com.panl.pui.PuiIcon
import com.panl.pui.PuiTopFade
import com.panl.pui.navBackdropSource
import com.panl.pui.progressiveTopBlur
import com.panl.pui.rememberNavBackdrop
import com.panl.pui.rememberPuiTopBlurHeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用根节点：主题 → 两页 Pager → 悬浮导航栏。
 *
 * 模糊链路（AGSL 顶部渐变 + NavBar 玻璃）需要 API 33（RuntimeShader）与 31（RenderEffect），
 * 低版本自动退化成「纯色渐隐」，主题与组件本身不受影响。
 */
@Composable
fun App(vm: BalanceViewModel = viewModel()) {
    BalanceTheme {
        val pagerState = rememberPagerState(initialPage = vm.startPage) { 2 }
        val scope = rememberCoroutineScope()
        val blurHeight = rememberPuiTopBlurHeight(extra = TopBlurExtra)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val navBlur = with(density) { Appearance.navBlurPx.toDp() }
        val topBlur = with(density) { Appearance.topBlurPx.toDp() }
        val backdrop = rememberNavBackdrop(blurRadius = navBlur)
        val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val balanceScroll = rememberScrollState()
        val settingsScroll = rememberScrollState()

        val lifecycleOwner = LocalLifecycleOwner.current
        var resumed by remember { mutableStateOf(false) }

        // 「软件更新」「外观与导航栏」整页盖在最上层：不销毁下面的 Pager，返回时页面与滚动位置都还在
        var showOta by remember { mutableStateOf(false) }
        var showAppearance by remember { mutableStateOf(false) }
        BackHandler(enabled = showOta) { showOta = false }
        BackHandler(enabled = showAppearance) { showAppearance = false }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        resumed = true
                        vm.onForeground()
                    }
                    Lifecycle.Event.ON_PAUSE -> resumed = false
                    // 退到后台先把没提交的密钥草稿收起来，进程被回收后还能接上
                    Lifecycle.Event.ON_STOP -> vm.persistDraft()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 前台定时刷新：退到后台就停，避免无谓请求
        LaunchedEffect(resumed, vm.autoRefresh, vm.intervalSec, vm.hasKey) {
            if (!resumed || !vm.autoRefresh || !vm.hasKey) return@LaunchedEffect
            while (true) {
                delay(vm.intervalSec * 1000L)
                vm.refresh()
            }
        }

        // 记住停留页：冷启动与「内存过高被回收」后都回到同一页
        LaunchedEffect(pagerState.currentPage) {
            vm.onPageShown(pagerState.currentPage)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (supportsBlur) Modifier.navBackdropSource(backdrop) else Modifier)
                    .then(
                        if (supportsBlur) {
                            Modifier.then(
                                if (Appearance.blurEnabled) {
                                    Modifier.progressiveTopBlur(blurHeight, maxRadius = topBlur, backdrop = backdrop)
                                } else {
                                    Modifier
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
            ) { page ->
                if (page == 0) {
                    BalancePage(
                        vm = vm,
                        topPad = blurHeight,
                        scroll = balanceScroll,
                        onManageKey = {
                            vm.requestKeyFocus()
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }
                    )
                } else {
                    SettingsPage(
                        vm = vm,
                        topPad = blurHeight,
                        scroll = settingsScroll,
                        onOpenOta = { showOta = true },
                        onOpenAppearance = { showAppearance = true }
                    )
                }
            }

            // 顶部渐隐：内容滚进模糊区时自然淡出
            PuiTopFade(height = blurHeight, topAlpha = 0.5f)

            PuiFloatingNavBar(
                items = listOf("余额", "设置"),
                selectedIndex = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                icons = listOf(PuiIcon.Coin, PuiIcon.Settings),
                backdrop = if (supportsBlur) backdrop else null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(
                        bottom = 16.dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
            )

            AnimatedVisibility(
                visible = showOta,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                // 吞掉空白区的点击，避免穿透到下面的 Pager
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    OtaScreen(onBack = { showOta = false })
                }
            }

            AnimatedVisibility(
                visible = showAppearance,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    AppearanceScreen(onBack = { showAppearance = false })
                }
            }
        }
    }
}
