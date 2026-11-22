package com.panl.balance.notify

import android.content.Context
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.data.DeepSeekClient
import com.panl.balance.data.Prefs

/**
 * 通知与桌面插件共用的「拉一次余额并落盘」。
 *
 * 短超时是有意为之：调用方都在广播的 `goAsync()` 预算内（约 10 秒），
 * 宁可这次失败保留旧数据，也不能让系统判定广播超时。
 */
object BalanceSync {

    suspend fun fetchAndStore(context: Context): BalanceSnapshot? {
        val prefs = Prefs(context)
        val key = prefs.apiKey
        if (key.isBlank()) return null
        val result = DeepSeekClient(connectTimeoutMs = 5_000, readTimeoutMs = 7_000).fetch(key)
        return result.getOrNull()?.also { prefs.lastSnapshot = it }
    }
}
