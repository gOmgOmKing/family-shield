package com.familyshield.app.net

/** VPN 통로로 들어온 UDP 패킷 하나. 주소는 IPv4면 4바이트, IPv6면 16바이트 */
class UdpPacket(
    val srcAddr: ByteArray,
    val dstAddr: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

/**
 * IPv4(RFC 791) / IPv6(RFC 8200) 위의 UDP(RFC 768) 패킷 읽기와 답장 만들기.
 * DNS 질의만 다루므로 조각난 패킷·IPv6 확장 헤더는 읽지 않고 버린다(폰의 DNS 질의에는 쓰이지 않는다).
 */
object IpPacket {
    private const val UDP = 17
    private const val UDP_HEADER = 8
    private const val IPV4_HEADER = 20
    private const val IPV6_HEADER = 40

    fun parseUdp(buf: ByteArray, length: Int): UdpPacket? {
        if (length < 1) return null
        return when ((buf[0].toInt() and 0xFF) shr 4) {
            4 -> parseIpv4(buf, length)
            6 -> parseIpv6(buf, length)
            else -> null
        }
    }

    private fun parseIpv4(b: ByteArray, length: Int): UdpPacket? {
        if (length < IPV4_HEADER) return null
        val headerLen = (b[0].toInt() and 0x0F) * 4
        val total = u16(b, 2)
        val moreFragments = b[6].toInt() and 0x20 != 0
        val fragmentOffset = u16(b, 6) and 0x1FFF
        if (headerLen < IPV4_HEADER || total > length || total < headerLen + UDP_HEADER) return null
        if (moreFragments || fragmentOffset != 0 || (b[9].toInt() and 0xFF) != UDP) return null
        return readUdp(b, headerLen, total, b.copyOfRange(12, 16), b.copyOfRange(16, 20))
    }

    private fun parseIpv6(b: ByteArray, length: Int): UdpPacket? {
        if (length < IPV6_HEADER) return null
        val end = IPV6_HEADER + u16(b, 4)
        if (end > length || (b[6].toInt() and 0xFF) != UDP) return null
        return readUdp(b, IPV6_HEADER, end, b.copyOfRange(8, 24), b.copyOfRange(24, 40))
    }

    private fun readUdp(b: ByteArray, at: Int, ipEnd: Int, src: ByteArray, dst: ByteArray): UdpPacket? {
        if (at + UDP_HEADER > ipEnd) return null
        val udpLen = u16(b, at + 4)
        if (udpLen < UDP_HEADER || at + udpLen > ipEnd) return null
        return UdpPacket(src, dst, u16(b, at), u16(b, at + 2), b.copyOfRange(at + UDP_HEADER, at + udpLen))
    }

    /** request 를 보낸 쪽으로 돌아가는 답장 패킷 (주소·포트를 맞바꾼다) */
    fun buildUdpReply(request: UdpPacket, payload: ByteArray): ByteArray {
        val v6 = request.srcAddr.size == 16
        val ipHeader = if (v6) IPV6_HEADER else IPV4_HEADER
        val udpLen = UDP_HEADER + payload.size
        val p = ByteArray(ipHeader + udpLen)
        val src = request.dstAddr
        val dst = request.srcAddr

        if (v6) {
            p[0] = 0x60
            put16(p, 4, udpLen)
            p[6] = UDP.toByte()
            p[7] = 64
            src.copyInto(p, 8)
            dst.copyInto(p, 24)
        } else {
            p[0] = 0x45
            put16(p, 2, p.size)
            p[6] = 0x40 // 조각내지 않음(DF)
            p[8] = 64
            p[9] = UDP.toByte()
            src.copyInto(p, 12)
            dst.copyInto(p, 16)
            put16(p, 10, checksum(p, 0, IPV4_HEADER, 0))
        }

        put16(p, ipHeader, request.dstPort)
        put16(p, ipHeader + 2, request.srcPort)
        put16(p, ipHeader + 4, udpLen)
        payload.copyInto(p, ipHeader + UDP_HEADER)

        // 가상 헤더: 출발·도착 주소 + 프로토콜 + UDP 길이 (IPv4·IPv6 모두 합계는 같은 방식)
        val pseudo = sum(src, 0, src.size) + sum(dst, 0, dst.size) + UDP + udpLen
        val udpSum = checksum(p, ipHeader, udpLen, pseudo)
        put16(p, ipHeader + 6, if (udpSum == 0) 0xFFFF else udpSum)
        return p
    }

    /** 인터넷 체크섬(RFC 1071): 16비트 1의 보수 합의 보수 */
    fun checksum(b: ByteArray, from: Int, len: Int, initial: Long): Int {
        var s = initial + sum(b, from, len)
        while (s > 0xFFFF) s = (s and 0xFFFF) + (s shr 16)
        return s.toInt().inv() and 0xFFFF
    }

    private fun sum(b: ByteArray, from: Int, len: Int): Long {
        var s = 0L
        var i = from
        val end = from + len
        while (i + 1 < end) {
            s += u16(b, i)
            i += 2
        }
        if (i < end) s += (b[i].toInt() and 0xFF) shl 8
        return s
    }

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v shr 8).toByte()
        b[at + 1] = v.toByte()
    }
}
