package com.familyshield.app.vpn

import com.familyshield.app.net.IpPacket
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 통과시킬 질의를 실제 망의 DNS 서버에 그대로 보내고, 받은 답을 질의한 앱에게 돌려준다.
 * 서버가 답하지 않으면 다음 서버를 한 번 시도할 뿐 재시도를 반복하지 않는다(앱이 스스로 다시 묻는다).
 * servers·bindToNetwork 는 UpstreamDns 가 아는 실제 망과 그 DNS 서버 (시험에서는 PC 안의 가짜 서버)
 */
class DnsForwarder(
    private val servers: () -> List<InetAddress>,
    private val bindToNetwork: (DatagramSocket) -> Unit,
    private val writeToTun: (ByteArray) -> Unit,
    private val onProblem: (String?) -> Unit,
    private val port: Int = DNS_PORT,
) {
    // 질의가 몰려도 스레드·대기열이 끝없이 늘지 않게 상한을 둔다. 넘치면 버리고 앱의 재질의에 맡긴다.
    // 기본 스레드 수도 상한과 같게 둔다 — ThreadPoolExecutor 는 대기열이 꽉 차야 기본 수 위로 스레드를 늘려서,
    // 기본을 2로 두면 늦게 답하는 질문 둘이 폰 전체의 DNS 를 붙잡는다. 한가하면 30초 뒤 스레드가 사라진다
    private val pool = ThreadPoolExecutor(MAX_THREADS, MAX_THREADS, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64), ThreadPoolExecutor.DiscardPolicy())
        .apply { allowCoreThreadTimeOut(true) }

    fun submit(forward: Verdict.Forward) = pool.execute { forward(forward) }

    private fun forward(f: Verdict.Forward) {
        val servers = servers().take(MAX_SERVERS)
        if (servers.isEmpty()) {
            onProblem("인터넷 연결의 DNS 서버를 찾지 못했습니다")
            return
        }
        val query = f.request.payload
        for (server in servers) {
            try {
                DatagramSocket().use { s ->
                    bindToNetwork(s)
                    s.soTimeout = TIMEOUT_MS
                    s.connect(server, port)
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
        const val MAX_THREADS = 8
        const val MAX_SERVERS = 2
        const val TIMEOUT_MS = 2500
        const val MAX_UDP = 65_535
    }
}
