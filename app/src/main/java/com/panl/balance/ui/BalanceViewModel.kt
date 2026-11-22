package com.panl.balance.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panl.balance.data.ApiError
import com.panl.balance.data.ApiFailure
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.data.DeepSeekClient
import com.panl.balance.data.HistoryEntry
import com.panl.balance.data.Prefs
import com.panl.balance.data.Trash
import com.panl.balance.notify.BalanceNotifications
import com.panl.balance.notify.BalanceWatchService
import com.panl.balance.perf.MemoryGuard
import com.panl.balance.widget.BalanceWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 「保存并验证」的结果，只在设置页展示。 */
sealed interface VerifyState {
    data object Idle : VerifyState
    data object Running : VerifyState
    data class Ok(val text: String) : VerifyState
    data class Err(val text: String) : VerifyState
}

/**
 * 单一状态源：密钥、余额快照、错误、历史、偏好全在这里。
 *
 * 状态用 Compose 的 `mutableStateOf` 而不是 Flow —— 界面只有一个，且全是同步读取，
 * 省掉 collectAsState 的一层包装，读起来更直接。
 */
class BalanceViewModel(app: Application) : AndroidViewModel(app), MemoryGuard.SceneSource {

    private val prefs = Prefs(app)
    private val client = DeepSeekClient()
    private var job: Job? = null

    /** 上次停留的页面，冷启动时 Pager 直接落在这里。 */
    val startPage: Int = prefs.uiPage
    private var page: Int = startPage

    var apiKey by mutableStateOf(prefs.apiKey)
        private set

    /** 上次成功结果（含磁盘缓存），用于冷启动秒开。 */
    var snapshot by mutableStateOf(prefs.lastSnapshot)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<ApiError?>(null)
        private set

    var history by mutableStateOf(prefs.history)
        private set

    /** 回收站内容（单一数据源在 [Trash]，设置页和回收站页共用）。 */
    val trash: List<HistoryEntry> get() = Trash.entries

    var verify by mutableStateOf<VerifyState>(VerifyState.Idle)
        private set

    var keyDraft by mutableStateOf(prefs.keyDraft)
        private set

    var showKey by mutableStateOf(false)
        private set

    /** 从余额页点「填写密钥」时置位，设置页消费一次后复位。 */
    var focusKeyField by mutableStateOf(false)
        private set

    var autoRefresh by mutableStateOf(prefs.autoRefresh)
        private set

    var intervalSec by mutableStateOf(prefs.intervalSec)
        private set

    var lowAlert by mutableStateOf(prefs.lowAlert)
        private set

    var threshold by mutableStateOf(prefs.threshold)
        private set

    var blueAccent by mutableStateOf(prefs.blueAccent)
        private set

    var wallpaperIcons by mutableStateOf(prefs.wallpaperIcons)
        private set

    var liveNotification by mutableStateOf(prefs.liveNotification)
        private set

    var normalNotification by mutableStateOf(prefs.normalNotification)
        private set

    /** 后台常驻（前台服务）开关。 */
    var backgroundRun by mutableStateOf(prefs.backgroundRun)
        private set

    /** 后台隐藏：切到后台后从「最近任务」里摘掉本应用。 */
    var hideFromRecents by mutableStateOf(prefs.hideFromRecents)
        private set

    val hasKey: Boolean get() = apiKey.isNotBlank()

    init {
        // 查杀广播只给 3 秒，现场直接从 VM 取，不经过 Compose 状态树
        MemoryGuard.sceneSource = this
    }

    // ---------------- 现场（公平运行内存 / 内存限制器） ----------------

    fun onPageShown(index: Int) {
        if (page == index) return
        page = index
        prefs.uiPage = index
    }

    /** 退到后台时收掉未提交的密钥草稿，进程被回收后重新打开还能接着粘贴。 */
    fun persistDraft() {
        if (prefs.keyDraft == keyDraft) return
        prefs.keyDraft = keyDraft
    }

