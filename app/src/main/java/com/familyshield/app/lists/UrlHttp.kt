package com.familyshield.app.lists

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 실제 인터넷 요청. 우리가 누군지 User-Agent 에 밝히고, 재시도는 하지 않는다(다음 정기 실행에 맡김).
 * 안드로이드의 HttpURLConnection 은 gzip 압축 전송을 스스로 요청하고 풀어 준다.
 */
class UrlHttp(private val userAgent: String) : Http {

    override fun get(url: String, etag: String?, maxBytes: Long): HttpResult {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return HttpResult.Failed(e.javaClass.simpleName)
        }
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent)
            if (etag != null) conn.setRequestProperty("If-None-Match", etag)

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> readCapped(conn, maxBytes)
                HttpURLConnection.HTTP_NOT_MODIFIED -> HttpResult.NotModified
                403, 429 -> HttpResult.Refused(code)
                else -> HttpResult.Failed("HTTP $code")
            }
        } catch (e: IOException) {
            HttpResult.Failed(describe(e))
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(conn: HttpURLConnection, maxBytes: Long): HttpResult {
        if (conn.contentLengthLong > maxBytes) return HttpResult.TooLarge
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        conn.inputStream.use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return HttpResult.TooLarge
            }
        }
        return HttpResult.Ok(out.toByteArray(), conn.getHeaderField("ETag"))
    }

    private fun describe(e: IOException): String = when (e) {
        is java.net.SocketTimeoutException -> "응답 시간 초과"
        is java.net.UnknownHostException -> "인터넷 연결 없음"
        else -> e.javaClass.simpleName
    }
}
