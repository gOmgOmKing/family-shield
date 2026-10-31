package com.familyshield.app.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsMessageTest {

    @Test
    fun reads_id_name_and_type_of_query() {
        val q = DnsMessage.parseQuery(dnsQuery(id = 0x1234, name = "Ads.Example.com", type = 28))
        assertNotNull(q)
        assertEquals(0x1234, q!!.id)
        assertEquals("ads.example.com", q.name)
        assertEquals(28, q.type)
    }

    @Test
    fun rejects_response_messages() {
        val msg = dnsQuery(name = "ads.example.com")
        msg[2] = (msg[2].toInt() or 0x80).toByte()
        assertNull(DnsMessage.parseQuery(msg))
    }

    @Test
    fun rejects_message_shorter_than_header() {
        assertNull(DnsMessage.parseQuery(ByteArray(11)))
    }

    @Test
    fun rejects_label_running_past_end() {
        val msg = dnsQuery(name = "ads.example.com")
        assertNull(DnsMessage.parseQuery(msg.copyOf(20)))
    }

    @Test
    fun rejects_message_with_no_question() {
        val msg = dnsQuery(name = "ads.example.com")
        msg[5] = 0
        assertNull(DnsMessage.parseQuery(msg))
    }

    @Test
    fun nxdomain_reply_keeps_id_and_question() {
        val query = dnsQuery(id = 0xBEEF, name = "ads.example.com", type = 1)
        val reply = DnsMessage.nxdomain(query, DnsMessage.parseQuery(query)!!)

        assertEquals(0xBEEF, u16(reply, 0))
        assertArrayEquals(query.copyOfRange(12, query.size), reply.copyOfRange(12, reply.size))
    }

    @Test
    fun nxdomain_reply_is_a_response_with_name_error_code() {
        val query = dnsQuery(name = "ads.example.com")
        val reply = DnsMessage.nxdomain(query, DnsMessage.parseQuery(query)!!)
        val flags = u16(reply, 2)

        assertEquals("QR", 1, flags shr 15)
        assertEquals("RD copied", 1, (flags shr 8) and 1)
        assertEquals("RCODE", 3, flags and 0xF)
        assertEquals("QDCOUNT", 1, u16(reply, 4))
        assertEquals("ANCOUNT", 0, u16(reply, 6))
        assertEquals("NSCOUNT", 0, u16(reply, 8))
        assertEquals("ARCOUNT", 0, u16(reply, 10))
    }

    @Test
    fun nxdomain_reply_drops_additional_records_of_query() {
        val query = dnsQuery(name = "ads.example.com", withEdns = true)
        val reply = DnsMessage.nxdomain(query, DnsMessage.parseQuery(query)!!)

        assertEquals(0, u16(reply, 10))
        assertEquals(query.size - EDNS_OPT.size, reply.size)
    }

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
}

/** EDNS0 OPT 레코드 (실제 폰·브라우저가 질의 끝에 붙여 보내는 모양) */
internal val EDNS_OPT = byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0)

/** 표준 재귀 질의 (RD=1) 한 개를 만든다 */
internal fun dnsQuery(id: Int = 0x0102, name: String, type: Int = 1, withEdns: Boolean = false): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xFF) }
    u16(id); u16(0x0100); u16(1); u16(0); u16(0); u16(if (withEdns) 1 else 0)
    for (label in name.split('.')) {
        out.write(label.length)
        out.write(label.toByteArray(Charsets.US_ASCII))
    }
    out.write(0)
    u16(type); u16(1)
    if (withEdns) out.write(EDNS_OPT)
    return out.toByteArray()
}
