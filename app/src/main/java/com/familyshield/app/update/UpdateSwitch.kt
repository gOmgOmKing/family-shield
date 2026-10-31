package com.familyshield.app.update

/**
 * 자동 업데이트 스위치 파일(lists/AUTO_UPDATE.txt)을 읽는다.
 * '#' 설명 줄을 뺀 첫 줄이 정확히 '켜기'(또는 on)일 때만 켜진 것으로 본다. 애매하면 꺼짐 — 부모님 폰에 뜻밖의 설치 창이 뜨지 않게.
 */
object UpdateSwitch {
    fun isOn(text: String): Boolean {
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return false
        return first == "켜기" || first.equals("on", ignoreCase = true)
    }
}
