package com.familyshield.app.lists

import com.familyshield.app.filter.DomainFilter
import com.familyshield.app.filter.DomainSet
import com.familyshield.app.filter.ListParser
import java.io.Reader

/** 저장된 목록들을 모아 차단 규칙 하나로 만든다. 받은 사본이 없으면 앱에 들어 있는 사본(bundled)을 쓴다 */
object FilterBuilder {

    fun build(
        sources: List<ListSource>,
        store: ListStore,
        openBundled: (id: String) -> Reader?,
        coupangOn: Boolean,
    ): DomainFilter {
        val block = DomainSet.Builder()
        val allow = DomainSet.Builder()
        for (source in sources) {
            val target = when (source.kind) {
                ListKind.BLOCK -> block
                ListKind.COUPANG -> if (coupangOn) block else continue
                ListKind.ALLOW -> allow
                ListKind.SETTING -> continue
            }
            open(source, store, openBundled)?.use { reader -> ListParser.parse(reader).forEach(target::add) }
        }
        return DomainFilter(block.build(), allow.build())
    }

    private fun open(source: ListSource, store: ListStore, openBundled: (String) -> Reader?): Reader? {
        val downloaded = store.file(source.id)
        return if (downloaded.exists()) downloaded.reader(Charsets.UTF_8) else openBundled(source.id)
    }
}
