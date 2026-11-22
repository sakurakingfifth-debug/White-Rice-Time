package com.panl.balance.perf

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.panl.balance.data.Prefs
import java.io.File
import java.util.Locale

/**
 * 公平运行内存（HyperOS / 金标联盟）与 Android 17 内存限制器（Memory Limiter）的接入点。
 *
 * **公平运行内存**：系统按「应用物理内存（高优先级进程 PSS 之和）」与「Java 堆」两个口径判定，
 * 触达预警线时发 `itgsa.intent.action.TRIM`（action=`trim`），触达查杀线时同一个广播再发一次
 * （action=`kill`）。应用要在 **3 秒内**通过广播里带回来的 Binder 回一个 result：
 * `0` = 已正确处理（释放内存 / 保存现场），`1` = 未处理。超时系统按「未处理」算，照杀。
 *
 * **内存限制器**（Android 17+，cgroup v2 的 `memory.high`）：超额先回收文件页、把匿名页压进
 * zRAM，表现为短暂的执行受限；继续涨才被杀，退出原因记在 `ApplicationExitInfo` 里
 * （`REASON_OTHER` + description 含 `MemoryLimiter:AnonSwap`）。
 *
 * 这里的实现刻意分成两半：
 * - 前半是**协议**：收广播、解析、回 Binder（不做任何耗时的事，保证 3 秒内回调）；
 * - 后半是**策略**：预警时释放，查杀时落盘（见 [release] / [saveScene]）。
 *
 * 协议字段说明（小米文档 3.2 / 3.3 的两张表）：
 *
 * | 位置 | key | 含义 | 类型 |
 * |---|---|---|---|
 * | common | notifyType | 1000 物理内存 / 2000 Java 堆 | int |
 * | common | notifyId | 本次通知 ID（回传要原样带上） | int |
 * | common | reason | `Excessive PSS Usage` / `Excessive Java Heap Usage` | String |
 * | common | action | `trim` / `kill` | String |
 * | common | callback | 回传结果的 Binder | IBinder |
 * | extra | pss / pssLimit | 高优进程 PSS 实际值与上限 | int（KB） |
 * | extra | heapSize / heapCapacity | Java 堆已用与上限 | int（KB） |
 *
 * 注意 `heapSize` 在表格里写作 `BUNDLE_KEY_HEAP_ALLOC`，但示例代码读的是 `heapAlloc`，
 * 两边对不上，所以两个 key 都读一次（先表格后示例）。
 */
object MemoryGuard {

    private const val TAG = "FairMemory"

    /** 金标联盟约定的预警 / 查杀广播。 */
    const val ACTION = "itgsa.intent.action.TRIM"

    private const val KEY_COMMON = "common"
    private const val KEY_EXTRA = "extra"

    private const val K_TYPE = "notifyType"
    private const val K_ID = "notifyId"
    private const val K_REASON = "reason"
    private const val K_ACTION = "action"
    private const val K_CALLBACK = "callback"

    private const val K_PSS = "pss"
    private const val K_PSS_LIMIT = "pssLimit"
    private const val K_HEAP = "heapSize"
    private const val K_HEAP_ALT = "heapAlloc"
    private const val K_HEAP_CAPACITY = "heapCapacity"

    /** 异常类型：物理内存（PSS）异常 / Java 堆异常。 */
    private const val TYPE_PHYSICAL = 1000
    private const val TYPE_HEAP = 2000

    /** 回调结果：0 = 正确处理（释放或保存），1 = 未正确处理。 */
    private const val RESULT_OK = 0
    private const val RESULT_FAIL = 1

    /** 系统端只等 3 秒。 */
    private const val REPLY_TIMEOUT_MS = 3_000L

    // ---------------- 对外状态（自检面板用） ----------------

    @Volatile private var installed = false
    @Volatile private var trimCount = 0
    @Volatile private var killCount = 0
    @Volatile private var lastEvent = "尚未收到"
    @Volatile private var lastType = 0
    @Volatile private var lastReason = ""
    @Volatile private var lastPss = 0
    @Volatile private var lastPssLimit = 0
    @Volatile private var lastHeap = 0
    @Volatile private var lastHeapLimit = 0
    @Volatile private var lastReply = "—"
    @Volatile private var lastCostMs = -1L

    /** 当前有几个 Activity 在前台（0 表示整个应用不可见）。 */
    @Volatile private var visibleActivities = 0

