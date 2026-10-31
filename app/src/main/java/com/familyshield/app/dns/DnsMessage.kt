package com.familyshield.app.dns

/** 질의에서 꺼낸 내용. questionEnd = 질문 부분이 끝나는 위치(차단 답장에 질문을 그대로 되돌려 주려고 기억) */
class DnsQuestion(val id: Int, val name: String, val type: Int, internal val questionEnd: Int)

/** DNS 메시지(RFC 1035) 중 이 앱에 필요한 두 가지: 질의 읽기, "그런 주소 없음(NXDOMAIN)" 답장 만들기 */
object DnsMessage {
    private const val HEADER = 12
    private const val RCODE_NXDOMAIN = 3

    fun parseQuery(msg: ByteArray): DnsQuestion? {
        if (msg.size < HEADER) return null
        val flags = u16(msg, 2)
        val isResponse = flags and 0x8000 != 0
        if (isResponse || u16(msg, 4) != 1) return null

        val name = StringBuilder()
        var pos = HEADER
        while (true) {
            if (pos >= msg.size) return null
            val len = msg[pos].toInt() and 0xFF
            pos++
            if (len == 0) break
            if (len > 63 || pos + len > msg.size) return null // 압축 포인터(0xC0~)도 여기서 거절된다
            if (name.isNotEmpty()) name.append('.')
            for (i in pos until pos + len) name.append((msg[i].toInt() and 0xFF).toChar().lowercaseChar())
            pos += len
            if (name.length > 253) return null
        }
        if (pos + 4 > msg.size) return null
        return DnsQuestion(u16(msg, 0), name.toString(), u16(msg, pos), pos + 4)
    }

    /** 질의의 헤더와 질문만 남기고, 응답 표시·재귀 가능·NXDOMAIN 을 켠다 */
    fun nxdomain(query: ByteArray, question: DnsQuestion): ByteArray {
        val reply = query.copyOf(question.questionEnd)
        val queryFlags = u16(query, 2)
        val opcodeAndRd = queryFlags and 0x7900 // opcode(4비트) + RD
        val flags = 0x8000 or opcodeAndRd or 0x0080 or RCODE_NXDOMAIN
        put16(reply, 2, flags)
        put16(reply, 4, 1)
        put16(reply, 6, 0)
        put16(reply, 8, 0)
        put16(reply, 10, 0)
        return reply
    }

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v shr 8).toByte()
        b[at + 1] = v.toByte()
    }
}
