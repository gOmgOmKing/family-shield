package com.familyshield.app.lists

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Reader

class FilterBuilderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { ListStore(tmp.newFolder("lists")) }

    private fun src(id: String, kind: ListKind) =
        ListSource(id, id, "https://example.org/$id.txt", kind, NetworkRule.ANY, 1000, 0)

    private val block = src("block", ListKind.BLOCK)
    private val coupang = src("coupang", ListKind.COUPANG)
    private val allow = src("allow", ListKind.ALLOW)
    private val community = src("community", ListKind.BLOCK)

    private val bundled = mutableMapOf<String, String>()
    private fun openBundled(id: String): Reader? = bundled[id]?.reader()

    private fun build(coupangOn: Boolean = true) =
        FilterBuilder.build(listOf(block, coupang, allow, community), store, ::openBundled, coupangOn)

    private fun download(source: ListSource, body: String) {
        store.saveDownload(source.id, body.toByteArray(), etag = null, entries = 1, now = 1)
    }

    @Test
    fun uses_bundled_copy_until_first_download() {
        bundled["block"] = "ads.example.com\n"
        assertTrue(build().isBlocked("ads.example.com"))
    }

    @Test
    fun prefers_downloaded_copy_over_bundled() {
        bundled["block"] = "old.example.com\n"
        download(block, "new.example.com\n")

        val f = build()
        assertTrue(f.isBlocked("new.example.com"))
        assertFalse(f.isBlocked("old.example.com"))
    }

    @Test
    fun coupang_list_blocks_only_when_switch_is_on() {
        bundled["coupang"] = "ads-partners.coupang.com\n"
        assertTrue(build(coupangOn = true).isBlocked("ads-partners.coupang.com"))
        assertFalse(build(coupangOn = false).isBlocked("ads-partners.coupang.com"))
    }

    @Test
    fun allow_list_overrides_every_block_list() {
        download(community, "shop.example.com\n")
        bundled["block"] = "shop.example.com\n"
        bundled["allow"] = "shop.example.com\n"
        assertFalse(build().isBlocked("shop.example.com"))
    }

    @Test
    fun counts_community_list_once_it_is_downloaded() {
        assertFalse(build().isBlocked("tracker.example.net"))
        download(community, "tracker.example.net\n")
        assertTrue(build().isBlocked("tracker.example.net"))
    }
}
