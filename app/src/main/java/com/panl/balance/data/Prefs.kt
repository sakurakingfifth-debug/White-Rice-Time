package com.panl.balance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化：API Key（加密）、刷新偏好、提醒阈值、余额缓存与变动记录。
 *
 * 只用一个 SharedPreferences 文件，不引 DataStore —— 数据量极小（< 4 KB），
 * 同步读写反而更简单，也不会在首帧多一次 IO 等待。
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("pui_balance", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString(K_API_KEY, null)?.let { SecretBox.decrypt(it) }.orEmpty()
        set(value) {
            val editor = sp.edit()
            if (value.isBlank()) editor.remove(K_API_KEY)
            else editor.putString(K_API_KEY, SecretBox.encrypt(value))
            editor.apply()
        }

    var autoRefresh: Boolean
        get() = sp.getBoolean(K_AUTO, true)
        set(value) = sp.edit().putBoolean(K_AUTO, value).apply()

    var intervalSec: Int
        get() = sp.getInt(K_INTERVAL, 60).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        set(value) = sp.edit().putInt(K_INTERVAL, value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()

    var lowAlert: Boolean
        get() = sp.getBoolean(K_LOW_ALERT, true)
        set(value) = sp.edit().putBoolean(K_LOW_ALERT, value).apply()

    var threshold: Int
        get() = sp.getInt(K_THRESHOLD, 10).coerceIn(1, 500)
        set(value) = sp.edit().putInt(K_THRESHOLD, value.coerceIn(1, 500)).apply()

    var blueAccent: Boolean
        get() = sp.getBoolean(K_BLUE, true)
        set(value) = sp.edit().putBoolean(K_BLUE, value).apply()

    var wallpaperIcons: Boolean
        get() = sp.getBoolean(K_WALLPAPER, false)
        set(value) = sp.edit().putBoolean(K_WALLPAPER, value).apply()

    // ---------------- 外观与导航栏 ----------------

    /** 明暗：`System` / `Light` / `Dark`。 */
    var themeMode: String
        get() = sp.getString(K_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
        set(value) = sp.edit().putString(K_THEME_MODE, value).apply()

    /**
     * 主题色来源：`Default`（DeepSeek 蓝）/ `Wallpaper`（系统壁纸取色）/ `Custom`（[accentArgb]）。
     *
     * 默认值沿用旧开关的状态：之前关掉「DeepSeek 蓝主色」的人回到了 PUI 洋红，
     * 这里没有洋红这一档，统一落到「默认」并把选择权交回给用户。
     */
    var accentMode: String
        get() = sp.getString(K_ACCENT_MODE, null) ?: if (wallpaperIcons) MODE_WALLPAPER else MODE_DEFAULT
        set(value) = sp.edit().putString(K_ACCENT_MODE, value).apply()

    /** 自定义主题色（ARGB），只在 [accentMode] = `Custom` 时生效。 */
    var accentArgb: Int
        get() = sp.getInt(K_ACCENT_ARGB, DEFAULT_ACCENT_ARGB)
        set(value) = sp.edit().putInt(K_ACCENT_ARGB, value).apply()

    /** 顶部模糊与毛玻璃总开关。 */
    var blurEnabled: Boolean
        get() = sp.getBoolean(K_BLUR_ENABLED, true)
        set(value) = sp.edit().putBoolean(K_BLUR_ENABLED, value).apply()

    /** 顶部模糊深度（px），对应 [com.panl.pui.progressiveTopBlur] 的 maxRadius。 */
    var topBlurPx: Int
        get() = sp.getInt(K_TOP_BLUR, 60).coerceIn(2, 100)
        set(value) = sp.edit().putInt(K_TOP_BLUR, value.coerceIn(2, 100)).apply()

    /** 浮窗（底部导航栏）背景模糊半径（px）。 */
    var navBlurPx: Int
        get() = sp.getInt(K_NAV_BLUR, 48).coerceIn(2, 100)
        set(value) = sp.edit().putInt(K_NAV_BLUR, value.coerceIn(2, 100)).apply()

    /** 实时更新通知（Android 16+ 会被提升为状态栏实时动态）。 */
    var liveNotification: Boolean
        get() = sp.getBoolean(K_LIVE_NOTIFY, false)
        set(value) = sp.edit().putBoolean(K_LIVE_NOTIFY, value).apply()

    /** 普通余额通知（常驻，不可滑走）。 */
    var normalNotification: Boolean
        get() = sp.getBoolean(K_NORMAL_NOTIFY, false)
        set(value) = sp.edit().putBoolean(K_NORMAL_NOTIFY, value).apply()

    /**
     * 后台常驻：用前台服务按 [intervalSec] 在后台同步余额。
     *
     * 这是「应用一直在后台运行」的实现方式 —— 只有前台服务能让系统在锁屏后仍按秒级间隔
     * 调度请求；纯后台定时（WorkManager 最短 15 分钟）撑不起 30–300 秒的刷新间隔。
     */
    var backgroundRun: Boolean
        get() = sp.getBoolean(K_BACKGROUND, false)
        set(value) = sp.edit().putBoolean(K_BACKGROUND, value).apply()

    /**
     * 后台隐藏：切到后台后把本应用从「最近任务」里摘掉（`setExcludeFromRecents`）。
     *
     * 卡片上会带着余额缩略图，隐藏既是隐私也是防误滑；代价是部分机型（含 ColorOS）
     * 隐藏后没法再给应用加锁后台，见设置页里的提示。
     */
    var hideFromRecents: Boolean
        get() = sp.getBoolean(K_HIDE_RECENTS, false)
        set(value) = sp.edit().putBoolean(K_HIDE_RECENTS, value).apply()

    /** 上次停留的页面（0 余额 / 1 设置），冷启动时直接落在同一页。 */
    var uiPage: Int
        get() = sp.getInt(K_UI_PAGE, 0).coerceIn(0, 1)
        set(value) = sp.edit().putInt(K_UI_PAGE, value.coerceIn(0, 1)).apply()

    /**
     * 未提交的密钥草稿（加密存放）。
     *
     * 只用来接续「粘贴了一半还没保存就被系统回收」的现场，保存成功后立刻清掉；
     * 和正式密钥一样过 [SecretBox]，不会在磁盘上留明文。
     */
    var keyDraft: String
        get() = sp.getString(K_KEY_DRAFT, null)?.let { SecretBox.decrypt(it) }.orEmpty()
        set(value) {
            val editor = sp.edit()
            if (value.isBlank()) editor.remove(K_KEY_DRAFT)
            else editor.putString(K_KEY_DRAFT, SecretBox.encrypt(value))
            editor.apply()
        }

    /**
     * 现场落盘的同步版本：公平运行内存的查杀广播只给 3 秒，必须 `commit()` 等写完再回调，
     * 用 `apply()` 的话进程一死就丢了。
     */
    fun saveSceneSync(page: Int, draft: String): Boolean = runCatching {
        val editor = sp.edit().putInt(K_UI_PAGE, page.coerceIn(0, 1))
        if (draft.isBlank()) editor.remove(K_KEY_DRAFT)
        else editor.putString(K_KEY_DRAFT, SecretBox.encrypt(draft))
        editor.commit()
    }.getOrDefault(false)

    var lastSnapshot: BalanceSnapshot?
        get() = sp.getString(K_SNAPSHOT, null)?.let { raw ->
            runCatching { snapshotFromJson(JSONObject(raw)) }.getOrNull()
        }
        set(value) {
            val editor = sp.edit()
            if (value == null) editor.remove(K_SNAPSHOT)
            else editor.putString(K_SNAPSHOT, snapshotToJson(value).toString())
            editor.apply()
        }

    var history: List<HistoryEntry>
        get() = sp.getString(K_HISTORY, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(
                            HistoryEntry(
                                at = o.optLong("at"),
                                currency = o.optString("currency", "CNY"),
                                total = o.optDouble("total", 0.0),
                            )
                        )
                    }
                }
            }.getOrNull()
        }.orEmpty()
        set(value) {
            val arr = JSONArray()
            value.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("at", e.at)
                        put("currency", e.currency)
                        put("total", e.total)
                    }
                )
            }
            sp.edit().putString(K_HISTORY, arr.toString()).apply()
        }

    private fun snapshotToJson(s: BalanceSnapshot): JSONObject {
        val items = JSONArray()
        s.items.forEach { i ->
            items.put(
                JSONObject().apply {
                    put("currency", i.currency)
                    put("total", i.total)
                    put("granted", i.granted)
                    put("topped_up", i.toppedUp)
                }
            )
        }
        return JSONObject().apply {
            put("available", s.available)
            put("fetched_at", s.fetchedAt)
            put("items", items)
        }
    }

    private fun snapshotFromJson(o: JSONObject): BalanceSnapshot {
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val it2 = arr.optJSONObject(i) ?: continue
                add(
                    CurrencyBalance(
                        currency = it2.optString("currency", "CNY"),
                        total = it2.optDouble("total", 0.0),
                        granted = it2.optDouble("granted", 0.0),
                        toppedUp = it2.optDouble("topped_up", 0.0),
                    )
                )
            }
        }
        return BalanceSnapshot(
            available = o.optBoolean("available", false),
            items = items,
            fetchedAt = o.optLong("fetched_at"),
        )
    }

    companion object {
        const val MIN_INTERVAL = 30
        const val MAX_INTERVAL = 300

        // 明暗与主题色（外观与导航栏）
        const val MODE_SYSTEM = "System"
        const val MODE_LIGHT = "Light"
        const val MODE_DARK = "Dark"
        const val MODE_DEFAULT = "Default"
        const val MODE_WALLPAPER = "Wallpaper"
        const val MODE_CUSTOM = "Custom"

        /** DeepSeek 蓝 #4D6BFE，和主题色板的 primary 一致。 */
        const val DEFAULT_ACCENT_ARGB = 0xFF4D6BFE.toInt()

        private const val K_THEME_MODE = "theme_mode"
        private const val K_ACCENT_MODE = "theme_accent_mode"
        private const val K_ACCENT_ARGB = "theme_accent_argb"
        private const val K_BLUR_ENABLED = "blur_enabled"
        private const val K_TOP_BLUR = "top_blur_px"
        private const val K_NAV_BLUR = "nav_blur_px"

        private const val K_API_KEY = "api_key"
        private const val K_AUTO = "auto_refresh"
        private const val K_INTERVAL = "interval_sec"
        private const val K_LOW_ALERT = "low_alert"
        private const val K_THRESHOLD = "threshold"
        private const val K_BLUE = "blue_accent"
        private const val K_WALLPAPER = "wallpaper_icons"
        private const val K_LIVE_NOTIFY = "live_notification"
        private const val K_NORMAL_NOTIFY = "normal_notification"
        private const val K_BACKGROUND = "background_run"
        private const val K_SNAPSHOT = "last_snapshot"
        private const val K_HISTORY = "history"
        private const val K_UI_PAGE = "ui_page"
        private const val K_KEY_DRAFT = "key_draft"
        private const val K_HIDE_RECENTS = "hide_from_recents"
    }
}
