package com.panl.balance.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.panl.balance.MainActivity
import com.panl.balance.R
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.data.CurrencyBalance
import com.panl.balance.data.Prefs
import com.panl.balance.qs.BalanceTileService
import com.panl.balance.ui.Fmt

/**
 * 三种余额通知：
 *
 * 1. **实时更新**（[postLive]）—— 常驻通知。Android 16（API 36）起按官方 Live Updates 规范构建：
 *    `ProgressStyle` + `EXTRA_REQUEST_PROMOTED_ONGOING` + `setShortCriticalText`，
 *    由系统提升为状态栏胶囊 / 锁屏置顶卡片；低版本退化为带进度条的常驻通知。
 *    官方要求：必须是 ongoing、必须有 contentTitle、不得使用自定义 RemoteViews、
 *    不得是群组摘要、不得 colorized、渠道重要性不得为 IMPORTANCE_MIN，
 *    并在清单里声明 `POST_PROMOTED_NOTIFICATIONS`。
 *
 *    在 ColorOS 16 上，这条通知会被渲染成**流体云**胶囊 / 面板，所以文案与结构按流体云
 *    模板的 level 组织（见 [FluidCloud]）：
 *
 *    | 流体云位置 | level | 本应用填的内容 |
 *    |---|---|---|
 *    | 胶囊左侧 | `A*` + `B*` | 服务图标 + 余额（≤5 字，`FluidCloud.capsuleText`） |
 *    | 面板顶部 | `D8` + `C10` | 同步图标 + 「更新于 HH:mm」 |
 *    | 面板核心 | `D1` + `B1*` + `C1` | 图标 + 总余额 + 赠送 / 充值构成 |
 *    | 面板底部 | `D13*` | 构成条（赠送 / 充值两段配色） |
 *    | 面板按钮 | `E9*` / `E10*` | 刷新 / 打开 |
 *
 * 2. **普通通知**（[postNormal]）—— **常驻**通知（`setOngoing(true)`，滑不走），展开显示赠送 / 充值构成与更新时间。
 *
 * 3. **后台常驻通知**（[serviceNotification]）—— 只在前台服务 [BalanceWatchService] 运行时存在，
 *    是 Android 强制要求的前台服务通知（渠道 `balance_service`，静默、常驻），
 *    顺带显示余额与刷新间隔，并带「刷新 / 停止」两个动作。
 *
 * 三者都带「刷新」动作，走 [NotificationActionReceiver] 在后台拉一次数据。
 */
object BalanceNotifications {

    private const val CHANNEL_LIVE = "balance_live"
    private const val CHANNEL_NORMAL = "balance_normal"
    private const val CHANNEL_SERVICE = "balance_service"
    private const val TAG = "BalanceNotifications"
    private const val ID_LIVE = 1001
    private const val ID_NORMAL = 1002

    /** 前台服务通知的 id，[BalanceWatchService] 的 `startForeground` 必须用同一个。 */
    const val ID_SERVICE = 1003

    private const val COLOR_BLUE = 0xFF4D6BFE.toInt()
    private const val COLOR_BLUE_ALT = 0xFF8FA2FF.toInt()
    private const val COLOR_GREEN = 0xFF00A05A.toInt()
    private const val COLOR_GREEN_ALT = 0xFF6FD3A3.toInt()

