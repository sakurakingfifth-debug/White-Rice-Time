package com.panl.pui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 「刻度」类触感：滑块每跨一格、页面滑到顶 / 底各用一次。
 *
 * ## 为什么不用 Compose 的 SegmentTick / SegmentFrequentTick
 *
 * 这两个对应 `HapticFeedbackConstants.SEGMENT_TICK(26)` 和 `SEGMENT_FREQUENT_TICK(27)`，
 * 都是 **API 34 才新增**的常量。部分 OEM 没给它们做振动映射，`performHapticFeedback`
 * 会退回一个通用兜底效果 —— OPPO 上实测就是这样（Android 17 / PMX110）：
 *
 * ```
 * effect=Prebaked{effect=TEXTURE_TICK, strength=MEDIUM, fallback=true}
 * reason=performHapticFeedback(constant=27)
 * ```
 *
 * 注意 `fallback=true`：说明厂商没有专用波形，用的是「纹理刻度」兜底，振幅极低，
 * 高频连续触发时手几乎感觉不到。**这不是权限或系统开关的问题** —— 请求已经送达
 * VibratorManagerService，只是被映射成了一个很弱的效果。
 *
 * ## 改用 CLOCK_TICK
 *
 * `CLOCK_TICK(4)` 从 API 3 就存在，AOSP 的 `NumberPicker` / `TimePicker` 一直用它，
 * 各家 OEM 都有真实映射；语义也一致（一次干脆的「哒」）。
 * 它同样走 `performHapticFeedback`，所以照样受系统「触感反馈」总开关约束，
 * 也不需要 `VIBRATE` 权限。
 *
 * 如果哪天又遇到某个厂商连 CLOCK_TICK 都不映射，下一步只能直接拿
 * `VibratorManager.defaultVibrator` 调 `VibrationEffect.createOneShot(ms, amplitude)`，
 * 那需要往清单里加 `android.permission.VIBRATE`。
 */
@Composable
fun rememberTickHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        val tick: () -> Unit = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        tick
    }
}
