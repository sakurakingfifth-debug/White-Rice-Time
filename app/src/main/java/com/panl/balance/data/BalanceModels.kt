package com.panl.balance.data

/** 单一币种的余额构成，数值来自 `balance_infos[]`。 */
data class CurrencyBalance(
    val currency: String,
    val total: Double,
    val granted: Double,
    val toppedUp: Double,
)

/** 一次成功请求的结果。`fetchedAt` 用于展示「更新于」与判断缓存是否过期。 */
data class BalanceSnapshot(
    val available: Boolean,
    val items: List<CurrencyBalance>,
    val fetchedAt: Long,
) {
    /** 主展示币种：优先人民币，否则取第一条。 */
    val primary: CurrencyBalance?
        get() = items.firstOrNull { it.currency.equals("CNY", ignoreCase = true) } ?: items.firstOrNull()
}

/** 本地记录的余额变化，用于「最近变动」。 */
data class HistoryEntry(
    val at: Long,
    val currency: String,
    val total: Double,
)

/** 已翻译成用户可读文案的错误。 */
data class ApiError(
    val message: String,
    val detail: String? = null,
    val code: Int? = null,
)

class ApiFailure(val error: ApiError) : Exception(error.message)
