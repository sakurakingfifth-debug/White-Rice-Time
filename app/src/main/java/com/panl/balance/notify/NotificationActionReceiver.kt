package com.panl.balance.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.panl.balance.data.Prefs
import com.panl.balance.widget.BalanceWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 通知上的动作：后台拉一次余额（刷新），或关掉「后台常驻」。 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> refresh(context)
            ACTION_STOP_BACKGROUND -> stopBackground(context)
            else -> Unit
        }
    }

    private fun refresh(context: Context) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) { BalanceSync.fetchAndStore(app) }
                BalanceNotifications.sync(app)
                // 后台常驻开着时，服务自己的那条通知也要跟着更新
                if (BalanceWatchService.running) BalanceNotifications.refreshService(app)
                BalanceWidgets.updateAll(app)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    /** 从后台常驻通知上「停止」：等同于把设置页的开关关掉。 */
    private fun stopBackground(context: Context) {
        val app = context.applicationContext
        Prefs(app).backgroundRun = false
        BalanceWatchService.stop(app)
    }

    companion object {
        const val ACTION_REFRESH = "com.panl.balance.action.NOTIFICATION_REFRESH"
        const val ACTION_STOP_BACKGROUND = "com.panl.balance.action.STOP_BACKGROUND"
    }
}
