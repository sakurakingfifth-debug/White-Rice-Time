package com.panl.balance.data

import java.util.Calendar
import java.util.TimeZone

/**
 * DeepSeek 的高峰 / 低峰（错峰）时段判断。
 *
 * 官方规则：**北京时间、周一至周五（不含法定节假日）09:00–12:00 与 14:00–18:00 为高峰时段**，
 * 其余时间（含周末全天）为空闲时段，单价是高峰的一半。所以这个判断只影响「现在调用贵不贵」，
 * 与余额本身无关。
 *
 * 一个已知的近似：法定节假日官方算空闲，但节假日表每年由国务院公布，这里没有内置 ——
 * 节假日里会被判成高峰，只影响那几天的提示文案，不影响任何计费逻辑。
 */
object PeakHours {

    /** 官方按北京时间算，跟设备时区无关。 */
    private val ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private const val MORNING_START = 9 * 60
    private const val MORNING_END = 12 * 60
    private const val AFTERNOON_START = 14 * 60
    private const val AFTERNOON_END = 18 * 60

    private const val DAY = 24L * 60 * 60 * 1000

    /** 现在是不是高峰时段。 */
    fun isPeak(now: Long = System.currentTimeMillis()): Boolean {
        val cal = calendar(now)
        if (!isWeekday(cal)) return false
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return minutes in MORNING_START until MORNING_END ||
            minutes in AFTERNOON_START until AFTERNOON_END
    }

    /** 下一次高峰 / 低峰切换的时刻。 */
    fun nextSwitch(now: Long = System.currentTimeMillis()): Long {
        val cal = calendar(now)
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        if (isWeekday(cal)) {
            when {
                minutes < MORNING_START -> return atMinute(cal, MORNING_START)
                minutes < MORNING_END -> return atMinute(cal, MORNING_END)
                minutes < AFTERNOON_START -> return atMinute(cal, AFTERNOON_START)
                minutes < AFTERNOON_END -> return atMinute(cal, AFTERNOON_END)
            }
        }
        // 今天已经没有高峰了（或周末）：往后找第一个工作日的 09:00
        var cursor = calendar(now)
        repeat(8) {
            cursor = calendar(cursor.timeInMillis + DAY)
            if (isWeekday(cursor)) return atMinute(cursor, MORNING_START)
        }
        return now + DAY
    }

    /** 距下一次切换还有多久，如「3 小时 12 分」。 */
    fun remaining(now: Long = System.currentTimeMillis()): String {
        val diff = (nextSwitch(now) - now).coerceAtLeast(0)
        val totalMinutes = (diff + 59_999) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
            hours > 0 -> "$hours 小时"
            else -> "$minutes 分"
        }
    }

    /** 一句话概括，给首页卡片用。 */
    fun summary(now: Long = System.currentTimeMillis()): String =
        if (isPeak(now)) {
            "高峰时段 · 距低峰 ${remaining(now)}"
        } else {
            "低峰时段 · 单价 5 折 · 距高峰 ${remaining(now)}"
        }

    // ---------------- 内部 ----------------

    private fun calendar(now: Long): Calendar =
        Calendar.getInstance(ZONE).apply { timeInMillis = now }

    private fun isWeekday(cal: Calendar): Boolean {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return day != Calendar.SATURDAY && day != Calendar.SUNDAY
    }

    /** [base] 那一天的某个「当天第几分钟」，秒与毫秒归零。 */
    private fun atMinute(base: Calendar, minuteOfDay: Int): Long {
        val copy = base.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        copy.set(Calendar.MINUTE, minuteOfDay % 60)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }
}
