package com.familyshield.app.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ListUpdaterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 24 * 60 * 60 * 1000L
    private var now = 1_000_000L
    private var pauses = 0
    private val http = FakeHttp()
    private val store by lazy { ListStore(tmp.newFolder("lists")) }
    private val updater by lazy { ListUpdater(store, http, now = { now }, pause = { pauses++ }) }

    private val big = ListSource("big", "큰 목록", "https://raw.example.org/big.txt", ListKind.BLOCK, NetworkRule.UNMETERED_ONLY, maxBytes = 1000, minEntries = 3)
    private val small = ListSource("small", "작은 목록", "https://raw.example.org/small.txt", ListKind.BLOCK, NetworkRule.ANY, maxBytes = 1000, minEntries = 0)
    private val other = ListSource("other", "다른 곳", "https://cdn.example.net/other.txt", ListKind.BLOCK, NetworkRule.ANY, maxBytes = 1000, minEntries = 0)

    private val goodBody = "a.example.com\nb.example.com\nc.example.com\n"

    @Test
    fun saves_downloaded_list_with_entry_count_and_etag() {
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), "\"v1\"")
        updater.update(listOf(big), unmetered = true)

        assertEquals(goodBody, store.file(big.id).readText())
        val s = store.status(big.id)
        assertEquals(3, s.entries)
        assertEquals("\"v1\"", s.etag)
        assertEquals(now, s.lastSuccess)
        assertNull(s.error)
    }

    @Test
    fun sends_saved_etag_and_keeps_file_when_not_modified() {
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), "\"v1\"")
        updater.update(listOf(big), unmetered = true)
        now += day
        http.answers[big.url] = HttpResult.NotModified

        updater.update(listOf(big), unmetered = true)

        assertEquals("\"v1\"", http.requests.last().etag)
        assertEquals(goodBody, store.file(big.id).readText())
        assertEquals(now, store.status(big.id).lastSuccess)
    }

    @Test
    fun keeps_last_good_copy_and_shows_error_when_download_fails() {
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), null)
        updater.update(listOf(big), unmetered = true)
        val firstSuccess = now
        now += day
        http.answers[big.url] = HttpResult.Failed("연결 시간 초과")

        updater.update(listOf(big), unmetered = true)

        assertEquals(goodBody, store.file(big.id).readText())
        val s = store.status(big.id)
        assertEquals(firstSuccess, s.lastSuccess)
        assertEquals(now, s.lastAttempt)
        assertNotNull(s.error)
    }

    @Test
    fun rejects_body_that_is_a_web_page_instead_of_a_list() {
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), null)
        updater.update(listOf(big), unmetered = true)
        // 공용 와이파이 로그인 화면처럼, 주소처럼 보이는 줄이 충분히 섞인 웹페이지
        val portal = "<!DOCTYPE html>\n<html>\n<body>\nwifi.example.com\nlogin.example.com\nhelp.example.com\nterms.example.com\n</body>\n</html>\n"
        http.answers[big.url] = HttpResult.Ok(portal.toByteArray(), null)

        updater.update(listOf(big), unmetered = true)

        assertEquals(goodBody, store.file(big.id).readText())
        assertNotNull(store.status(big.id).error)
    }

    @Test
    fun rejects_list_with_too_few_entries() {
        http.answers[big.url] = HttpResult.Ok("only.one.com\n".toByteArray(), null)
        updater.update(listOf(big), unmetered = true)

        assertFalse(store.file(big.id).exists())
        assertNotNull(store.status(big.id).error)
    }

    @Test
    fun rejects_list_over_size_cap() {
        http.answers[big.url] = HttpResult.TooLarge
        updater.update(listOf(big), unmetered = true)

        assertFalse(store.file(big.id).exists())
        assertNotNull(store.status(big.id).error)
        assertEquals(1000L, http.requests.single().maxBytes)
    }

    @Test
    fun clears_error_after_next_success() {
        http.answers[small.url] = HttpResult.Failed("x")
        updater.update(listOf(small), unmetered = false)
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), null)

        updater.update(listOf(small), unmetered = false)

        assertNull(store.status(small.id).error)
    }

    @Test
    fun skips_wifi_only_list_on_metered_network() {
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), null)
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), null)

        updater.update(listOf(big, small), unmetered = false)

        assertEquals(listOf(small.url), http.requests.map { it.url })
    }

    @Test
    fun stops_asking_a_server_for_a_day_after_it_refuses() {
        http.answers[small.url] = HttpResult.Refused(429)
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), null)
        http.answers[other.url] = HttpResult.Ok("x.com\n".toByteArray(), null)

        updater.update(listOf(small, big, other), unmetered = true)
        assertEquals("같은 서버(raw.example.org)는 더 묻지 않는다", listOf(small.url, other.url), http.requests.map { it.url })
        assertNotNull(store.status(small.id).error)
        assertNotNull(store.status(big.id).error)

        http.requests.clear()
        now += day / 2
        updater.update(listOf(small, big), unmetered = true)
        assertTrue("반나절 뒤에도 묻지 않는다", http.requests.isEmpty())

        now += day
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), null)
        updater.update(listOf(small, big), unmetered = true)
        assertEquals(listOf(small.url, big.url), http.requests.map { it.url })
    }

    @Test
    fun pauses_between_requests_but_not_before_first() {
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), null)
        http.answers[other.url] = HttpResult.Ok("y.com\n".toByteArray(), null)

        updater.update(listOf(small, other), unmetered = false)

        assertEquals(1, pauses)
    }

    @Test
    fun reports_whether_anything_changed() {
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), "\"1\"")
        assertTrue(updater.update(listOf(small), unmetered = false))
        http.answers[small.url] = HttpResult.NotModified
        assertFalse(updater.update(listOf(small), unmetered = false))
    }

    /** 폰 저장 공간이 꽉 찬 것과 같은 실패: 임시 파일 자리에 쓸 수 없게 만든다 */
    private fun makeSavingFail(source: ListSource) = File(store.dir, "${source.id}.txt.tmp").mkdirs()

    @Test
    fun shows_error_instead_of_crashing_when_phone_cannot_save_list() {
        // 예외가 밖으로 나가면 안드로이드가 앱 전체를 끈다 — VPN(보호)도 같이 꺼진다
        makeSavingFail(big)
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), "\"v1\"")

        assertFalse(updater.update(listOf(big), unmetered = true))

        assertFalse(store.file(big.id).exists())
        assertNotNull(store.status(big.id).error)
        assertNull("저장 못 한 목록의 ETag 를 남기면 다음에 304 만 받고 영영 못 받는다", store.status(big.id).etag)
    }

    @Test
    fun other_lists_are_still_received_after_one_cannot_be_saved() {
        makeSavingFail(big)
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), null)
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), null)

        updater.update(listOf(big, small), unmetered = true)

        assertEquals("x.com\n", store.file(small.id).readText())
    }

    @Test
    fun leaves_no_temp_file_behind_when_saving_fails() {
        // 저장 공간이 꽉 찼을 때 반쯤 쓴 임시 파일이 남으면 폰의 남은 공간을 계속 차지한다
        makeSavingFail(big)
        http.answers[big.url] = HttpResult.Ok(goodBody.toByteArray(), null)

        updater.update(listOf(big), unmetered = true)

        assertEquals(emptyList<String>(), store.dir.list()!!.filter { it.endsWith(".tmp") })
    }

    @Test
    fun status_survives_restart() {
        http.answers[small.url] = HttpResult.Ok("x.com\n".toByteArray(), "\"e\"")
        updater.update(listOf(small), unmetered = false)

        val reopened = ListStore(store.dir)
        assertEquals("\"e\"", reopened.status(small.id).etag)
        assertEquals(1, reopened.status(small.id).entries)
    }
}

class FakeHttp : Http {
    class Request(val url: String, val etag: String?, val maxBytes: Long)

    val answers = mutableMapOf<String, HttpResult>()
    val requests = mutableListOf<Request>()

    override fun get(url: String, etag: String?, maxBytes: Long): HttpResult {
        requests.add(Request(url, etag, maxBytes))
        return answers[url] ?: HttpResult.Failed("no answer")
    }
}
