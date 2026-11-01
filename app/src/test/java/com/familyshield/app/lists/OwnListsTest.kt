package com.familyshield.app.lists

import com.familyshield.app.filter.DomainFilter
import com.familyshield.app.filter.DomainSet
import com.familyshield.app.filter.ListParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.Reader

/** 저장소 lists/ 의 실제 파일을 읽어, 꼭 열려야 하는 주소를 막지 않는지 지킨다 */
class OwnListsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val listsDir = File("../lists").takeIf { it.isDirectory } ?: File("lists")

    /** 앱에 들어 있는 사본 = 저장소 lists/ 의 파일 */
    private fun bundled(id: String): Reader? = File(listsDir, "$id.txt").takeIf { it.isFile }?.reader()

    private fun read(name: String): List<String> {
        val f = File(listsDir, name)
        assertTrue("목록 파일이 없음: ${f.absolutePath}", f.isFile)
        return f.reader().use { ListParser.parse(it).toList() }
    }

    private val ownBlockingEverything by lazy {
        DomainFilter(DomainSet.of(read("block.txt") + read("coupang.txt")), DomainSet.EMPTY)
    }

    @Test
    fun block_list_is_not_empty() {
        assertTrue(read("block.txt").size >= 10)
        assertTrue(read("coupang.txt").isNotEmpty())
    }

    @Test
    fun auto_update_switch_ships_turned_off() {
        // Play 프로텍트가 스스로 업데이트를 막는다(docs/이력.md #2) — 오너가 GitHub 에서 직접 켜기 전엔 꺼져 있어야 한다
        val f = File(listsDir, "AUTO_UPDATE.txt")
        assertTrue("스위치 파일이 없음: ${f.absolutePath}", f.isFile)
        assertFalse(com.familyshield.app.update.UpdateSwitch.isOn(f.readText()))
    }

    @Test
    fun switch_file_adds_no_blocked_address() {
        assertTrue(read("AUTO_UPDATE.txt").isEmpty())
    }

    @Test
    fun official_lottery_sites_stay_open_although_gambling_list_blocks_them() {
        // 2026-11-01 HaGeZi 도박 목록에 실제로 들어 있던 공식 주소들 (오너 결정: 공식 사행산업 사이트는 예외)
        val gamblingListEntries = listOf("dhlottery.co.kr", "bokgwon.go.kr", "klc.go.kr")
        val f = DomainFilter(DomainSet.of(gamblingListEntries), DomainSet.of(read("allow.txt")))
        for (host in listOf("dhlottery.co.kr", "www.dhlottery.co.kr", "m.dhlottery.co.kr", "bokgwon.go.kr", "www.bokgwon.go.kr", "klc.go.kr")) {
            assertFalse("$host 가 막힘", f.isBlocked(host))
        }
    }

    @Test
    fun lottery_exception_does_not_open_unofficial_lookalikes() {
        val f = DomainFilter(DomainSet.of(listOf("nlotto.co.kr", "dhlottery-event.com")), DomainSet.of(read("allow.txt")))
        assertTrue(f.isBlocked("nlotto.co.kr"))
        assertTrue(f.isBlocked("dhlottery-event.com"))
    }

    @Test
    fun coupang_partner_ads_are_always_blocked_and_family_links_stay_open() {
        // '쿠팡 광고 막기' 스위치는 없앰(오너 결정 2026-11-01). 예전에 꺼 둔 폰도 업데이트 뒤엔 똑같이 막아야 한다
        val f = FilterBuilder.build(ListSources.all("owner/family-shield"), ListStore(tmp.newFolder("lists")), ::bundled)
        assertTrue(f.isBlocked("ads-partners.coupang.com"))
        assertFalse(f.isBlocked("link.coupang.com"))
        assertFalse(f.isBlocked("www.coupang.com"))
    }

    @Test
    fun blocks_known_ad_hosts() {
        for (host in listOf("googleads.g.doubleclick.net", "pagead2.googlesyndication.com", "www.googleadservices.com", "display.ad.daum.net")) {
            assertTrue(host, ownBlockingEverything.isBlocked(host))
        }
    }

    @Test
    fun never_blocks_blog_content_or_family_shopping_links() {
        val mustOpen = listOf(
            "tistory.com", "someone.tistory.com", "t1.daumcdn.net", "blog.kakaocdn.net", "img1.daumcdn.net",
            "blogspot.com", "someone.blogspot.com", "www.blogger.com", "blogger.googleusercontent.com",
            "www.coupang.com", "m.coupang.com", "link.coupang.com", "coupa.ng",
            "kakao.com", "talk.kakao.com", "naver.com", "m.naver.com", "www.google.com", "www.youtube.com", "googlevideo.com",
        )
        for (host in mustOpen) {
            assertFalse("$host 가 막힘", ownBlockingEverything.isBlocked(host))
        }
    }
}
