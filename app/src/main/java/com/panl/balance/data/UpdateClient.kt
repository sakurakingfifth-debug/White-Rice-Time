package com.panl.balance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 一次「检查更新」的结果。 */
data class ReleaseInfo(
    /** 原始 tag，例如 `v4.14`。 */
    val tag: String,
    /** 去掉前缀 v 的版本号，用于和 BuildConfig 里的 versionName 比较。 */
    val version: String,
    /** Release 正文，即更新日志（Markdown 原样）。 */
    val changelog: String,
    val pageUrl: String,
    /** Release 里第一个 .apk 附件的直链；没有就为 null。 */
    val apkUrl: String?,
    /** 走备用通道时记录 API 失败的原因，界面上会提示一句。 */
    val apiError: String? = null,
)

/**
 * GitHub Releases 客户端：`GET /repos/{owner}/{repo}/releases/latest`。
 *
 * 和 [DeepSeekClient] 一样只用 `HttpURLConnection` + `org.json`，不引 OkHttp —— 这里只有
 * 一个 GET，加一个 HTTP 库不划算。
 *
 * 注意 GitHub API 对未鉴权的请求按 IP 限流（60 次/小时），检查更新是用户手动点一次的频率，
 * 完全够用；也不需要在客户端塞 token。
 */
class UpdateClient(
    private val endpoint: String = LATEST_API,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000,
) {

    suspend fun latest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        val api = apiLatest()
        if (api.isSuccess) return@withContext api
        val reason = api.exceptionOrNull()?.message

        // API 被限流（同一出口 IP 每小时只有 60 次）时退回两条不碰 API 的通道：
        // ① releases.atom —— 版本号与更新日志都有，只是正文是转义过的 HTML；
        // ② releases/latest 的 302 跳转 —— 只有版本号。
        atomLatest().getOrNull()?.let { return@withContext Result.success(it.copy(apiError = reason)) }
        redirectLatest().getOrNull()?.let { return@withContext Result.success(it.copy(apiError = reason)) }

        // 三条都失败就报 API 的错误，那个信息最有价值
        api
    }

    /** `https://github.com/OWNER/REPO/releases.atom`：不需要 token，也不吃 API 的配额。 */
    private fun atomLatest(): Result<ReleaseInfo> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ATOM_FEED).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return Result.failure(IOException("atom ${conn.responseCode}"))
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val entry = xml.substringAfter("<entry>", "").substringBefore("</entry>")
            if (entry.isBlank()) return Result.failure(IOException("atom 里没有 entry"))

            val tag = Regex("/releases/tag/([^\"]+)").find(entry)?.groupValues?.get(1).orEmpty()
            if (tag.isBlank()) return Result.failure(IOException("atom 里没有 tag"))

            val html = entry.substringAfter("<content type=\"html\">", "").substringBefore("</content>")
            Result.success(
                ReleaseInfo(
                    tag = tag,
                    version = tag.trimStart('v', 'V'),
                    changelog = stripMarkdown(htmlToText(html)),
                    pageUrl = "$REPO_URL/releases/tag/$tag",
                    apkUrl = null,
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /** 把 atom 里那段转义过的 HTML 变成可读纯文本：去标签、反转义、压缩空行。 */
    private fun htmlToText(raw: String): String = raw
        .replace("<br />", "\n")
        .replace("<br/>", "\n")
        .replace("</p>", "\n")
        .replace("</li>", "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** 不跟随 302，直接读 `Location`：`…/releases/tag/v4.14` → `v4.14`。 */

    private fun apiLatest(): Result<ReleaseInfo> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            when {
                code in 200..299 -> Result.success(parse(body))
                code == 404 -> Result.failure(IOException("仓库还没有发布任何版本"))
                else -> Result.failure(IOException(httpReason(code, body)))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /** 不跟随 302，直接读 `Location`：`…/releases/tag/v4.14` → `v4.14`。 */
    private fun redirectLatest(): Result<ReleaseInfo> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(RELEASES_LATEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location").orEmpty().trim()
            val tag = location.substringAfter("/tag/", "").trim()
            if (code in 300..399 && tag.isNotEmpty()) {
                Result.success(
                    ReleaseInfo(
                        tag = tag,
                        version = tag.trimStart('v', 'V'),
                        changelog = "",
                        pageUrl = location,
                        apkUrl = null,
                    )
                )
            } else {
                Result.failure(IOException("备用通道也失败（HTTP $code）"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /** 把 GitHub 的 `{"message": "..."}` 摊平成一句人话，限流单独提示。 */
    private fun httpReason(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        val rateLimited = code == 403 && (
            message.contains("rate limit", ignoreCase = true) ||
                body.contains("rate limit", ignoreCase = true)
            )
        return when {
            rateLimited -> "GitHub 接口限流（每小时 60 次），过一会儿再试"
            message.isNotBlank() -> "HTTP $code：${message.take(80)}"
            else -> "HTTP $code"
        }
    }

    private fun parse(body: String): ReleaseInfo {
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url").ifBlank { null }
                    break
                }
            }
        }
        return ReleaseInfo(
            tag = tag,
            version = tag.trim().trimStart('v', 'V'),
            changelog = stripMarkdown(o.optString("body")),
            pageUrl = o.optString("html_url").ifBlank { REPO_URL },
            apkUrl = apk,
        )
    }

    /** 日志在界面上是纯文本展示，把 Markdown 的记号去掉（保留换行与列表符号）。 */
    private fun stripMarkdown(markdown: String): String = markdown.trim().lines()
        .joinToString("\n") { line ->
            line.trimStart()
                .trimStart('#')     // `## 4.14` → `4.14`
                .trimStart('>')
                .trimStart()
                .replace("**", "")
                .replace("`", "")
                .trimEnd()
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    companion object {
        const val OWNER = "sakurakingfifth-debug"
        const val REPO = "White-Rice-Time"
        const val REPO_URL = "https://github.com/$OWNER/$REPO"
        const val LATEST_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        const val RELEASES_LATEST = "$REPO_URL/releases/latest"
        const val RELEASES_PAGE = "$REPO_URL/releases"
        const val ATOM_FEED = "$REPO_URL/releases.atom"

        private const val USER_AGENT = "PuiBalance (Android)"

        /** 逐段比较版本号：`4.15 > 4.14`；非数字段按 0 处理，`v` 前缀忽略。 */
        fun compare(a: String, b: String): Int {
            val x = segments(a)
            val y = segments(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val diff = (x.getOrNull(i) ?: 0) - (y.getOrNull(i) ?: 0)
                if (diff != 0) return diff
            }
            return 0
        }

        private fun segments(v: String): List<Int> = v.trim()
            .trimStart('v', 'V')
            .split('.', '-', '+', ' ')
            .map { it.toIntOrNull() ?: 0 }
    }
}
