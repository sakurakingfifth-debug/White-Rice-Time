package com.panl.balance.anim

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View

/**
 * ColorOS「View 无缝动画」桥接。
 *
 * 介绍：https://open.oppomobile.com/documentation/page/info?id=13771
 * 接入：https://open.oppomobile.com/documentation/page/info?id=13772
 *
 * 能力是把「卡片 View」放大成全屏 Activity，返回时再缩回原 View，用系统级 Spring 动画，
 * 比 `overridePendingTransition` 和共享元素动画更顺，且支持阻断与回弹。
 *
 * ## 官方接口一览（id=13772）
 *
 * | 接口 | 用途 |
 * |---|---|
 * | `setSeamlessView(view, activity, bundle, callback)` | 在 `startActivity` **之前**调用，返回是否支持 |
 * | `finishCurrentAnimation()` | 提前结束当前动画（如返回时立刻滑动列表） |
 * | `skipBackAnim(activity)` | 只做打开动画，返回不做任何动效 |
 * | `setSkipViewSeamless(activity)` | 返回改走默认横切（如返回时源 View 已销毁） |
 * | `setForceLeashAlphaOut(activity, force)` | 返回强制 alpha 淡出（无回弹） |
 * | `getVersion()` | 系统实现版本，`> 37000` 才支持 |
 *
 * 规格：ColorOS 16.1 及以上、动效等级 B+ 以上；浮窗 / 兼容模式 / 平行视窗 / 分屏不支持。
 * 文档要求自行 try-catch `RuntimeException` 与 `NoSuchMethodError`。
 *
 * ## 为什么用反射
 *
 * 官方接法是 `compileOnly "com.oplus.animation:viewseamless:1.0.0@aar"`。这个 AAR 在 Maven Central 上能下到，
 * 但里面的 `setSeamlessView` 是**空实现**（直接 `return false`），只用于编译期对齐签名——
 * 真正实现在 ColorOS 系统里。所以：
 *
 * 1. 不能把它打进 APK（会遮蔽系统的真实实现，永远返回 false）；
 * 2. 非 OPPO 机型、ColorOS 16.1 以下没有真实类，直接引用会 `NoClassDefFoundError`。
 *
 * 反射两头都占：类不存在就安静退化成普通 `startActivity`，存在就按真实签名调用。
 * 常量值取自 AAR 反编译结果（`view_seamless_open` 等），同时优先运行时读取，避免 SDK 改值后失效。
 */
object SeamlessView {

    private const val TAG = "SeamlessView"

    /**
     * 真实类名。注意是 `com.oplus.animation`，**不是** `com.oplus.animation.viewseamless`
     * ——后者是按 Gradle 坐标猜的，会让 `Class.forName` 直接抛异常，表现为「点了没动画」。
     */
    private val CANDIDATE_CLASSES = listOf(
        "com.oplus.animation.OplusViewSeamless",
        "com.oplus.animation.viewseamless.OplusViewSeamless",
    )

    /** 文档：仅支持动效等级 B+ 及以上机型（`persist.sys.oplus.anim_level <= 3`）。 */
    private const val MAX_ANIM_LEVEL = 3

    // AAR 反编译得到的常量值，运行时读不到字段时兜底
    private const val FALLBACK_KEY_OPEN = "view_seamless_open"
    private const val FALLBACK_KEY_RADIUS = "view_seamless_radius"
    private const val FALLBACK_KEY_COLOR = "view_seamless_color"
    private const val FALLBACK_OS_16_0_BASE = 37000

    private class Api(
        val className: String,
        val setSeamlessView: java.lang.reflect.Method,
        val getVersion: java.lang.reflect.Method?,
        val keyOpen: String,
        val keyRadius: String,
        val keyColor: String,
        /** 以下 key 只有真实实现里才有；取不到就整项不传（系统会忽略未知 key，但没必要瞎猜）。 */
        val keyRect: String?,
        val keyListCover: String?,
        val baseVersion: Int,
    )

    @Volatile
    private var api: Api? = null