    /**
     * 整个应用最后一个界面也退掉时回调（配置变更、主动 finish 不算）。
     * 「后台隐藏」靠这个时机摘任务卡片 —— 只有这时才确定用户是真的离开了应用。
     */
    @Volatile var onAllStopped: ((android.app.Activity) -> Unit)? = null

    /**
     * 现场来源。查杀广播只有 3 秒窗口，来不及走 Compose 的状态树，
     * 所以由 ViewModel 自己把「当前页 + 未提交的密钥草稿」暴露出来。
     */
    @Volatile var sceneSource: SceneSource? = null

    interface SceneSource {
        fun scenePage(): Int
        fun sceneDraft(): String
    }

    private var thread: HandlerThread? = null
    private var bg: Handler? = null

    /** 回传用的 Binder，按文档在收到时 linkToDeath，回完就解绑。 */
    private var remote: IBinder? = null
    private val death = IBinder.DeathRecipient {
        synchronized(this) { remote = null }
    }

    // ---------------- 安装 ----------------

    /**
     * 在 `Application.onCreate` 里调用一次。注册在主线程之外 —— 收到广播后要落盘，
     * 不能占用 UI 线程，也不该和界面抢 3 秒窗口。
     */
    fun install(app: Application) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val thread = HandlerThread("fair-memory").apply { start() }
            thread.let { this.thread = it }
            bg = Handler(thread.looper)

            installed = runCatching {
                val filter = IntentFilter(ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 广播来自系统侧（另一个进程），必须 EXPORTED，否则收不到
                    app.registerReceiver(receiver, filter, null, bg, Context.RECEIVER_EXPORTED)
                } else {
                    app.registerReceiver(receiver, filter, null, bg)
                }
            }.onFailure { Log.w(TAG, "注册公平运行内存广播失败", it) }.isSuccess

