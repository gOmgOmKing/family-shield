package com.familyshield.app.lists

import com.familyshield.app.filter.ListParser
import java.net.URI

/**
 * 목록을 차례로(동시에 X) 받아 저장한다. 상대 서버 예의(docs/절대준수.md):
 * - 요청 사이에 쉼(pause)을 둔다
 * - 바뀐 게 없으면 서버가 304로 짧게 답하도록 ETag 를 보낸다
 * - 403/429(그만 오라는 뜻)를 받으면 그 서버에는 하루 동안 아예 묻지 않는다. 재시도·우회 없음
 * 받은 내용이 목록처럼 보이지 않으면(웹페이지, 너무 적음) 버리고 마지막 정상본을 계속 쓴다.
 */
class ListUpdater(
    private val store: ListStore,
    private val http: Http,
    private val now: () -> Long,
    private val pause: () -> Unit,
) {

    /** @return 저장된 목록 내용이 하나라도 바뀌었으면 true (차단 규칙을 다시 만들어야 함) */
    fun update(sources: List<ListSource>, unmetered: Boolean): Boolean {
        var changed = false
        var asked = false
        for (source in sources) {
            if (source.network == NetworkRule.UNMETERED_ONLY && !unmetered) continue
            val host = hostOf(source.url)
            if (store.hostPausedUntil(host) > now()) {
                store.markFailure(source.id, "서버가 잠시 쉬어 달라고 해서 내일 다시 받습니다", now())
                continue
            }
            if (asked) pause()
            asked = true
            changed = fetch(source, host) || changed
        }
        return changed
    }

    private fun fetch(source: ListSource, host: String): Boolean {
        val etag = store.status(source.id).etag?.takeIf { store.file(source.id).exists() }
        when (val r = http.get(source.url, etag, source.maxBytes)) {
            is HttpResult.Ok -> return accept(source, r)
            HttpResult.NotModified -> store.markUnchanged(source.id, now())
            is HttpResult.Refused -> {
                store.pauseHost(host, now() + ONE_DAY)
                store.markFailure(source.id, "서버가 거절함(${r.code}) — 내일 다시 받습니다", now())
            }
            HttpResult.TooLarge -> store.markFailure(source.id, "파일이 너무 큼 (${source.maxBytes / 1_000_000}MB 초과)", now())
            is HttpResult.Failed -> store.markFailure(source.id, "받기 실패: ${r.message}", now())
        }
        return false
    }

    private fun accept(source: ListSource, r: HttpResult.Ok): Boolean {
        if (looksLikeWebPage(r.body)) {
            store.markFailure(source.id, "목록 대신 웹페이지가 왔음", now())
            return false
        }
        val entries = ListParser.parse(r.body.inputStream().reader()).count()
        if (entries < source.minEntries) {
            store.markFailure(source.id, "주소가 너무 적음 (${entries}개)", now())
            return false
        }
        store.saveDownload(source.id, r.body, r.etag, entries, now())
        return true
    }

    private fun looksLikeWebPage(body: ByteArray): Boolean {
        val head = String(body, 0, minOf(body.size, 512), Charsets.UTF_8).trimStart().lowercase()
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<?xml")
    }

    private fun hostOf(url: String): String = URI(url).host ?: url

    private companion object {
        const val ONE_DAY = 24 * 60 * 60 * 1000L
    }
}
