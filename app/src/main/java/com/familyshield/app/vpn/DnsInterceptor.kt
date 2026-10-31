package com.familyshield.app.vpn

import com.familyshield.app.dns.DnsMessage
import com.familyshield.app.filter.DomainFilter
import com.familyshield.app.net.IpPacket
import com.familyshield.app.net.UdpPacket

/** VPN 통로로 들어온 패킷 하나를 어떻게 할지 */
sealed interface Verdict {
    /** 막을 주소: reply 를 그대로 통로에 써 넣으면 "그런 주소 없음" 답장이 된다 */
    class Block(val name: String, val reply: ByteArray) : Verdict

    /** 통과: request.payload(원래 질의)를 진짜 DNS 서버에 보내고, 받은 답을 request 쪽으로 돌려준다 */
    class Forward(val name: String, val request: UdpPacket) : Verdict

    /** DNS 질의가 아님: 버린다 */
    data object Ignore : Verdict
}

/** currentFilter 는 매번 다시 읽는다 — 목록이 새로 받아지면 VPN을 껐다 켜지 않아도 바로 반영되게 */
class DnsInterceptor(private val currentFilter: () -> DomainFilter) {

    fun inspect(packet: ByteArray, length: Int): Verdict {
        val udp = IpPacket.parseUdp(packet, length) ?: return Verdict.Ignore
        if (udp.dstPort != DNS_PORT) return Verdict.Ignore
        val question = DnsMessage.parseQuery(udp.payload) ?: return Verdict.Ignore

        if (!currentFilter().isBlocked(question.name)) return Verdict.Forward(question.name, udp)
        val dnsReply = DnsMessage.nxdomain(udp.payload, question)
        return Verdict.Block(question.name, IpPacket.buildUdpReply(udp, dnsReply))
    }

    private companion object {
        const val DNS_PORT = 53
    }
}