    override fun scenePage(): Int = page

    override fun sceneDraft(): String = keyDraft

    override fun onCleared() {
        if (MemoryGuard.sceneSource === this) MemoryGuard.sceneSource = null
    }

    // ---------------- 网络 ----------------

    fun refresh(verifying: Boolean = false) {
        val key = apiKey
        if (key.isBlank()) return
        if (verifying && loading) {
            job?.cancel()
            loading = false
        }
        if (loading) return

        loading = true
        error = null
        if (verifying) verify = VerifyState.Running

        job = viewModelScope.launch {
            val result = client.fetch(key)
            result.onSuccess { snap ->
                snapshot = snap
                prefs.lastSnapshot = snap
                recordHistory(snap)
                syncWidgets()
                syncNotifications()
                if (verifying) verify = VerifyState.Ok(verifiedText(snap))
            }.onFailure { e ->
                val err = (e as? ApiFailure)?.error ?: ApiError("请求失败", e.message)
                error = err
                if (verifying) verify = VerifyState.Err(err.message)
            }
            loading = false
        }
    }

    /** 回到前台时调用：缓存超过 30 秒才真正发请求，避免来回切页狂刷。 */
    fun onForeground() {
        // 回收站里恢复过记录的话，磁盘上的 history 比内存新，回前台先对齐
        prefs.history.takeIf { it != history }?.let { history = it }
        // 后台常驻可能被通知上的「停止」或系统回收改掉，回到前台以磁盘为准重新对齐
        backgroundRun = prefs.backgroundRun
        if (backgroundRun) BalanceWatchService.ensureRunning(getApplication())
        if (!hasKey) return
        // 进程被系统回收 / 重装后通知会消失，回到前台先用缓存补回来
        syncNotifications()
        val last = snapshot?.fetchedAt ?: 0L
        if (System.currentTimeMillis() - last > 30_000) refresh()
    }

    // ---------------- 密钥 ----------------

    fun updateKeyDraft(value: String) {
        keyDraft = value
        if (verify is VerifyState.Err) verify = VerifyState.Idle
    }

    fun toggleShowKey() {
        showKey = !showKey
    }

    fun saveKey() {
        val key = keyDraft.trim()
        if (key.isBlank()) {
            verify = VerifyState.Err("请先粘贴 API Key")
            return
        }
        if (!KEY_PATTERN.matches(key)) {
            verify = VerifyState.Err("格式不对：应以 sk- 开头，后接至少 16 位字母、数字或 - _")
            return
        }
        apiKey = key
        prefs.apiKey = key
        keyDraft = ""
        prefs.keyDraft = ""
        showKey = false
        syncWidgets()
        syncNotifications()
        refresh(verifying = true)
    }

    fun clearKey() {
        job?.cancel()
        loading = false
        apiKey = ""
        prefs.apiKey = ""
        snapshot = null
        prefs.lastSnapshot = null
        history = emptyList()
        prefs.history = emptyList()
        error = null
        verify = VerifyState.Idle
        keyDraft = ""
        prefs.keyDraft = ""
        showKey = false
        // 没有密钥就没有可同步的东西，后台服务一并收摊
        if (backgroundRun) updateBackgroundRun(false)
        syncWidgets()
        BalanceNotifications.cancelAll(getApplication())
    }

    // ---------------- 偏好 ----------------

    fun updateAutoRefresh(value: Boolean) {
        autoRefresh = value
        prefs.autoRefresh = value
    }

    fun updateIntervalSec(value: Int) {
        intervalSec = value.coerceIn(Prefs.MIN_INTERVAL, Prefs.MAX_INTERVAL)
        prefs.intervalSec = intervalSec
    }

    fun updateLowAlert(value: Boolean) {
        lowAlert = value
        prefs.lowAlert = value
    }

    fun updateThreshold(value: Int) {
        threshold = value.coerceIn(1, 500)
        prefs.threshold = threshold
    }

