package com.panl.balance.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.panl.balance.data.Prefs
import com.panl.balance.widget.BalanceWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 「允许应用一直在后台运行」的实现：一个前台服务，按刷新间隔在后台拉余额。
 *
 * 为什么必须是前台服务：
 * - 纯后台定时（`WorkManager` 周期任务最短 15 分钟、`AlarmManager` 在 Doze 下被推迟）
 *   撑不起 30–300 秒的刷新间隔；
 * - 前台服务让进程进入 active 状态，锁屏后网络与定时器都不会被冻结；
 * - 代价是必须挂一条不可撤销的通知（见 [BalanceNotifications.serviceNotification]），
 *   所以开关打开后通知栏会多一条「后台同步」，这是 Android 的硬性要求，不是本应用多事。
 *
 * 前台服务类型用 `specialUse` 而不是 `dataSync`：Android 15 起 `dataSync` 前台服务
 * 24 小时内累计只能跑 6 小时，到点会被系统强制停掉；`specialUse` 没有这个时间上限，
 * 适合「长期挂着看余额」这种用法。
 *
 * ColorOS / 欧加系机型上，第三方应用首次拉起前台服务时系统会弹
 * 「要允许应用一直在后台运行吗？」，用户选「允许」后服务才能长期存活 —— 这个弹窗由系统弹出，
 * 应用侧不需要也不应该自己伪造。
 */
class BalanceWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        // onCreate 里就要进前台：startForegroundService 之后 5 秒内不调用会 ANR / 抛异常
        enterForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        // 开关关了或密钥没了就直接收摊，不留一条孤儿通知
        if (!prefs.backgroundRun || prefs.apiKey.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        enterForeground()
        restartLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        loop = null
        scope.cancel()
        running = false
        super.onDestroy()
    }

    // ---------------- 内部 ----------------

    private fun enterForeground() {
        val notification = BalanceNotifications.serviceNotification(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    BalanceNotifications.ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(BalanceNotifications.ID_SERVICE, notification)
            }
        }.onFailure { Log.e(TAG, "startForeground failed", it) }
    }

    private fun restartLoop() {
        loop?.cancel()
        loop = scope.launch {
            // 先把缓存与界面拉齐（30 秒内的数据不重复请求，避免开关来回切时狂刷接口）
            syncOnce(force = false)
            while (isActive) {
                // 每轮都重新读间隔，设置页拖滑杆能立刻生效
                val interval = Prefs(this@BalanceWatchService).intervalSec
                delay(interval * 1000L)
                syncOnce(force = true)
            }
        }
    }

    private suspend fun syncOnce(force: Boolean) {
        val prefs = Prefs(this)
        if (!prefs.backgroundRun) {
            stopSelf()
            return
        }
        val last = prefs.lastSnapshot?.fetchedAt ?: 0L
        if (force || System.currentTimeMillis() - last > FRESH_WINDOW_MS) {
            runCatching { BalanceSync.fetchAndStore(this) }
                .onFailure { Log.w(TAG, "background sync failed", it) }
        }
        // 三处一起刷新：两条可选通知 + 服务自己的常驻通知 + 桌面插件
        runCatching { BalanceNotifications.sync(this) }
        BalanceNotifications.refreshService(this)
        runCatching { BalanceWidgets.updateAll(this) }
    }

    companion object {
        private const val TAG = "BalanceWatchService"
        private const val FRESH_WINDOW_MS = 30_000L

        /** 进程内标记，避免每次回到前台都重复 start（重复 start 会重置计时）。 */
        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BalanceWatchService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.e(TAG, "start failed", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BalanceWatchService::class.java)) }
                .onFailure { Log.e(TAG, "stop failed", it) }
        }

        /** 回到前台时补拉起：进程被系统回收后服务不会自己回来。 */
        fun ensureRunning(context: Context) {
            if (running) return
            if (!Prefs(context).backgroundRun) return
            start(context)
        }
    }
}