    @Volatile
    private var resolved = false

    /** 最近一次点击的结果，给自检面板看。 */
    @Volatile
    var lastResult: String = "尚未调用"
        private set

    /** 系统是否提供该能力（类存在 + 方法签名对得上 + 动效等级够）。 */
    fun isAvailable(): Boolean = resolve() != null && animLevelSupported()

    /** 系统实现版本号，取不到返回 -1。 */
    fun version(): Int = runCatching {
        resolve()?.getVersion?.invoke(null) as? Int ?: -1
    }.getOrDefault(-1)

    /** 设置页一行展示用。 */
    fun statusText(): String = when {
        resolve() == null -> "系统未提供"
        !animLevelSupported() -> "动效等级不足"
        // getVersion 返回 -1 说明拿到的是 Maven 上那个空实现，系统没有接管
        version() < 0 -> "仅空实现"
        else -> "已支持（v${version()}）"
    }

    /**
     * 打开 [intent]，若系统支持则走无缝动画。
     *
     * 按官方文档（id=13772）把能传的参数都传了：
     *
     * | key | 作用 |
     * |---|---|
     * | `VIEW_SEAMLESS_OPEN` | 声明这次 startActivity 走无缝动画（必传） |
     * | `BUNDLE_RADIUS` | 卡片圆角（有圆角必传） |
     * | `BUNDLE_COLOR` | 卡片之外区域的补边色（系统取色失败时兜底） |
     * | `BUNDLE_RECT` | 卡片在屏幕上的区域（框架算错时兜底，**顺手传上更稳**） |
     * | `BUNDLE_LIST_COVER` | 上下被遮挡区域占屏幕的比例：卡片在导航栏 / 顶部渐隐下面时，
     *   动画末端会做 alpha 渐隐，而不是硬缩到被遮住的位置 |
     *
     * @param source 参与动画的源 View（被点击的那张卡片）
     * @param radiusPx 卡片圆角（px）
     * @param fillColor 卡片之外的补边色
     * @param coverTop 顶部被遮挡区域占屏幕高度的比例（顶部渐隐）
     * @param coverBottom 底部被遮挡区域占屏幕高度的比例（悬浮导航栏）
     * @return 是否走了无缝动画
     */
    fun open(
        activity: Activity,
        source: View,
        radiusPx: Float,
        fillColor: Int,
        intent: Intent,
        coverTop: Float = 0f,
        coverBottom: Float = 0f,
    ): Boolean {
        val api = resolve()
        if (api == null) {
            lastResult = "系统未提供 OplusViewSeamless"
            activity.startActivity(intent)
            return false
        }
        if (!animLevelSupported()) {
            lastResult = "动效等级不足（anim_level=${systemProperty("persist.sys.oplus.anim_level")}）"
            activity.startActivity(intent)
            return false
        }

        var bundle: Bundle? = null
        val supported = try {
            val version = api.getVersion?.let { it.invoke(null) as? Int } ?: -1
            // version 为 -1 说明拿到的是 Maven 上的空实现，仍继续调用，让系统实现自己决定
            val tooOld = version > 0 && version <= api.baseVersion
            if (tooOld) {
                lastResult = "系统版本过低（v$version <= ${api.baseVersion}）"
                false
            } else {
                val args = Bundle().apply {
                    putBoolean(api.keyOpen, true)
                    putFloat(api.keyRadius, radiusPx)
                    putInt(api.keyColor, fillColor)
                    // 卡片区域：框架自己算的时候，列表滚动 / 圆角 / 遮挡都可能偏，传一份更稳
                    api.keyRect?.let { key ->
                        val rect = Rect()
                        if (source.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                            putParcelable(key, rect)
                        }
                    }
                    // 上下遮挡比例：卡片在顶部渐隐 / 悬浮导航栏下面时，末端渐隐而不是硬缩
                    api.keyListCover?.let { key ->
                        if (coverTop > 0f || coverBottom > 0f) {
                            putFloatArray(key, floatArrayOf(coverTop, coverBottom))
                        }
                    }
                }
                bundle = args
                val ok = api.setSeamlessView.invoke(null, source, activity, args, null) as? Boolean
                    ?: false
                lastResult = if (ok) {
                    "已请求无缝动画（v$version）"
                } else {
                    "系统返回 false（v$version，可能是浮窗/分屏/动效关闭）"
                }
                ok
            }
        } catch (e: Throwable) {
            // 文档要求自行兜住 RuntimeException / NoSuchMethodError
            lastResult = "调用异常：${e.javaClass.simpleName}"
            Log.w(TAG, "seamless skipped", e)
            false
        }

        return if (supported) {
            activity.startActivity(intent, bundle)
            true
        } else {
            activity.startActivity(intent)
            false
        }
    }