    fun updateBlueAccent(value: Boolean) {
        blueAccent = value
        prefs.blueAccent = value
        syncWidgets()
        syncNotifications()
    }

    fun updateWallpaperIcons(value: Boolean) {
        wallpaperIcons = value
        prefs.wallpaperIcons = value
    }

    fun updateLiveNotification(value: Boolean) {
        liveNotification = value
        prefs.liveNotification = value
        syncNotifications()
    }

    fun updateNormalNotification(value: Boolean) {
        normalNotification = value
        prefs.normalNotification = value
        syncNotifications()
    }

    /**
     * 后台常驻开关：开 → 拉起前台服务（ColorOS 上系统会弹「要允许应用一直在后台运行吗」），
     * 关 → 停掉服务并撤掉它那条常驻通知。
     */
    fun updateBackgroundRun(value: Boolean) {
        backgroundRun = value
        prefs.backgroundRun = value
        val app = getApplication<Application>()
        if (value) BalanceWatchService.start(app) else BalanceWatchService.stop(app)
    }

    /**
     * 后台隐藏开关。这里只改状态，真正把任务从「最近任务」摘掉的动作在 App 根节点里统一做 ——
     * 那里能拿到当前 Activity 的任务记录，且冷启动时也会按持久化的值再刷一遍。
     */
    fun updateHideFromRecents(value: Boolean) {
        hideFromRecents = value
        prefs.hideFromRecents = value
    }

    /**
     * 清空余额记录 —— 注意**不是真删**：整批挪进回收站，用户还能在回收站里恢复。
     *
     * 真删只发生在回收站里（[purgeTrash] / [purgeTrashEntry]）。
     */
    fun clearHistory() {
        if (history.isEmpty()) return
        val moved = history
        history = emptyList()
        prefs.history = emptyList()
        Trash.addAll(moved)
    }

    /** 回收站里恢复一条：搬回记录并重新按时间排序。 */
    fun restoreTrashEntry(entry: HistoryEntry) {
        Trash.remove(entry)
        history = (history + entry).sortedByDescending { it.at }
        prefs.history = history
    }

    // ---------------- 跳转辅助 ----------------

    fun requestKeyFocus() {
        focusKeyField = true
    }

    fun consumeKeyFocus() {
        focusKeyField = false
    }

    // ---------------- 内部 ----------------

    /** 应用内数据变化后刷新桌面插件（没放置插件时是一次空操作）。 */
    private fun syncWidgets() {
        runCatching { BalanceWidgets.updateAll(getApplication()) }
    }

    /** 数据、密钥、强调色变化后重绘通知（未开启通知时是空操作）。 */
    private fun syncNotifications() {
        runCatching { BalanceNotifications.sync(getApplication()) }
    }

    private fun recordHistory(snap: BalanceSnapshot) {
        val primary = snap.primary ?: return
        val last = history.lastOrNull()
        // 余额没变就不记 —— 定时刷新会把列表刷成一堆「无变化」，没有信息量
        val unchanged = last != null &&
            last.currency.equals(primary.currency, ignoreCase = true) &&
            abs(last.total - primary.total) < 0.005
        if (unchanged) return

        val next = (history + HistoryEntry(snap.fetchedAt, primary.currency, primary.total))
            .takeLast(MAX_HISTORY)
        history = next
        prefs.history = next
    }

    private fun verifiedText(snap: BalanceSnapshot): String {
        val p = snap.primary
        return when {
            p == null -> "连接成功，但账户没有返回余额信息"
            !snap.available -> "连接成功 · 账户当前不可用"
            else -> "已验证 · 剩余 " + Fmt.money(p.total, p.currency)
        }
    }

    companion object {
        private const val MAX_HISTORY = 30
        private val KEY_PATTERN = Regex("^sk-[A-Za-z0-9_-]{16,}$")
    }
}
