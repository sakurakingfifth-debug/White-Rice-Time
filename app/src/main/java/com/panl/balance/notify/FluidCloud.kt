package com.panl.balance.notify

import android.os.Build
import com.panl.balance.data.CurrencyBalance
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * ColorOS 流体云（Fluid Cloud）适配。
 *
 * 流体云有两条接入路径：
 *
 * 1. **泛在服务卡片**（UPK + `.oml` 模板 + `SeedlingSupportSDK`）—— 能力最全，能出现在气泡、
 *    状态栏胶囊、大胶囊、展开面板、锁屏、AOD、外屏、手表、耳机播报等入口。但要求先向 OPPO
 *    申请泛在卡片服务、UPK 由 OPPO 云端下发、并拿到授权码，个人应用无法自行上线自测。
 *    本仓库 `fluidcloud/` 目录给出了按规范写好的卡片模板，供后续申请通过后直接使用。
 *
 * 2. **Android 16 Live Updates** —— 标准的 promoted ongoing 通知。ColorOS 16（Android 16）
 *    在系统侧把它渲染成流体云胶囊 / 面板，无需 OPPO 授权。这是本应用实际走的路径。
 *
 * 本文件负责第 2 条里「按流体云规范收敛」的部分：
 *
 * - 胶囊文本最多 5 个字符（流体云 2.0 胶囊规范），超长会被系统拉长后以「…」截断；
 * - 胶囊结构固定为「图标 + 文本」，同一实时活动内不改变样式；
 * - 展开面板按「顶部（更新时间）→ 核心（金额 / 构成）→ 底部（构成条）→ 按钮」四段组织。
 *
 * 参考：https://open.oppomobile.com/documentation/page/info?id=12658（流体云模板）
 *      https://open.oppomobile.com/documentation/page/info?id=12965（流体云卡片接入）
 */
object FluidCloud {

    /** 流体云 2.0 胶囊文本上限。 */
    const val CAPSULE_MAX_CHARS = 5

    private val OPLUS_BRANDS = listOf("oppo", "oneplus", "realme", "oplus")

    /** 是否 OPPO 系设备（OPPO / 一加 / 真我）。 */
    fun isOplus(): Boolean {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        if (OPLUS_BRANDS.any { brand.contains(it) || manufacturer.contains(it) }) return true
        // 部分一加/真我机型 Build.BRAND 仍是国际品牌名，靠 ROM 版本号兜底
        return !colorOsVersion().isNullOrBlank()
    }

    /** 读取 ColorOS 版本号，如 `V16.0.1`；非 ColorOS 返回 null。 */
    fun colorOsVersion(): String? {
        val raw = systemProperty("ro.build.version.oplusrom")
            ?: systemProperty("ro.build.version.opporom")
        return raw?.takeIf { it.isNotBlank() }
    }

    /** ColorOS 主版本号，如 `V16.0.1` → 16；取不到返回 0。 */
    fun colorOsMajor(): Int {
        val version = colorOsVersion() ?: return 0
        return Regex("(\\d+)").find(version)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** 系统是否会把实时更新渲染成流体云胶囊。ColorOS 16 起基于 Android 16。 */
    fun supportsFluidCloud(): Boolean = isOplus() && Build.VERSION.SDK_INT >= 36

    /**
     * 胶囊文本：**只放数字，不带货币符号**。
     *
     * 胶囊左边是通知的小图标（`ic_notification` 就是一个 ¥），文字里再带一个 ¥ 就成了
     * 「¥ ¥24」—— 元素重复（3.6 修的）。所以这里输出 `24.03` 这种纯数字，
     * 读起来是「[¥] 24.03」，图标负责货币、文字负责数值。
     *
     * 按「先精确到分、再逐步放宽」的顺序取第一个不超过 5 字的写法：
     * 24.03 → `24.03`；1234.56 → `1235`；128450 → `13万`；128500000 → `1.3亿`。
     */
    fun capsuleText(primary: CurrencyBalance?): String {
        if (primary == null) return "未同步"
        val value = primary.total
        if (value <= 0.0) return "0.00"

        val candidates = buildList {
            // 优先两位小数（用户要求精确到 .00）
            add(fmt("0.00").format(value))
            add(fmt("0.0").format(value))
            add(fmt("0").format(value))
            if (value >= 10_000) {
                add(fmt("0.#").format(value / 10_000) + "万")
                add(fmt("0").format(value / 10_000) + "万")
            }
            if (value >= 100_000_000) {
                add(fmt("0.#").format(value / 100_000_000) + "亿")
                add(fmt("0").format(value / 100_000_000) + "亿")
            }
        }
        return candidates.firstOrNull { it.length <= CAPSULE_MAX_CHARS }
            ?: candidates.last().let { if (it.length <= CAPSULE_MAX_CHARS) it else it.take(4) + "…" }
    }

    /** 固定用 US 符号，避免小数点随系统语言变成逗号。 */
    private fun fmt(pattern: String): DecimalFormat =
        DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US))

    /** 反射读 `android.os.SystemProperties`（SDK 里是隐藏 API）。 */
    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()
    }.getOrNull()
}
