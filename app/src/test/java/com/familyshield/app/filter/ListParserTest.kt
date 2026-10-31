package com.familyshield.app.filter

import org.junit.Assert.assertEquals
import org.junit.Test

class ListParserTest {

    private fun parse(text: String) = ListParser.parse(text.reader()).toList()

    @Test
    fun reads_plain_domain_per_line() {
        assertEquals(listOf("ads.example.com", "track.example.net"), parse("ads.example.com\ntrack.example.net\n"))
    }

    @Test
    fun reads_hosts_file_lines_with_trailing_comment() {
        assertEquals(
            listOf("ads.example.com", "pop.example.org"),
            parse("0.0.0.0 ads.example.com\n127.0.0.1\tpop.example.org # popup\n"),
        )
    }

    @Test
    fun reads_adblock_domain_rule() {
        assertEquals(listOf("ads.example.com"), parse("||ads.example.com^\n"))
    }

    @Test
    fun reads_wildcard_domain() {
        assertEquals(listOf("ads.example.com"), parse("*.ads.example.com\n"))
    }

    @Test
    fun lowercases_domains() {
        assertEquals(listOf("ads.example.com"), parse("ADS.Example.com\n"))
    }

    @Test
    fun skips_comments_blank_lines_and_localhost() {
        val text = """
            # comment
            ! adblock comment

            127.0.0.1 localhost
            0.0.0.0 0.0.0.0
            ads.example.com
        """.trimIndent()
        assertEquals(listOf("ads.example.com"), parse(text))
    }

    @Test
    fun never_treats_exception_rule_as_block() {
        assertEquals(emptyList<String>(), parse("@@||coupang.com^\n"))
    }

    @Test
    fun skips_rules_that_are_not_whole_domains() {
        val text = "||example.com/ads^\n||example.com^\$third-party\n##.banner\nexample\nhttps://example.com\n"
        assertEquals(emptyList<String>(), parse(text))
    }
}
