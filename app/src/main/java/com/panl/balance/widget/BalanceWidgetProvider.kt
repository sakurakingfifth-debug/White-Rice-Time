package com.panl.balance.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * 所有尺寸插件共用的接收器基类。
 *
 * `onUpdate` 只负责用缓存渲染（必须快），真正的网络同步放到 `onReceive` 的 `goAsync()` 里，
 * 这样系统回调不会因为等待网络被 ANR 判定。
 */
open class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                BalanceWidgets.updateAll(context)
                BalanceWidgets.refreshAsync(context, goAsync())
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                BalanceWidgets.refreshAsync(context, goAsync())
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        BalanceWidgets.render(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        const val ACTION_REFRESH = "com.panl.balance.action.WIDGET_REFRESH"
    }
}

/** 1×1：只有金额。 */
class Widget1x1 : BalanceWidgetProvider()

/** 2×1：金额 + 标题。 */
class Widget2x1 : BalanceWidgetProvider()

/** 2×2：金额 + 赠送/充值。 */
class Widget2x2 : BalanceWidgetProvider()

/** 4×2：金额 + 状态 + 构成。 */
class Widget4x2 : BalanceWidgetProvider()

/** 4×4：完整面板 + 构成条 + 手动刷新。 */
class Widget4x4 : BalanceWidgetProvider()
