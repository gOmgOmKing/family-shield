package com.familyshield.app.vpn

/**
 * 막은 횟수와 최근 막은 주소. 브라우저가 주소 하나에 질의를 여러 번(A·AAAA·HTTPS) 보내므로
 * 같은 주소를 10초 안에 다시 막은 것은 한 번으로 센다.
 */
class BlockLog(startCount: Long, private val keep: Int = 50) {

    class Entry(val name: String, val time: Long)

    private val entries = ArrayDeque<Entry>()
    private val lastSeen = HashMap<String, Long>()

    @Volatile
    var total: Long = startCount
        private set

    @Synchronized
    fun record(name: String, now: Long) {
        val last = lastSeen[name]
        if (last != null && now - last < SAME_VISIT_MS) return
        lastSeen[name] = now
        if (lastSeen.size > keep * 4) lastSeen.entries.removeAll { now - it.value >= SAME_VISIT_MS }
        total++
        entries.addFirst(Entry(name, now))
        while (entries.size > keep) entries.removeLast()
    }

    @Synchronized
    fun recent(): List<Entry> = entries.toList()

    private companion object {
        const val SAME_VISIT_MS = 10_000L
    }
}
