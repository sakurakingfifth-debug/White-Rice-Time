package com.panl.balance.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.panl.balance.data.Prefs
import com.panl.balance.widget.BalanceWidgets

/** 开机后从缓存恢复通知与插件，并在「后台常驻」开启时把前台服务拉起来。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        runCatching { BalanceNotifications.sync(app) }
        runCatching { BalanceWidgets.updateAll(app) }
        // 开机自启：系统允许从 BOOT_COMPLETED 拉起 specialUse 类型的前台服务
        if (Prefs(app).backgroundRun && Prefs(app).apiKey.isNotBlank()) {
            runCatching { BalanceWatchService.start(app) }
        }
    }
}
