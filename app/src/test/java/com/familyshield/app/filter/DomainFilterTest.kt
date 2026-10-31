package com.familyshield.app.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFilterTest {

    private fun filter(block: List<String>, allow: List<String> = emptyList()) =
        DomainFilter(DomainSet.of(block), DomainSet.of(allow))

    @Test
    fun blocks_listed_domain() {
        assertTrue(filter(listOf("ads.example.com")).isBlocked("ads.example.com"))
    }

    @Test
    fun blocks_subdomain_of_listed_domain() {
        assertTrue(filter(listOf("doubleclick.net")).isBlocked("googleads.g.doubleclick.net"))
    }

    @Test
    fun does_not_block_parent_of_listed_domain() {
        assertFalse(filter(listOf("ads.example.com")).isBlocked("example.com"))
    }

    @Test
    fun does_not_block_domain_that_only_ends_with_same_letters() {
        assertFalse(filter(listOf("example.com")).isBlocked("myexample.com"))
    }

    @Test
    fun does_not_block_unlisted_domain() {
        assertFalse(filter(listOf("ads.example.com")).isBlocked("naver.com"))
    }

    @Test
    fun never_blocks_whole_top_level_domain() {
        assertFalse(filter(listOf("com", "kr")).isBlocked("example.com"))
        assertFalse(filter(listOf("com", "kr")).isBlocked("naver.kr"))
    }

    @Test
    fun allow_list_wins_over_block_list() {
        val f = filter(block = listOf("coupang.com"), allow = listOf("www.coupang.com"))
        assertFalse(f.isBlocked("www.coupang.com"))
        assertFalse(f.isBlocked("img.www.coupang.com"))
        assertTrue(f.isBlocked("link.coupang.com"))
    }

    @Test
    fun ignores_letter_case_and_trailing_dot() {
        assertTrue(filter(listOf("ads.example.com")).isBlocked("ADS.Example.COM."))
    }

    @Test
    fun counts_unique_domains() {
        assertTrue(DomainSet.of(listOf("a.com", "A.com", "b.com")).size == 2)
    }
}
