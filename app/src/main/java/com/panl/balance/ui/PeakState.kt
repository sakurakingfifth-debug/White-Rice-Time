package com.panl.balance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.panl.balance.data.PeakHours
import kotlinx.coroutines.delay

/** 高峰 / 低峰的展示状态。 */
data class PeakState(
    val peak: Boolean,
    val summary: String,
) {
    /** 卡片角标上的两字标签。 */
    val label: String get() = if (peak) "高峰" else "低峰"
}

/**
 * 半分钟重算一次高峰状态：页面停在前台跨过 9:00 / 12:00 这类边界时，角标要自己翻过来，
 * 不能等用户手动刷新。30 秒的粒度对「几点切换」这种量级足够了。
 */
@Composable
fun rememberPeakState(): PeakState {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return remember(now) {
        PeakState(peak = PeakHours.isPeak(now), summary = PeakHours.summary(now))
    }
}
