package com.panl.balance.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.panl.balance.R

/**
 * 「鲸元卷」4×2 纯图片插件。
 *
 * 没有数据、没有刷新、没有点击 —— 内容就是一张固定插画（`drawable-nodpi/widget_banknote.jpg`），
 * 所以：
 *
 * - `updatePeriodMillis="0"`：系统不用定期唤醒应用；
 * - `onUpdate` 只把布局挂上去，不做任何渲染；
 * - 不设 `setOnClickPendingIntent`：需求是「只展示这张图」，点了不该有副作用。
 */
class WidgetBanknote4x2 : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_banknote_4x2)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }
}