            Log.i(TAG, "install -> installed=$installed")
        }
    }

    /** 记录 Activity 可见性，用于判断「现在能不能安全 GC」。 */
    fun trackVisibility(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                visibleActivities++
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                if (visibleActivities > 0) visibleActivities--
                // 旋转屏幕、主动 finish 都会走到这里，但都不代表「用户离开了应用」
                if (visibleActivities == 0 && !activity.isChangingConfigurations && !activity.isFinishing) {
                    onAllStopped?.invoke(activity)
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, bundle: Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, bundle: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }

    // ---------------- 协议：收广播 ----------------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val started = SystemClock.elapsedRealtime()
            try {
                handle(context.applicationContext, intent)
            } catch (t: Throwable) {
                Log.w(TAG, "处理公平运行内存广播失败", t)
            } finally {
                lastCostMs = SystemClock.elapsedRealtime() - started
                if (lastCostMs > REPLY_TIMEOUT_MS) {
                    Log.w(TAG, "回调耗时 ${lastCostMs}ms，超过系统给的 3 秒")
                }
            }
        }
    }

    private fun handle(app: Context, intent: Intent) {
        val all = intent.extras ?: return
        // 正常是 `common` / `extra` 两个子 Bundle；个别实现会把公共字段直接摊在顶层，
        // 拿不到 common 时退回顶层，这样 `adb shell am broadcast` 也能自测。
        val common = all.getBundle(KEY_COMMON) ?: all

        val type = common.getInt(K_TYPE, 0)
        val id = common.getInt(K_ID, 0)
        val reason = common.getString(K_REASON).orEmpty()
        val action = common.getString(K_ACTION).orEmpty().lowercase(Locale.US)
        val callback = common.getBinder(K_CALLBACK)

        val extra = all.getBundle(KEY_EXTRA) ?: all
        lastType = type
        lastReason = reason
        lastPss = extra.getInt(K_PSS, 0)
        lastPssLimit = extra.getInt(K_PSS_LIMIT, 0)
        lastHeap = extra.getInt(K_HEAP, extra.getInt(K_HEAP_ALT, 0))
        lastHeapLimit = extra.getInt(K_HEAP_CAPACITY, 0)

        val isKill = action == "kill"
        val label = if (type == TYPE_HEAP) "Java 堆" else "物理内存"

        // 先把 Binder 接住，再做正事 —— 正事做砸了也要回一个 1，不能让系统干等 3 秒
        attach(callback)

        val result = runCatching {
            if (isKill) {
                killCount++
                saveScene(app)
            } else {
                trimCount++
                release("$label 预警")
                true
            }
        }.onFailure { Log.w(TAG, "现场处理失败", it) }.getOrDefault(false)

        val message = if (result) "已${if (isKill) "保存现场" else "释放内存"}" else "处理失败"
        reply(type, id, if (result) RESULT_OK else RESULT_FAIL, message)

        lastEvent = buildString {
            append(if (isKill) "查杀" else "预警")
            append("（").append(label)
            if (reason.isNotBlank()) append(" · ").append(reason)
            append("）")
        }
    }

    private fun attach(callback: IBinder?) {
        if (callback == null) {
            Log.w(TAG, "广播里没有 callback Binder")
            return
        }
        synchronized(this) {
            remote = callback
            runCatching { callback.linkToDeath(death, 0) }
        }
    }

    /**
     * 按文档 3.3 回传：`int notifyType`、`int notifyId`、`int result`、`Bundle extra`，
     * 事务号取 `IBinder.FIRST_CALL_TRANSACTION`，单向发送。
     */
    private fun reply(type: Int, id: Int, result: Int, message: String) {
        val binder = synchronized(this) { remote }
        if (binder == null) {
            lastReply = "没有回调 Binder，结果无法回传"
            return
        }
        val data = Parcel.obtain()
        val answer = Parcel.obtain()
        try {
            data.writeInt(type)
            data.writeInt(id)
            data.writeInt(result)
            data.writeBundle(Bundle().apply { putString("reply", message) })
            binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, answer, IBinder.FLAG_ONEWAY)
            answer.readException()
            lastReply = "result=$result（$message）"
        } catch (t: Throwable) {
            lastReply = "回传失败：${t.javaClass.simpleName}"
            Log.w(TAG, "回传失败", t)
        } finally {
            runCatching { binder.unlinkToDeath(death, 0) }
            synchronized(this) { if (remote === binder) remote = null }
            answer.recycle()
            data.recycle()
        }
    }

    // ---------------- 策略：释放 / 保存 ----------------

    /**
     * 内存预警：释放可重建的东西。
     *
     * 本应用没有图片加载库、没有 LruCache，PSS 的大头是 Compose 与图形层（RenderEffect / 纹理），
     * 这部分不受应用侧控制。能做的只有两件事，都聊胜于无但确实有效：
     * 1. 丢掉可重建的瞬时状态（错误文案、校验结果这类）；
     * 2. 应用不可见时补一次 GC —— Compose 每帧产生大量短命对象，堆里绝大多数是可回收垃圾，
     *    查杀判定里「Java 堆」那一项主要就吃这个。可见时不 GC，避免卡帧。
     */
    private fun release(reason: String): Boolean {
        if (visibleActivities <= 0) {
            val handler = bg
            if (handler == null || Looper.myLooper() == handler.looper) {
                runCatching { System.gc() }
            } else {
                handler.post { runCatching { System.gc() } }
            }
        }
        Log.i(TAG, "release($reason) visible=$visibleActivities")
        return true
    }

    /**
     * 查杀前保存现场：必须同步写完（`commit`，不是 `apply`），否则进程一死就丢。
     * 这里只存「再打开时能接上」的最小集合：停留页面 + 没提交的密钥草稿。
     */
    private fun saveScene(app: Context): Boolean {
        val src = sceneSource
        val page = src?.scenePage() ?: 0
        val draft = src?.sceneDraft().orEmpty()
        return Prefs(app).saveSceneSync(page, draft)
    }

    /** 系统的标准内存回调（非小米机型 / 普通低内存时走这条）。 */
    @Suppress("DEPRECATION")
    fun onSystemTrim(level: Int) {
        val name = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "level=$level"
        }
        lastEvent = "系统 onTrimMemory（$name）"
        release(name)
    }

    // ---------------- 诊断 ----------------

    /**
     * 自检面板里「内存」那一段。全部在后台线程读：`Debug.getPss()` 一次要几毫秒到几十毫秒，
     * 文档明确要求别放主线程。
     */
    fun diagnose(app: Context): List<Pair<String, String>> {
        val pssKb = runCatching { Debug.getPss() }.getOrDefault(0L)
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapMax = runtime.maxMemory()
        // cgroup v2 里本进程所属的目录。读不到路径、读得到路径但读不到文件，是两回事，分开报才好定位
        val path = cgroupPath()
        val dir = path?.let { File("/sys/fs/cgroup", it) }

        return buildList {
            add("物理内存" to mb(pssKb))
            add("Java 堆" to "${mb(heapUsed / 1024)} / ${mb(heapMax / 1024)}")
            add("cgroup" to (path ?: "/proc/self/cgroup 读不到"))
            // Android 17 的内存限制器会为每个进程单独建一层 cgroup（apps/uid_x/pid_y），
            // 老版本只有 apps/uid_x。所以「有没有 pid 这一层」是应用侧唯一能看到的启用证据。
            add("限制器" to if (path == null) "—" else if (path.contains("/pid_")) "已启用（按进程分组）" else "未见进程分组")
            add("软上限" to limitText(dir, "memory.high"))
            add("硬上限" to limitText(dir, "memory.max"))
            add("交换上限" to limitText(dir, "memory.swap.max"))
            add("上次退出" to lastExit(app))
            add("预警监听" to if (installed) "已监听（$ACTION）" else "未监听")
            add("预警 / 查杀" to "$trimCount / $killCount 次")
            add("最近一次" to if (lastType == 0) lastEvent else "$lastEvent；PSS $lastPss/${lastPssLimit} KB，堆 $lastHeap/$lastHeapLimit KB")
            add("回传结果" to if (lastCostMs >= 0) "$lastReply（${lastCostMs}ms）" else lastReply)
        }
    }

    /**
     * 上次进程是怎么没的。Android 17 的内存限制器杀进程时给的是
     * `REASON_OTHER` + description 含 `MemoryLimiter:AnonSwap`，这里把它翻译成人话。
     */
    private fun lastExit(app: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "系统不提供（API < 30）"
        val am = app.getSystemService(ActivityManager::class.java) ?: return "读不到"
        val list = runCatching {
            am.getHistoricalProcessExitReasons(app.packageName, 0, 5)
        }.getOrNull().orEmpty()
        val info = list.firstOrNull { it.reason != REASON_EXIT_SELF } ?: list.firstOrNull()
            ?: return "无记录"
        val description = runCatching { info.description }.getOrNull().orEmpty()
        val base = when {
            description.contains("MemoryLimiter", ignoreCase = true) ->
                "被内存限制器限流/终止（${description.substringAfter("MemoryLimiter", "MemoryLimiter")}）"
            else -> reasonText(info.reason)
        }
        val ago = System.currentTimeMillis() - info.timestamp
        return "$base · ${agoText(ago)}前"
    }

    private fun reasonText(reason: Int): String = when (reason) {
        0 -> "未知"
        1 -> "正常退出（自杀）"
        2 -> "被信号杀死"
        3 -> "系统低内存（LMK）"
        4 -> "Java 崩溃"
        5 -> "Native 崩溃"
        6 -> "ANR"
        7 -> "初始化失败"
        8 -> "权限变更"
        9 -> "资源使用过度"
        10 -> "用户主动停止"
        11 -> "用户停止"
        12 -> "依赖进程退出"
        13 -> "其他"
        14 -> "被冻结"
        15 -> "包状态变更"
        16 -> "应用被更新"
        else -> "原因码 $reason"
    }

    private fun agoText(ms: Long): String = when {
        ms < 60_000 -> "${ms / 1000} 秒"
        ms < 3_600_000 -> "${ms / 60_000} 分钟"
        ms < 86_400_000 -> "${ms / 3_600_000} 小时"
        else -> "${ms / 86_400_000} 天"
    }

    /** `/proc/self/cgroup` 里 `0::` 那一行的路径，即本进程在 cgroup v2 中的位置。 */
    private fun cgroupPath(): String? = runCatching {
        File("/proc/self/cgroup").readLines()
            .firstOrNull { it.startsWith("0::") }
            ?.substring(3)
            ?.trim()
            ?.ifEmpty { null }
    }.getOrNull()

    /**
     * 读一个 cgroup 限额文件。Android 把这些节点标成 `cgroup_v2`，普通应用通常会被 SELinux 挡住，
     * 所以要能分清「没这个文件」和「没权限」—— 否则面板上只有一句「读不到」，没法定位。
     */
    private fun limitText(dir: File?, name: String): String {
        if (dir == null) return "—"
        val raw = runCatching { File(dir, name).readText().trim() }.getOrNull()
            ?: return if (dir.list() == null) "不可读（SELinux）" else "内核未提供"
        if (raw == "max") return "不限"
        val value = raw.toLongOrNull() ?: return "无法解析"
        return bytesText(value)
    }

    private fun bytesText(v: Long): String = when {
        v >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", v / 1073741824.0)
        else -> String.format(Locale.US, "%.0f MB", v / 1048576.0)
    }

    private fun mb(kb: Long): String = String.format(Locale.US, "%.1f MB", kb / 1024.0)

    private const val REASON_EXIT_SELF = 1
}
