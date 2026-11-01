package com.familyshield.app.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 토토 후보 관찰기(collector/)가 collector-data 브랜치에 매일 쓰는 차단 파일을 폰이 받아 쓰는지 지킨다 */
class TotoCandidatesListTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val repo = "owner/family-shield"
    private val totoUrl = "https://raw.githubusercontent.com/$repo/collector-data/collector/block.txt"
    private val sources = ListSources.all(repo)
    private val http = FakeHttp()
    private val store by lazy { ListStore(tmp.newFolder("lists")) }
    private val updater by lazy { ListUpdater(store, http, now = { 1_000L }, pause = {}) }
    private val toto get() = sources.single { it.url == totoUrl }

    // 스위치가 전부 '끄기'일 때 봇이 쓰는 모양 (2026-11-01 collector-data 브랜치에서 확인)
    private val header = "# 토토 후보 차단 목록 — 자동 생성, 손으로 고치지 마세요\n# 켠 분류: 없음\n# 개수: 0\n"

    private val listsDir = File("../lists").takeIf { it.isDirectory } ?: File("lists")

    @Test
    fun phone_asks_for_the_collector_block_file_on_wifi_only() {
        updater.update(sources, unmetered = false)
        assertFalse(http.requests.any { it.url == totoUrl })

        updater.update(sources, unmetered = true)
        assertTrue(http.requests.any { it.url == totoUrl })
    }

    @Test
    fun all_switches_off_file_is_a_normal_empty_list_not_a_failure() {
        http.answers[totoUrl] = HttpResult.Ok(header.toByteArray(), "\"v1\"")

        updater.update(listOf(toto), unmetered = true)

        val s = store.status(toto.id)
        assertNull(s.error)
        assertEquals(0, s.entries)
    }

    @Test
    fun switched_on_candidates_are_blocked_but_allow_list_still_wins() {
        val body = header + "betkrw33.xyz\nxn--vf4b27jfqja61l.com\ndhlottery.co.kr\n"
        http.answers[totoUrl] = HttpResult.Ok(body.toByteArray(), "\"v2\"")
        updater.update(listOf(toto), unmetered = true)

        val f = FilterBuilder.build(sources, store, { id -> File(listsDir, "$id.txt").takeIf { it.isFile }?.reader() }, coupangOn = true)

        assertTrue(f.isBlocked("betkrw33.xyz"))
        assertTrue(f.isBlocked("www.betkrw33.xyz"))
        assertTrue(f.isBlocked("xn--vf4b27jfqja61l.com")) // 토토사이트.com
        assertFalse("허용 목록의 공식 복권 사이트가 막힘", f.isBlocked("dhlottery.co.kr"))
    }
}
