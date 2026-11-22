package com.panl.balance.ui

import android.app.Activity
import android.util.Log

/**
 * 后台隐藏：应用整体退到后台时，把任务卡片从「最近任务」里摘掉。
 *
 * 一开始照 GKD 的做法用 `ActivityManager.appTasks` + `setExcludeFromRecents(true)`，
 * 实测（Android 16 / API 36）不可靠：这个 API 只是改任务根 Intent 上的
 * `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` 标志（可以在 dumpsys 里看到 flg 从 0x10000000 变成
 * 0x10800000），而最近任务列表在任务**创建时**就已经把卡片加进去了，事后改标志不会把已有卡片摘掉；
 * 重新启动又可能生成新任务、拿到旧任务引用，标志直接丢了。
 *
 * 所以这里换成确定性的做法：最后一个界面 stop 时 [Activity.finishAndRemoveTask]，
 * 卡片和任务一起消失。代价是下次打开是冷启动，但页面、余额快照与密钥草稿都已经持久化，
 * 冷启动实测 270ms 左右，体验上就是「重开了一下」。
 *
 * 只在整个应用不可见时调用（见 `MemoryGuard.onAllStopped`）—— MainActivity 进详情页时
 * 也会 stop，那会儿摘任务会把正在看的页面弄没。
 */
object Recents {

    private const val TAG = "Recents"

    fun removeTask(activity: Activity) {
        runCatching { activity.finishAndRemoveTask() }
            .onSuccess { Log.i(TAG, "已移除任务卡片（后台隐藏）") }
            .onFailure { Log.w(TAG, "移除任务卡片失败", it) }
    }
}