    /** 没有数据时的中性色，与品牌色无关。 */
    private const val COLOR_IDLE = 0xFFC9D0D6.toInt()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val live = NotificationChannel(
            CHANNEL_LIVE,
            context.getString(R.string.notification_channel_live),
            // 实时更新要求渠道重要性不能是 IMPORTANCE_MIN
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_live_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val normal = NotificationChannel(
            CHANNEL_NORMAL,
            context.getString(R.string.notification_channel_normal),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_normal_desc)
            setShowBadge(false)
        }
        // 前台服务通知：静默、不打扰，仅作为「应用在后台运行」的可见凭据
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_service_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(listOf(live, normal, service))
    }

    /**
     * 按当前开关与缓存重绘两条可选通知；未开启或没有密钥时直接撤销。
     * 后台常驻通知（[ID_SERVICE]）不在这里管，由 [BalanceWatchService] 自己维护。
     */
    fun sync(context: Context) {
        ensureChannels(context)
        val prefs = Prefs(context)
        val hasKey = prefs.apiKey.isNotBlank()
        val snapshot = prefs.lastSnapshot

        // 两类通知互不影响：一类构造失败不能把另一类也带下去
        if (prefs.liveNotification && hasKey) {
            runCatching { postLive(context, snapshot, prefs) }
                .onFailure { Log.e(TAG, "postLive failed", it) }
        } else {
            cancel(context, ID_LIVE)
        }

        if (prefs.normalNotification && hasKey) {
            runCatching { postNormal(context, snapshot) }
                .onFailure { Log.e(TAG, "postNormal failed", it) }
        } else {
            cancel(context, ID_NORMAL)
        }

        // 后台服务在跑时，它自己那条常驻通知也要立刻跟上（否则要等下一轮定时）
        if (BalanceWatchService.running) refreshService(context)

        // 控制中心磁贴读的是同一份缓存，这里统一踢一下（磁贴没放上去时是空操作）。
        // 所有「数据变了」的路径最后都会走到 sync()，所以放在这一处就够了。
        BalanceTileService.refresh(context)
    }

    fun cancelAll(context: Context) {
        cancel(context, ID_LIVE)
        cancel(context, ID_NORMAL)
        BalanceTileService.refresh(context)
    }

    // ---------------- 实时更新 ----------------

    private fun postLive(context: Context, snapshot: BalanceSnapshot?, prefs: Prefs) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val primary = snapshot?.primary
        val accent = if (prefs.blueAccent) COLOR_BLUE else COLOR_GREEN
        val accentAlt = if (prefs.blueAccent) COLOR_BLUE_ALT else COLOR_GREEN_ALT
        val amount = primary?.let { Fmt.money(it.total, it.currency) }

        // 抬头行已显示应用名，核心信息（B1*）直接放余额，辅助信息（C1）放构成
        val builder = Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (amount != null) context.getString(R.string.notification_balance_text, amount)
                else context.getString(R.string.notification_no_data)
            )
            .setContentText(
                primary?.let {
                    context.getString(
                        R.string.notification_composition,
                        Fmt.money(it.granted, it.currency),
                        Fmt.money(it.toppedUp, it.currency)
                    )
                } ?: context.getString(R.string.notification_live_title)
            )
            // 顶部信息（C10）：同步时间。流体云里这一行在面板头部
            .setSubText(snapshot?.let { Fmt.updated(it.fetchedAt) })
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setWhen(snapshot?.fetchedAt ?: System.currentTimeMillis())
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(accent)
            // Live Updates 明确禁止 colorized，显式关掉避免被 OEM 主题改坏
            .setColorized(false)
            .addAction(refreshAction(context))
            .addAction(openAction(context))

        if (Build.VERSION.SDK_INT >= 36) {
            // 胶囊文本：流体云 2.0 规定最多 5 个字，超出会被系统拉长后截断
            builder.setShortCriticalText(FluidCloud.capsuleText(primary))
            // 平台 Notification.Builder 没有 setRequestPromotedOngoing（那是 Compat 的方法），
            // 平台侧通过 EXTRA_REQUEST_PROMOTED_ONGOING 请求升级为实时动态
            builder.addExtras(
                Bundle().apply {
                    putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
                }
            )
            builder.setStyle(progressStyle(context, primary, accent, accentAlt))
        } else if (primary != null && primary.total > 0) {
            val percent = ((primary.granted / primary.total) * 100).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
        }

        manager.notify(ID_LIVE, builder.build())
    }

    /**
     * API 36 的 ProgressStyle，对应流体云面板底部的 `D13*` 进度条。
     *
     * 赠送段用品牌色、充值段用同色系浅一档，两段拼成构成条。没有数据时也给一条满格中性条：
     * 流体云规范要求同一实时活动的胶囊 / 面板样式在状态变化时保持不变，不能时而有时而没有进度条。
     *
     * **进度游标必须显式指定**：不设 `setProgressTrackerIcon` 时系统会用应用图标兜底，
     * 真机上渲染成黑底 + 一个奇怪的白色箭头（3.7 的 bug）。这里换成饭碗图标，
     * 顺带避免和面板右上角、按钮上的 ¥ 重复。
     */
    private fun progressStyle(
        context: Context,
        primary: CurrencyBalance?,
        accent: Int,
        accentAlt: Int,
    ): Notification.ProgressStyle {
        val style = Notification.ProgressStyle()
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_tile_bowl))
        if (primary == null || primary.total <= 0) {
            return style.setProgress(100).setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(COLOR_IDLE))
            )
        }

        val granted = ((primary.granted / primary.total) * 100).toInt().coerceIn(0, 100)
        val segments = ArrayList<Notification.ProgressStyle.Segment>(2)
        if (granted > 0) {
            segments.add(Notification.ProgressStyle.Segment(granted).setColor(accent))
        }
        if (granted < 100) {
            segments.add(Notification.ProgressStyle.Segment(100 - granted).setColor(accentAlt))
        }
        return style.setProgress(100).setProgressSegments(segments)
    }

    // ---------------- 常驻余额通知 ----------------

    private fun postNormal(context: Context, snapshot: BalanceSnapshot?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val primary = snapshot?.primary

        val summary = primary?.let {
            context.getString(R.string.notification_balance_text, Fmt.money(it.total, it.currency))
        } ?: context.getString(R.string.notification_no_data)

        val detail = buildString {
            if (primary != null) {
                append(context.getString(R.string.notification_balance_text, Fmt.money(primary.total, primary.currency)))
                append('\n')
                append(
                    context.getString(
                        R.string.notification_composition,
                        Fmt.money(primary.granted, primary.currency),
                        Fmt.money(primary.toppedUp, primary.currency)
                    )
                )
            } else {
                append(context.getString(R.string.notification_no_data))
            }
            snapshot?.let {
                append('\n')
                append(Fmt.updated(it.fetchedAt))
            }
        }

        val builder = Notification.Builder(context, CHANNEL_NORMAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_normal_title))
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(openApp(context))
            // 常驻：滑不走，只能从设置里关掉开关或清空密钥
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(refreshAction(context))

        manager.notify(ID_NORMAL, builder.build())
    }

    // ---------------- 后台常驻（前台服务） ----------------

    /**
     * 前台服务通知。Android 要求前台服务必须挂一条通知，这里让它顺便显示余额，
     * 避免用户为了「后台常驻」多看到一条毫无信息量的通知。
     *
     * 注意：必须在 `startForeground()` 之前调用，且内容可被 [refreshService] 反复覆盖。
     */
    fun serviceNotification(context: Context): Notification {
        ensureChannels(context)
        val prefs = Prefs(context)
        val primary = prefs.lastSnapshot?.primary
        val amount = primary?.let { Fmt.money(it.total, it.currency) }

        val builder = Notification.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                amount?.let { context.getString(R.string.notification_balance_text, it) }
                    ?: context.getString(R.string.notification_no_data)
            )
            .setContentText(context.getString(R.string.notification_service_text, prefs.intervalSec))
            .setSubText(prefs.lastSnapshot?.let { Fmt.updated(it.fetchedAt) })
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setColor(if (prefs.blueAccent) COLOR_BLUE else COLOR_GREEN)
            .addAction(refreshAction(context))
            .addAction(stopBackgroundAction(context))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 进前台时立刻显示，不要等系统那 10 秒的「延后展示」窗口
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    /** 后台服务每轮同步后刷新自己的常驻通知（余额、间隔、更新时间都会变）。 */
    fun refreshService(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(ID_SERVICE, serviceNotification(context))
        }.onFailure { Log.e(TAG, "refreshService failed", it) }
    }

    // ---------------- 公共部分 ----------------

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        10,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun refreshAction(context: Context): Notification.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_REFRESH)
        val pending = PendingIntent.getBroadcast(
            context,
            20,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_refresh),
            context.getString(R.string.notification_action_refresh),
            pending
        ).build()
    }

    /** 流体云面板上的第二个按钮（E10*）：直接进应用看明细。 */
    private fun openAction(context: Context): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.getString(R.string.notification_action_open),
            openApp(context)
        ).build()

    /** 后台常驻通知上的「停止」：关掉开关并停掉前台服务。 */
    private fun stopBackgroundAction(context: Context): Notification.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_STOP_BACKGROUND)
        val pending = PendingIntent.getBroadcast(
            context,
            30,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_stop),
            context.getString(R.string.notification_action_stop),
            pending
        ).build()
    }

    private fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
