package com.panl.balance

import android.app.Application
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.panl.balance.data.Prefs
import com.panl.balance.perf.MemoryGuard
import com.panl.balance.data.Trash
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

    /**
     * 应用级 ViewModel 宿主。
     *
     * 「刷新与通知」是独立 Activity，但它的开关驱动的是同一个 [BalanceViewModel]
     * （定时器 + 前台服务）。如果各建一份，两个实例会各跑一份轮询、状态互相覆盖 ——
     * 所以把 ViewModel 挂到 Application 上，主界面和这个页面共用同一个实例。
     */
    private val appViewModelStore = ViewModelStore()

    val appViewModelOwner: ViewModelStoreOwner = object : ViewModelStoreOwner,
        HasDefaultViewModelProviderFactory {
        override val viewModelStore: ViewModelStore get() = appViewModelStore
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = AndroidViewModelFactory.getInstance(this@PuiApp)
    }

    override fun onCreate() {
        super.onCreate()
        Appearance.load(this)
        Trash.load(this)
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
