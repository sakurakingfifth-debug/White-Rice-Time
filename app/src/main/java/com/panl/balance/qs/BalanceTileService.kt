package com.panl.balance.qs

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.panl.balance.MainActivity
import com.panl.balance.R
import com.panl.balance.data.Prefs
import com.panl.balance.ui.Fmt

/**
 * 控制中心磁贴：显示当前余额（精确到分），点一下打开应用。
 *
 * 三件事决定了这里的写法：
 *
 * 1. **余额放在 `subtitle` 里**（Android 10 / API 29 起支持）。磁贴的 `label` 是常驻标题
 *    （「余额」），`subtitle` 才是下面那行小字，用它放 `¥128.50` 才能在展开的控制中心里看清。
 *    API 29 以下没有 subtitle，只能把金额塞进 label。
 * 2. **磁贴不会自己知道数据变了**：应用里刷新完要调 [refresh] 让系统重新回调
 *    `onStartListening`，否则磁贴会一直显示上一次的金额。
 * 3. **Android 14 起 `startActivityAndCollapse(Intent)` 被废弃**，要改用 `PendingIntent` 版本，
 *    否则会抛 `UnsupportedOperationException`。
 */
class BalanceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // 锁屏下拉控制中心时不能直接起 Activity，等解锁后再开
        if (isLocked) unlockAndRun { openApp(intent) } else openApp(intent)
    }

    private fun openApp(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** 按缓存重绘磁贴：金额、状态、无障碍描述。 */
    private fun render() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        val snapshot = prefs.lastSnapshot
        val primary = snapshot?.primary
        val hasKey = prefs.apiKey.isNotBlank()

        val amount = primary?.let { Fmt.money(it.total, it.currency) }

        // 磁贴图标下面那行字是 label。绝大多数 ROM（ColorOS 实测、MIUI、One UI 都是）
        // **只渲染 label，不渲染 subtitle**，所以金额必须放 label 里 ——
        // 放 subtitle 的话界面上就只剩「余额」两个字（2.6 的 bug）。
        val title = when {
            !hasKey -> getString(R.string.tile_no_key)
            amount != null -> amount
            else -> getString(R.string.tile_no_data)
        }

        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_bowl)
        tile.label = title
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 原生 Android / Pixel 会在这行下面再显示 subtitle，用来说明这个数字是什么
            tile.subtitle = getString(R.string.tile_label)
        }
        // 有余额时点亮（ACTIVE），没数据 / 没密钥时置灰
        tile.state = if (amount != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = buildString {
            append(getString(R.string.tile_label))
            append(' ')
            append(title)
            if (snapshot != null) {
                append("，")
                append(Fmt.updated(snapshot.fetchedAt))
            }
        }
        tile.updateTile()
    }

    companion object {
        private const val REQUEST_OPEN = 40

        /**
         * 让系统重新回调 [onStartListening]，从而刷新磁贴内容。
         *
         * 磁贴没被加到控制中心时是空操作，所以可以放心在每次数据变化后调用。
         */
        fun refresh(context: Context) {
            runCatching {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, BalanceTileService::class.java)
                )
            }
        }

        /**
         * 让系统弹「把磁贴加到控制中心」的确认框（Android 13+）。
         *
         * 低版本没有这个 API，只能让用户自己去控制中心的「编辑」里拖。
         * 回调里给的是 `StatusBarManager.TILE_ADD_REQUEST_RESULT_*`。
         */
        fun requestAdd(context: Context, onResult: (Int) -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            runCatching {
                context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                    ComponentName(context, BalanceTileService::class.java),
                    context.getString(R.string.tile_label),
                    Icon.createWithResource(context, R.drawable.ic_tile_bowl),
                    context.mainExecutor
                ) { result -> onResult(result) }
            }
        }
    }
}
