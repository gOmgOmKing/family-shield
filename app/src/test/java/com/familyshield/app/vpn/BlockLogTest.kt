package com.familyshield.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockLogTest {

    @Test
    fun counts_each_blocked_name() {
        val log = BlockLog(startCount = 0)
        log.record("a.com", 1_000)
        log.record("b.com", 1_000)
        assertEquals(2, log.total)
    }

    @Test
    fun counts_repeated_queries_for_same_name_once_within_ten_seconds() {
        // 브라우저는 주소 하나에 A·AAAA·HTTPS 질의를 거의 동시에 보낸다
        val log = BlockLog(startCount = 0)
        log.record("ads.com", 1_000)
        log.record("ads.com", 1_050)
        log.record("ads.com", 9_000)
        assertEquals(1, log.total)
        log.record("ads.com", 20_000)
        assertEquals(2, log.total)
    }

    @Test
    fun recent_list_is_newest_first_and_limited() {
        val log = BlockLog(startCount = 0, keep = 3)
        listOf("a.com", "b.com", "c.com", "d.com").forEachIndexed { i, n -> log.record(n, i * 60_000L) }
        assertEquals(listOf("d.com", "c.com", "b.com"), log.recent().map { it.name })
    }

    @Test
    fun continues_from_saved_count() {
        val log = BlockLog(startCount = 41)
        log.record("a.com", 0)
        assertEquals(42, log.total)
    }
}
