package com.familyshield.app.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpPacketTest {

    private val phone4 = byteArrayOf(10, 111, 222.toByte(), 1)
    private val dns4 = byteArrayOf(10, 111, 222.toByte(), 2)
    private val phone6 = ByteArray(16) { if (it == 0) 0xfd.toByte() else if (it == 15) 1 else 0 }
    private val dns6 = ByteArray(16) { if (it == 0) 0xfd.toByte() else if (it == 15) 2 else 0 }
    private val payload = "hello dns".toByteArray()

    private fun parse(p: ByteArray) = IpPacket.parseUdp(p, p.size)

    @Test
    fun reads_ipv4_udp_packet() {
        val p = parse(ipv4Udp(phone4, dns4, 40000, 53, payload))
        assertNotNull(p)
        assertArrayEquals(phone4, p!!.srcAddr)
        assertArrayEquals(dns4, p.dstAddr)
        assertEquals(40000, p.srcPort)
        assertEquals(53, p.dstPort)
        assertArrayEquals(payload, p.payload)
    }

    @Test
    fun reads_only_packet_length_of_bigger_buffer() {
        val packet = ipv4Udp(phone4, dns4, 40000, 53, payload)
        val buffer = packet.copyOf(4096)
        assertArrayEquals(payload, IpPacket.parseUdp(buffer, packet.size)!!.payload)
    }

    @Test
    fun reads_ipv6_udp_packet() {
        val p = parse(ipv6Udp(phone6, dns6, 40001, 53, payload))
        assertNotNull(p)
        assertArrayEquals(phone6, p!!.srcAddr)
        assertArrayEquals(dns6, p.dstAddr)
        assertEquals(40001, p.srcPort)
        assertEquals(53, p.dstPort)
        assertArrayEquals(payload, p.payload)
    }

    @Test
    fun ignores_tcp_packet() {
        val packet = ipv4Udp(phone4, dns4, 40000, 53, payload)
        packet[9] = 6
        assertNull(IpPacket.parseUdp(packet, packet.size))
    }

    @Test
    fun ignores_ipv6_packet_with_extension_header() {
        val packet = ipv6Udp(phone6, dns6, 40000, 53, payload)
        packet[6] = 0 // hop-by-hop
        assertNull(IpPacket.parseUdp(packet, packet.size))
    }

    @Test
    fun ignores_truncated_packet() {
        val packet = ipv4Udp(phone4, dns4, 40000, 53, payload)
        assertNull(IpPacket.parseUdp(packet, packet.size - 3))
        assertNull(IpPacket.parseUdp(packet, 10))
    }

    @Test
    fun ignores_ipv6_packet_whose_length_field_exceeds_data() {
        val packet = ipv6Udp(phone6, dns6, 40000, 53, payload)
        assertNull(IpPacket.parseUdp(packet.copyOf(4096), packet.size - 3))
    }

    @Test
    fun ignores_ipv4_fragment() {
        val packet = ipv4Udp(phone4, dns4, 40000, 53, payload)
        packet[6] = 0x20 // more fragments
        assertNull(IpPacket.parseUdp(packet, packet.size))
    }

    @Test
    fun ignores_unknown_ip_version() {
        val packet = ipv4Udp(phone4, dns4, 40000, 53, payload)
        packet[0] = 0x55
        assertNull(IpPacket.parseUdp(packet, packet.size))
    }

    @Test
    fun ipv4_header_checksum_matches_known_example() {
        // RFC 1071 방식 예시로 널리 쓰이는 헤더: 4500 0073 0000 4000 4011 [b861] c0a8 0001 c0a8 00c7
        val header = hex("450000730000400040110000c0a80001c0a800c7")
        assertEquals(0xb861, IpPacket.checksum(header, 0, header.size, 0))
    }

    @Test
    fun ipv4_reply_goes_back_to_sender_with_valid_checksums() {
        val request = parse(ipv4Udp(phone4, dns4, 40000, 53, payload))!!
        val answer = "answer!".toByteArray()
        val reply = IpPacket.buildUdpReply(request, answer)

        val back = IpPacket.parseUdp(reply, reply.size)!!
        assertArrayEquals(dns4, back.srcAddr)
        assertArrayEquals(phone4, back.dstAddr)
        assertEquals(53, back.srcPort)
        assertEquals(40000, back.dstPort)
        assertArrayEquals(answer, back.payload)
        assertEquals("IPv4 header checksum", 0xFFFF, onesComplementSum(reply, 0, 20))
        assertEquals("UDP checksum", 0xFFFF, udpSumWithPseudoHeader4(reply))
    }

    @Test
    fun ipv6_reply_goes_back_to_sender_with_valid_udp_checksum() {
        val request = parse(ipv6Udp(phone6, dns6, 40001, 53, payload))!!
        val answer = "odd-length".toByteArray() + 1
        val reply = IpPacket.buildUdpReply(request, answer)

        val back = IpPacket.parseUdp(reply, reply.size)!!
        assertArrayEquals(dns6, back.srcAddr)
        assertArrayEquals(phone6, back.dstAddr)
        assertEquals(53, back.srcPort)
        assertEquals(40001, back.dstPort)
        assertArrayEquals(answer, back.payload)
        assertEquals("UDP checksum", 0xFFFF, udpSumWithPseudoHeader6(reply))
    }

    // ---- 아래는 구현과 따로 쓴 검산용 계산 (RFC 768, RFC 8200 §8.1) ----

    private fun onesComplementSum(b: ByteArray, from: Int, len: Int, start: Long = 0): Int {
        var sum = start
        var i = from
        while (i < from + len) {
            val hi = b[i].toInt() and 0xFF
            val lo = if (i + 1 < from + len) b[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
            i += 2
        }
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.toInt()
    }

    private fun udpSumWithPseudoHeader4(p: ByteArray): Int {
        val udpLen = p.size - 20
        val pseudo = p.copyOfRange(12, 20) + byteArrayOf(0, 17, (udpLen shr 8).toByte(), udpLen.toByte())
        return onesComplementSum(p, 20, udpLen, onesComplementSum(pseudo, 0, pseudo.size).toLong())
    }

    private fun udpSumWithPseudoHeader6(p: ByteArray): Int {
        val udpLen = p.size - 40
        val pseudo = p.copyOfRange(8, 40) +
            byteArrayOf(0, 0, (udpLen shr 8).toByte(), udpLen.toByte(), 0, 0, 0, 17)
        return onesComplementSum(p, 40, udpLen, onesComplementSum(pseudo, 0, pseudo.size).toLong())
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** 폰이 가짜 DNS 주소로 보내는 것과 같은 모양의 IPv4/UDP 패킷 (체크섬은 0 — 받는 쪽은 검사하지 않음) */
internal fun ipv4Udp(src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
    val total = 20 + 8 + payload.size
    val p = ByteArray(total)
    p[0] = 0x45
    p[2] = (total shr 8).toByte(); p[3] = total.toByte()
    p[6] = 0x40 // DF
    p[8] = 64; p[9] = 17
    src.copyInto(p, 12); dst.copyInto(p, 16)
    writeUdp(p, 20, srcPort, dstPort, payload)
    return p
}

internal fun ipv6Udp(src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
    val udpLen = 8 + payload.size
    val p = ByteArray(40 + udpLen)
    p[0] = 0x60
    p[4] = (udpLen shr 8).toByte(); p[5] = udpLen.toByte()
    p[6] = 17; p[7] = 64
    src.copyInto(p, 8); dst.copyInto(p, 24)
    writeUdp(p, 40, srcPort, dstPort, payload)
    return p
}

private fun writeUdp(p: ByteArray, at: Int, srcPort: Int, dstPort: Int, payload: ByteArray) {
    val len = 8 + payload.size
    p[at] = (srcPort shr 8).toByte(); p[at + 1] = srcPort.toByte()
    p[at + 2] = (dstPort shr 8).toByte(); p[at + 3] = dstPort.toByte()
    p[at + 4] = (len shr 8).toByte(); p[at + 5] = len.toByte()
    payload.copyInto(p, at + 8)
}
