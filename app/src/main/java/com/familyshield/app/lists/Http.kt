package com.familyshield.app.lists

sealed interface HttpResult {
    class Ok(val body: ByteArray, val etag: String?) : HttpResult
    data object NotModified : HttpResult
    class Refused(val code: Int) : HttpResult
    data object TooLarge : HttpResult
    class Failed(val message: String) : HttpResult
}

interface Http {
    fun get(url: String, etag: String?, maxBytes: Long): HttpResult
}
