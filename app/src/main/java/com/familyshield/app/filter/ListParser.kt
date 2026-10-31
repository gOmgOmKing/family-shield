package com.familyshield.app.filter

import java.io.Reader

/**
 * 차단 목록 파일에서 "주소 전체를 막는" 줄만 골라 주소로 바꾼다.
 * 받아들이는 모양: `ads.example.com`, `0.0.0.0 ads.example.com`, `||ads.example.com^`, `*.ads.example.com`.
 * 나머지(경로 규칙, `$` 옵션, 화면 숨김 규칙, `@@` 예외)는 DNS로 흉내 낼 수 없으므로 버린다.
 * 특히 `@@` 예외를 차단으로 잘못 읽으면 살려야 할 사이트를 막게 되므로 절대 받아들이지 않는다.
 */
object ListParser {

    fun parse(reader: Reader): Sequence<String> =
        reader.buffered().lineSequence().flatMap { domainsIn(it) }

    private fun domainsIn(rawLine: String): List<String> {
        val line = rawLine.substringBefore(" #").substringBefore("\t#").trim()
        if (line.isEmpty() || line[0] == '#' || line[0] == '!' || line.startsWith("@@")) return emptyList()

        if (line.startsWith("||")) {
            if (!line.endsWith("^")) return emptyList()
            return listOfNotNull(validDomain(line.substring(2, line.length - 1)))
        }

        val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
        if (tokens.size >= 2 && isIpAddress(tokens[0])) {
            return tokens.drop(1).mapNotNull { validDomain(it) }
        }
        if (tokens.size != 1) return emptyList()
        return listOfNotNull(validDomain(tokens[0].removePrefix("*.")))
    }

    private val LOCAL_NAMES = setOf("localhost.localdomain", "ip6-localhost.localdomain")

    private fun validDomain(candidate: String): String? {
        val d = candidate.trimEnd('.').lowercase()
        if (!d.contains('.') || d in LOCAL_NAMES || isIpAddress(d)) return null
        val labels = d.split('.')
        val ok = labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first() != '-' && label.last() != '-' &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
        }
        return if (ok && d.length <= 253) d else null
    }

    private fun isIpAddress(token: String): Boolean =
        token.contains(':') || token.all { it.isDigit() || it == '.' }
}
