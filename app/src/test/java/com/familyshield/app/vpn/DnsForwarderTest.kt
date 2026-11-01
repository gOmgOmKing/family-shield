package com.familyshield.app.vpn

import com.familyshield.app.dns.DnsMessage
import com.familyshield.app.dns.dnsQuery
import com.familyshield.app.net.IpPacket
import com.familyshield.app.net.ipv4Udp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DnsForwarderTest {

    private val phone = byteArrayOf(10, 111, 222.toByte(), 1)
    private val fakeDns = byteArrayOf(10, 111, 222.toByte(), 2)
    private val loopback = InetAddress.getByName("127.0.0.1")

    /** 실제 망의 DNS 서버 흉내: 이름이 slow 로 시작하면 답하지 않고(느린 서버), 나머지는 바로 답한다 */
    private val upstream = DatagramSocket(0, loopback)
    private val upstreamThread = thread(isDaemon = true) {
        val buf = ByteArray(512)
        while (!upstream.isClosed) {
            val p = DatagramPacket(buf, buf.size)
            try {
                upstream.receive(p)
            } catch (e: Exception) {
                break
            }
            val query = buf.copyOf(p.length)
            if (DnsMessage.parseQuery(query)!!.name.startsWith("slow")) continue
            query[2] = (query[2].toInt() or 0x80).toByte() // 응답 표시
            upstream.send(DatagramPacket(query, query.size, p.socketAddress))
        }
    }

    private val replies = LinkedBlockingQueue<ByteArray>()
    private val forwarder = DnsForwarder(
        servers = { listOf(loopback) },
        bindToNetwork = {},
        writeToTun = { replies.add(it) },
        onProblem = {},
        port = upstream.localPort,
    )

    @After
    fun tearDown() {
        forwarder.shutdown()
        upstream.close()
        upstreamThread.join(1000)
    }

    private fun forward(id: Int, name: String) {
        val packet = ipv4Udp(phone, fakeDns, 50000 + id, 53, dnsQuery(id = id, name = name))
        forwarder.submit(Verdict.Forward(name, IpPacket.parseUdp(packet, packet.size)!!))
    }

    private fun idOf(reply: ByteArray): Int {
        val dns = IpPacket.parseUdp(reply, reply.size)!!.payload
        return ((dns[0].toInt() and 0xFF) shl 8) or (dns[1].toInt() and 0xFF)
    }

    @Test
    fun answer_from_upstream_goes_back_to_the_asking_app() {
        forward(id = 7, name = "blog.tistory.com")

        val reply = replies.poll(2, TimeUnit.SECONDS)
        assertNotNull("답이 통로로 돌아오지 않음", reply)
        assertEquals(7, idOf(reply!!))
        assertEquals(50007, IpPacket.parseUdp(reply, reply.size)!!.dstPort)
    }

    @Test
    fun slow_upstream_answers_do_not_hold_up_other_queries() {
        // 실제 망 DNS 가 늦게 답하는 질문 두 개가 먼저 와도, 뒤의 평범한 질문은 기다리지 않고 바로 답을 받아야 한다
        forward(id = 1, name = "slow1.example.com")
        forward(id = 2, name = "slow2.example.com")
        forward(id = 3, name = "www.naver.com")

        val reply = replies.poll(1, TimeUnit.SECONDS)
        assertNotNull("느린 질문 뒤에 줄 서서 1초 안에 답을 못 받음", reply)
        assertEquals(3, idOf(reply!!))
    }
}
