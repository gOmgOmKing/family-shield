package com.familyshield.app.filter

import java.util.Arrays

/**
 * 주소 집합. 수십만 개를 폰 메모리에 들고 있어야 해서 문자열 대신 64비트 해시만 정렬해 둔다.
 * (주소 100만 개에 약 8MB. 서로 다른 주소가 같은 해시가 될 확률은 사실상 0)
 */
class DomainSet private constructor(private val hashes: LongArray) {
    val size: Int get() = hashes.size

    fun contains(domain: String): Boolean = Arrays.binarySearch(hashes, hash(normalize(domain))) >= 0

    /** 파일을 읽으면서 바로 해시만 쌓는다 — 주소 문자열 수십만 개를 메모리에 동시에 들고 있지 않으려고 */
    class Builder {
        private var hashes = LongArray(1024)
        private var count = 0

        fun add(domain: String) {
            if (count == hashes.size) hashes = hashes.copyOf(count * 2)
            hashes[count++] = hash(normalize(domain))
        }

        fun build(): DomainSet {
            val sorted = hashes.copyOf(count).apply { sort() }
            return DomainSet(dedupeSorted(sorted))
        }
    }

    companion object {
        val EMPTY = DomainSet(LongArray(0))

        fun of(domains: Iterable<String>): DomainSet = Builder().apply { domains.forEach(::add) }.build()

        private fun dedupeSorted(sorted: LongArray): LongArray {
            if (sorted.isEmpty()) return sorted
            var unique = 1
            for (i in 1 until sorted.size) {
                if (sorted[i] != sorted[unique - 1]) sorted[unique++] = sorted[i]
            }
            return sorted.copyOf(unique)
        }

        /** FNV-1a 64 + murmur 마무리 섞기 */
        private fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            h = h xor (h ushr 33)
            h *= -0xae502812aa7333L
            h = h xor (h ushr 33)
            h *= -0x3b314601e57a13adL
            return h xor (h ushr 33)
        }
    }
}

fun normalize(domain: String): String = domain.trim().trimEnd('.').lowercase()

/**
 * 주소와 그 상위 주소(a.b.com → b.com)를 차례로 확인한다.
 * 허용 목록에 걸리면 차단 목록보다 우선한다 — 가족이 보낸 링크를 살리는 마지막 수단이기 때문.
 */
class DomainFilter(private val block: DomainSet, private val allow: DomainSet) {

    val blockSize: Int get() = block.size

    fun isBlocked(name: String): Boolean {
        val suffixes = suffixesOf(normalize(name))
        if (suffixes.any { allow.contains(it) }) return false
        return suffixes.any { block.contains(it) }
    }

    /** "a.b.example.com" → [a.b.example.com, b.example.com, example.com] (최상위 "com" 단독은 제외) */
    private fun suffixesOf(domain: String): List<String> {
        val result = mutableListOf(domain)
        var dot = domain.indexOf('.')
        while (dot >= 0) {
            val rest = domain.substring(dot + 1)
            if (!rest.contains('.')) break
            result.add(rest)
            dot = domain.indexOf('.', dot + 1)
        }
        return result
    }

    companion object {
        val NONE = DomainFilter(DomainSet.EMPTY, DomainSet.EMPTY)
    }
}
