package com.familyshield.app.lists

import java.io.File
import java.util.Properties

/** 화면에 보여 줄 목록 상태. 값이 없으면(아직 한 번도 못 받음) null 그대로 둔다 */
data class ListStatus(
    val lastSuccess: Long?,
    val lastAttempt: Long?,
    val entries: Int?,
    val etag: String?,
    val error: String?,
)

/**
 * 받은 목록 파일과 그 상태를 폰 저장소에 둔다.
 * 새 파일은 임시 파일에 다 쓴 뒤 바꿔 끼우므로, 받다가 끊겨도 마지막 정상본이 남는다.
 */
class ListStore(val dir: File) {

    init {
        dir.mkdirs()
    }

    fun file(id: String): File = File(dir, "$id.txt")

    private fun metaFile(id: String) = File(dir, "$id.meta")

    fun status(id: String): ListStatus {
        val p = load(metaFile(id))
        return ListStatus(
            lastSuccess = p.getProperty(LAST_SUCCESS)?.toLongOrNull(),
            lastAttempt = p.getProperty(LAST_ATTEMPT)?.toLongOrNull(),
            entries = p.getProperty(ENTRIES)?.toIntOrNull(),
            etag = p.getProperty(ETAG),
            error = p.getProperty(ERROR),
        )
    }

    @Synchronized
    fun saveDownload(id: String, body: ByteArray, etag: String?, entries: Int, now: Long) {
        writeAtomically(file(id), body)
        editMeta(id) {
            setOrRemove(ETAG, etag)
            setProperty(ENTRIES, entries.toString())
            setProperty(LAST_SUCCESS, now.toString())
            setProperty(LAST_ATTEMPT, now.toString())
            remove(ERROR)
        }
    }

    @Synchronized
    fun markUnchanged(id: String, now: Long) = editMeta(id) {
        setProperty(LAST_SUCCESS, now.toString())
        setProperty(LAST_ATTEMPT, now.toString())
        remove(ERROR)
    }

    @Synchronized
    fun markFailure(id: String, message: String, now: Long) = editMeta(id) {
        setProperty(LAST_ATTEMPT, now.toString())
        setProperty(ERROR, message)
    }

    /** 서버가 "그만"(403/429)이라고 한 경우, 그 서버 이름별로 다시 물어도 되는 시각 */
    fun hostPausedUntil(host: String): Long = load(hostsFile()).getProperty(host)?.toLongOrNull() ?: 0L

    @Synchronized
    fun pauseHost(host: String, until: Long) {
        val p = load(hostsFile())
        p.setProperty(host, until.toString())
        writeAtomically(hostsFile(), p.toBytes())
    }

    private fun hostsFile() = File(dir, "paused-hosts.meta")

    private fun editMeta(id: String, change: Properties.() -> Unit) {
        val p = load(metaFile(id))
        p.change()
        writeAtomically(metaFile(id), p.toBytes())
    }

    private fun load(f: File) = Properties().apply {
        if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
    }

    private fun Properties.setOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else setProperty(key, value)
    }

    private fun Properties.toBytes(): ByteArray {
        val w = java.io.StringWriter()
        store(w, null)
        return w.toString().toByteArray(Charsets.UTF_8)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "파일 저장 실패: ${target.name}" }
        }
    }

    private companion object {
        const val LAST_SUCCESS = "lastSuccess"
        const val LAST_ATTEMPT = "lastAttempt"
        const val ENTRIES = "entries"
        const val ETAG = "etag"
        const val ERROR = "error"
    }
}
