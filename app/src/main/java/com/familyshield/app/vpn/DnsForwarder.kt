package com.familyshield.app.vpn

import com.familyshield.app.net.IpPacket
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 통과시킬 질의를 실제 망의 DNS 서버에 그대로 보내고, 받은 답을 질의한 앱에게 돌려준다.
 * 서버가 답하지 않으면 다음 서버를 한 번 시도할 뿐 재시도를 반복하지 않는다(앱이 스스로 다시 묻는다).
 */
class DnsForwarder(
    private val upstream: UpstreamDns,
    private val writeToTun: (ByteArray) -> Unit,
    private val onProblem: (String?) -> Unit,
) {
    // 질의가 몰려도 스레드·대기열이 끝없이 늘지 않게 상한을 둔다. 넘치면 버리고 앱의 재질의에 맡긴다
    private val pool = ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64), ThreadPoolExecutor.DiscardPolicy())

    fun submit(forward: Verdict.Forward) = pool.execute { forward(forward) }

    private fun forward(f: Verdict.Forward) {
        val network = upstream.network
        val servers = upstream.servers.take(MAX_SERVERS)
        if (servers.isEmpty()) {
            onProblem("인터넷 연결의 DNS 서버를 찾지 못했습니다")
            return
        }
        val query = f.request.payload
        for (server in servers) {
            try {
                DatagramSocket().use { s ->
                    network?.bindSocket(s)
                    s.soTimeout = TIMEOUT_MS
                    s.connect(server, DNS_PORT)
                    s.send(DatagramPacket(query, query.size))
                    val buf = ByteArray(MAX_UDP)
                    val answer = DatagramPacket(buf, buf.size)
                    s.receive(answer)
                    writeToTun(IpPacket.buildUdpReply(f.request, buf.copyOf(answer.length)))
                }
                onProblem(null)
                return
            } catch (e: IOException) {
                // 다음 서버
            }
        }
        onProblem("DNS 서버가 답하지 않습니다")
    }

    fun shutdown() {
        pool.shutdownNow()
    }

    private companion object {
        const val DNS_PORT = 53
        const val MAX_SERVERS = 2
        const val TIMEOUT_MS = 2500
        const val MAX_UDP = 65_535
    }
}
