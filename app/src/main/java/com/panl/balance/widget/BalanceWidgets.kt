package com.panl.balance.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.panl.balance.MainActivity
import com.panl.balance.R
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.notify.BalanceSync
import com.panl.balance.data.Prefs
import com.panl.balance.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DecimalFormat

/**
 * 桌面插件的渲染与刷新。
 *
 * 数据来源分两层：
 * 1. 先用 [Prefs.lastSnapshot]（应用或上一次插件请求缓存的余额）立刻渲染，保证桌面切换时不留白；
 * 2. 再在后台发一次请求，成功后写回缓存并重绘。系统每 30 分钟回调一次 `onUpdate`，
 *    4×4 插件右下角还有手动「刷新」。
 *
 * 网络请求放在 `goAsync()` 里跑，超时 9 秒（`goAsync` 的广播预算约 10 秒），
 * 失败就保留旧数据，不会把界面刷成错误页。
 */
object BalanceWidgets {

    private const val REFRESH_TIMEOUT_MS = 9_000L
    private const val COLOR_BLUE = 0xFF4D6BFE.toInt()
    private const val COLOR_GREEN = 0xFF00A05A.toInt()
    private const val COLOR_BLUE_DARK = 0xFF9FB2FF.toInt()
    private const val COLOR_GREEN_DARK = 0xFF5ADB9A.toInt()

    private val providers = listOf(
        Widget1x1::class.java,
        Widget2x1::class.java,
        Widget2x2::class.java,
        Widget4x2::class.java,
        Widget4x4::class.java,
    )

    /** 深色卡片上用浅一档的品牌色，否则 #4D6BFE 在 #1A1A1A 上偏暗。 */
    internal fun accentColor(context: Context, blueAccent: Boolean): Int {
        val night = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when {
            blueAccent && night -> COLOR_BLUE_DARK
            blueAccent -> COLOR_BLUE
            night -> COLOR_GREEN_DARK
            else -> COLOR_GREEN
        }
    }

    /** 应用内余额/密钥/强调色变化后调用，把已放置的所有尺寸插件刷一遍。 */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        providers.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(app, provider))
            if (ids.isNotEmpty()) render(app, manager, ids)
        }
    }

    fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = Prefs(context)
        val snapshot = prefs.lastSnapshot
        val hasKey = prefs.apiKey.isNotBlank()
        val accent = accentColor(context, prefs.blueAccent)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val refresh = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, BalanceWidgetProvider::class.java)
                .setAction(BalanceWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ids.forEach { id ->
            val layout = manager.getAppWidgetInfo(id)?.initialLayout ?: return@forEach
            manager.updateAppWidget(
                id,
                build(context, layout, snapshot, hasKey, accent, openApp, refresh, prefs.blueAccent)
            )
        }
    }

    /** 广播里触发一次后台同步，结束后重绘所有插件。 */
    fun refreshAsync(context: Context, pending: BroadcastReceiver.PendingResult) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { BalanceSync.fetchAndStore(app) }
                updateAll(app)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    internal fun build(
        context: Context,
        layoutId: Int,
        snapshot: BalanceSnapshot?,
        hasKey: Boolean,
        accent: Int,
        openApp: PendingIntent,
        refresh: PendingIntent,
        blueAccent: Boolean = true,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val primary = snapshot?.primary
        val compact = layoutId == R.layout.widget_1x1 || layoutId == R.layout.widget_2x1

        views.setTextViewText(
            R.id.widget_amount,
            when {
                primary == null -> "—"
                compact -> compactMoney(primary.total, primary.currency)
                else -> Fmt.money(primary.total, primary.currency)
            }
        )
        views.setTextColor(R.id.widget_amount, accent)

        if (layoutId == R.layout.widget_2x2 ||
            layoutId == R.layout.widget_4x2 ||
            layoutId == R.layout.widget_4x4
        ) {
            // 2×2 每列只有约 60dp，带分位的小数放不下，用紧凑格式
            val small = layoutId == R.layout.widget_2x2
            fun stat(value: Double, currency: String) =
                if (small) compactMoney(value, currency) else Fmt.money(value, currency)
            views.setTextViewText(
                R.id.widget_granted,
                primary?.let { stat(it.granted, it.currency) } ?: "—"
            )
            views.setTextViewText(
                R.id.widget_topped,
                primary?.let { stat(it.toppedUp, it.currency) } ?: "—"
            )
        }

        if (layoutId == R.layout.widget_4x2 || layoutId == R.layout.widget_4x4) {
            val (text, colorRes) = when {
                snapshot == null -> "● 未获取" to R.color.widget_dim
                snapshot.available -> "● 可用" to R.color.widget_ok
                else -> "● 不可用" to R.color.widget_bad
            }
            views.setTextViewText(R.id.widget_status, text)
            views.setTextColor(R.id.widget_status, ContextCompat.getColor(context, colorRes))
        }

        if (layoutId == R.layout.widget_4x4) {
            views.setTextViewText(
                R.id.widget_updated,
                snapshot?.let { Fmt.relative(it.fetchedAt) } ?: "尚未同步"
            )
            views.setTextViewText(
                R.id.widget_footer,
                if (hasKey) context.getString(R.string.widget_brand) else "打开应用配置密钥"
            )
            views.setTextColor(R.id.widget_refresh, accent)
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)

            val total = primary?.total ?: 0.0
            val grantedPercent = if (total > 0) {
                ((primary?.granted ?: 0.0) / total * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            // RemoteViews 不能调 setProgressTintList，所以蓝/绿各做一条，按强调色切换可见性
            views.setProgressBar(R.id.widget_bar, 100, grantedPercent, false)
            views.setProgressBar(R.id.widget_bar_alt, 100, grantedPercent, false)
            val blue = blueAccent
            views.setViewVisibility(R.id.widget_bar, if (blue) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_bar_alt, if (blue) View.GONE else View.VISIBLE)
        }

        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        return views
    }

    /** 1×1 / 2×1 空间有限：过万用「万」，其余只留整数，避免省略号。 */
    private fun compactMoney(value: Double, currency: String): String {
        val symbol = Fmt.symbol(currency)
        return if (value >= 10_000) {
            symbol + DecimalFormat("0.#").format(value / 10_000) + "万"
        } else {
            symbol + DecimalFormat("#,##0").format(value)
        }
    }
}