    /** 自检信息，直接贴到设置页的弹窗里。 */
    fun diagnose(): List<Pair<String, String>> {
        val api = resolve()
        return listOf(
            "设备" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "系统" to "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            "ColorOS" to (systemProperty("ro.build.version.oplusrom")
                ?: systemProperty("ro.build.version.opporom")).orUnknown(),
            "动效等级" to systemProperty("persist.sys.oplus.anim_level").orUnknown(),
            "SDK 类" to (api?.className ?: "未找到"),
            "实现版本" to if (api == null) "—" else version().toString(),
            "方法签名" to (api?.setSeamlessView?.let { m ->
                m.parameterTypes.joinToString(", ") { it.simpleName }
            } ?: "—"),
            "BUNDLE_RECT" to (api?.keyRect ?: "系统未提供"),
            "BUNDLE_LIST_COVER" to (api?.keyListCover ?: "系统未提供"),
            "上次点击" to lastResult,
        )
    }

    // ---------------- 反射解析 ----------------

    private fun resolve(): Api? {
        if (resolved) return api
        synchronized(this) {
            if (resolved) return api
            api = CANDIDATE_CLASSES.firstNotNullOfOrNull { name ->
                runCatching { buildApi(name) }.getOrNull()
            }
            resolved = true
            Log.i(TAG, "resolve -> ${api?.className ?: "not found"}")
            return api
        }
    }

    private fun buildApi(className: String): Api? {
        val clazz = Class.forName(className)

        // 静态方法，参数固定 4 个且第一个是 View
        val methods = clazz.declaredMethods.toList() + clazz.methods.toList()
        val setter = methods.firstOrNull { m ->
            m.name == "setSeamlessView" &&
                m.parameterTypes.size == 4 &&
                m.parameterTypes[0] == View::class.java
        } ?: return null
        setter.isAccessible = true

        val getVersion = (clazz.declaredMethods + clazz.methods).firstOrNull {
            it.name == "getVersion" && it.parameterTypes.isEmpty()
        }
        getVersion?.isAccessible = true

        return Api(
            className = className,
            setSeamlessView = setter,
            getVersion = getVersion,
            keyOpen = staticString(clazz, "VIEW_SEAMLESS_OPEN") ?: FALLBACK_KEY_OPEN,
            keyRadius = staticString(clazz, "BUNDLE_RADIUS") ?: FALLBACK_KEY_RADIUS,
            keyColor = staticString(clazz, "BUNDLE_COLOR") ?: FALLBACK_KEY_COLOR,
            keyRect = staticString(clazz, "BUNDLE_RECT"),
            keyListCover = staticString(clazz, "BUNDLE_LIST_COVER"),
            baseVersion = runCatching { clazz.getField("OS_16_0_BASE").getInt(null) }
                .getOrDefault(FALLBACK_OS_16_0_BASE),
        )
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "未检测到"

    private fun staticString(clazz: Class<*>, name: String): String? =
        runCatching { clazz.getField(name).get(null) as? String }.getOrNull()

    // ---------------- 设备判断 ----------------

    private fun animLevelSupported(): Boolean {
        val level = systemProperty("persist.sys.oplus.anim_level")?.toIntOrNull() ?: return true
        return level <= MAX_ANIM_LEVEL
    }

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()
    }.getOrNull()
}
