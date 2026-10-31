package com.familyshield.app.vpn

import com.familyshield.app.dns.DnsMessage
import com.familyshield.app.dns.dnsQuery
import com.familyshield.app.filter.DomainFilter
import com.familyshield.app.filter.DomainSet
import com.familyshield.app.net.IpPacket
import com.familyshield.app.net.ipv4Udp
import com.familyshield.app.net.ipv6Udp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsInterceptorTest {

    private val phone4 = byteArrayOf(10, 111, 222.toByte(), 1)
    private val dns4 = byteArrayOf(10, 111, 222.toByte(), 2)
    private val phone6 = ByteArray(16) { if (it == 0) 0xfd.toByte() else if (it == 15) 1 else 0 }
    private val dns6 = ByteArray(16) { if (it == 0) 0xfd.toByte() else if (it == 15) 2 else 0 }

    private var filter = DomainFilter(DomainSet.of(listOf("doubleclick.net")), DomainSet.EMPTY)
    private val interceptor = DnsInterceptor { filter }

    private fun inspect(packet: ByteArray) = interceptor.inspect(packet, packet.size)

    @Test
    fun answers_blocked_name_with_nxdomain_to_sender() {
        val query = dnsQuery(id = 0x4242, name = "googleads.g.doubleclick.net")
        val verdict = inspect(ipv4Udp(phone4, dns4, 51000, 53, query))

        assertTrue(verdict is Verdict.Block)
        verdict as Verdict.Block
        assertEquals("googleads.g.doubleclick.net", verdict.name)
        val back = IpPacket.parseUdp(verdict.reply, verdict.reply.size)!!
        assertArrayEquals(phone4, back.dstAddr)
        assertEquals(51000, back.dstPort)
        val dns = back.payload
        assertEquals(0x4242, ((dns[0].toInt() and 0xFF) shl 8) or (dns[1].toInt() and 0xFF))
        assertEquals("RCODE NXDOMAIN", 3, dns[3].toInt() and 0x0F)
    }

    @Test
    fun answers_blocked_name_over_ipv6_too() {
        val verdict = inspect(ipv6Udp(phone6, dns6, 51000, 53, dnsQuery(name = "doubleclick.net", type = 65)))
        assertTrue(verdict is Verdict.Block)
        val back = IpPacket.parseUdp((verdict as Verdict.Block).reply, verdict.reply.size)!!
        assertArrayEquals(phone6, back.dstAddr)
    }

    @Test
    fun forwards_allowed_name_unchanged() {
        val query = dnsQuery(name = "blog.tistory.com", withEdns = true)
        val verdict = inspect(ipv4Udp(phone4, dns4, 51000, 53, query))

        assertTrue(verdict is Verdict.Forward)
        verdict as Verdict.Forward
        assertEquals("blog.tistory.com", verdict.name)
        assertArrayEquals(query, verdict.request.payload)
        assertEquals(51000, verdict.request.srcPort)
    }

    @Test
    fun uses_latest_filter_after_list_update() {
        val packet = ipv4Udp(phone4, dns4, 51000, 53, dnsQuery(name = "ads.example.com"))
        assertTrue(inspect(packet) is Verdict.Forward)

        filter = DomainFilter(DomainSet.of(listOf("example.com")), DomainSet.EMPTY)
        assertTrue(inspect(packet) is Verdict.Block)
    }

    @Test
    fun ignores_packet_not_sent_to_dns_port() {
        val packet = ipv4Udp(phone4, dns4, 51000, 853, dnsQuery(name = "doubleclick.net"))
        assertEquals(Verdict.Ignore, inspect(packet))
    }

    @Test
    fun ignores_udp_payload_that_is_not_a_dns_query() {
        val packet = ipv4Udp(phone4, dns4, 51000, 53, "garbage".toByteArray())
        assertEquals(Verdict.Ignore, inspect(packet))
    }

    @Test
    fun ignores_non_udp_packet() {
        val packet = ipv4Udp(phone4, dns4, 51000, 53, dnsQuery(name = "doubleclick.net"))
        packet[9] = 6
        assertEquals(Verdict.Ignore, inspect(packet))
    }

    @Test
    fun blocked_reply_question_matches_query() {
        val query = dnsQuery(name = "doubleclick.net")
        val verdict = inspect(ipv4Udp(phone4, dns4, 51000, 53, query)) as Verdict.Block
        val dns = IpPacket.parseUdp(verdict.reply, verdict.reply.size)!!.payload
        assertEquals("doubleclick.net", DnsMessage.parseQuery(dns.copyOf().also { it[2] = 1 })!!.name)
    }
}
