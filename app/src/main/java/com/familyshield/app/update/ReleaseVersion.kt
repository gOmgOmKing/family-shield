package com.familyshield.app.update

/** GitHub 릴리스에 함께 올리는 version.txt (versionCode 숫자 하나) 를 읽는다 */
object ReleaseVersion {
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

    fun isNewer(remote: Int, installed: Int): Boolean = remote > installed
}
