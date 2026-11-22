package com.panl.balance

import android.app.Application
import com.panl.balance.data.Prefs
import com.panl.balance.perf.MemoryGuard
import com.panl.balance.ui.Appearance
import com.panl.balance.ui.Recents

/**
 * 进程级入口，只做三件事：
 *
 * 1. 装上公平运行内存（HyperOS）的预警 / 查杀广播 —— 必须早于任何界面，
 *    否则进程刚起来就被判定内存异常时收不到通知；
 * 2. 登记 Activity 可见性，内存预警时用来判断「现在能不能安全 GC」；
 * 3. 把系统的 [onTrimMemory] 接到同一条释放路径上，非小米机型也有一套一致的行为。
 *
 * 这里不持有任何业务对象，也不做初始化 IO —— 冷启动首帧不该被拖慢。
 */
class PuiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Appearance.load(this)
        MemoryGuard.install(this)
        MemoryGuard.trackVisibility(this)
        // 后台隐藏：整个应用退到后台时摘掉任务卡片（开关在设置页「后台」里，默认关闭）
        MemoryGuard.onAllStopped = { activity ->
            if (Prefs(this).hideFromRecents) Recents.removeTask(activity)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryGuard.onSystemTrim(level)
    }
}
