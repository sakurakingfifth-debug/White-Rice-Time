package com.panl.balance.ui

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 金额、时间、增量的展示格式，全应用统一走这里，避免各页面各写一套。 */
object Fmt {

    fun symbol(currency: String): String = when (currency.uppercase(Locale.ROOT)) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "HKD" -> "HK$"
        "JPY" -> "¥"
        else -> currency.uppercase(Locale.ROOT) + " "
    }

    fun amount(value: Double): String = DecimalFormat("#,##0.00").format(value)

    fun money(value: Double, currency: String): String = symbol(currency) + amount(value)

    /** 今天只显示时分，更早带上日期。 */
    fun clock(at: Long): String {
        val target = Calendar.getInstance().apply { timeInMillis = at }
        val now = Calendar.getInstance()
        val sameDay = target.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
        return SimpleDateFormat(pattern, Locale.CHINA).format(Date(at))
    }

    fun updated(at: Long): String = "更新于 " + clock(at)

    fun relative(at: Long): String {
        val diff = System.currentTimeMillis() - at
        return when {
            diff in 0..59_000 -> "刚刚更新"
            diff in 60_000..3_600_000 -> "${diff / 60_000} 分钟前更新"
            else -> updated(at)
        }
    }

    fun delta(value: Double, currency: String): String = when {
        abs(value) < 0.005 -> "无变化"
        value > 0 -> "+" + money(value, currency)
        else -> "-" + money(-value, currency)
    }

    /** 密钥脱敏：保留前缀与末 4 位。 */
    fun maskKey(key: String): String {
        if (key.isBlank()) return "未配置"
        val tail = key.takeLast(4)
        return "sk-••••••••" + tail
    }
}
