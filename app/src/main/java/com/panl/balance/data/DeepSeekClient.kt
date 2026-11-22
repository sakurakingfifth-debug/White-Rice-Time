package com.panl.balance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * DeepSeek 余额接口客户端。
 *
 * 接口：`GET https://api.deepseek.com/user/balance`
 * 鉴权：`Authorization: Bearer sk-...`
 *
 * 返回体：
 * ```json
 * { "is_available": true,
 *   "balance_infos": [ { "currency": "CNY", "total_balance": "110.00",
 *                        "granted_balance": "10.00", "topped_up_balance": "100.00" } ] }
 * ```
 *
 * 这里刻意只用 `HttpURLConnection` + `org.json`：不引入 OkHttp / Gson / Retrofit，
 * 整个 APK 的依赖面只有 Compose，体积和供应链风险都可控。
 */
class DeepSeekClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    /** 桌面插件走短超时：goAsync 的广播预算只有约 10 秒。 */
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
) {

    suspend fun fetch(apiKey: String): Result<BalanceSnapshot> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PuiBalance/1.0 (Android)")
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                return@withContext Result.failure(ApiFailure(mapHttpError(code, body)))
            }
            Result.success(parse(body))
        } catch (e: UnknownHostException) {
            Result.failure(
                ApiFailure(ApiError("无法连接网络", "域名解析失败，请检查网络或代理设置。"))
            )
        } catch (e: SocketTimeoutException) {
            Result.failure(
                ApiFailure(ApiError("请求超时", "网络较慢，稍后重试或调大刷新间隔。"))
            )
        } catch (e: SSLException) {
            Result.failure(
                ApiFailure(ApiError("安全连接失败", "TLS 握手被中断，可能是代理或证书拦截。"))
            )
        } catch (e: IOException) {
            Result.failure(
                ApiFailure(ApiError("网络异常", e.message ?: "连接被中断，请稍后重试。"))
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                ApiFailure(ApiError("解析失败", "返回内容不是预期的 JSON：${e.message}"))
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(body: String): BalanceSnapshot {
        val root = JSONObject(body)
        val available = root.optBoolean("is_available", false)
        val arr = root.optJSONArray("balance_infos") ?: JSONArray()
        val items = ArrayList<CurrencyBalance>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            items += CurrencyBalance(
                currency = o.optString("currency").ifBlank { "CNY" },
                total = o.decimal("total_balance"),
                granted = o.decimal("granted_balance"),
                toppedUp = o.decimal("topped_up_balance"),
            )
        }
        return BalanceSnapshot(available = available, items = items, fetchedAt = System.currentTimeMillis())
    }

    /** 接口里的金额是字符串（`"110.00"`），但按数字返回也兼容。 */
    private fun JSONObject.decimal(key: String): Double {
        val raw = optString(key, "")
        return raw.toDoubleOrNull() ?: optDouble(key, 0.0)
    }

    private fun mapHttpError(code: Int, body: String): ApiError {
        val serverMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().trimmed()

        return when (code) {
            400 -> ApiError("请求不合法", serverMessage, code)
            // 401 的原始文案是英文 + request_id，对用户没有价值，统一换成可执行的建议
            401 -> ApiError(
                "密钥无效",
                "服务端拒绝了这个 API Key：请确认复制完整、未被删除，或在开放平台重新生成。",
                code
            )
            402 -> ApiError("余额不足", serverMessage, code)
            403 -> ApiError("没有访问权限", serverMessage, code)
            404 -> ApiError("接口地址不存在", serverMessage, code)
            429 -> ApiError("请求过于频繁", "稍后再试，或把自动刷新间隔调大。", code)
            in 500..599 -> ApiError("DeepSeek 服务异常", serverMessage ?: "HTTP $code", code)
            else -> ApiError("请求失败", serverMessage ?: "HTTP $code", code)
        }
    }

    /** 服务端文案可能带 request_id 且很长，截断到一行可读长度。 */
    private fun String?.trimmed(max: Int = 120): String? = this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { if (it.length > max) it.take(max) + "…" else it }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/user/balance"
        const val TOP_UP_URL = "https://platform.deepseek.com/top_up"
        const val KEYS_URL = "https://platform.deepseek.com/api_keys"
    }
}
