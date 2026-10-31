package com.familyshield.app.lists

/** SETTING = 주소 목록이 아닌 설정 파일 (자동 업데이트 스위치). 차단 규칙에는 들어가지 않는다 */
enum class ListKind { BLOCK, COUPANG, ALLOW, SETTING }
enum class NetworkRule { ANY, UNMETERED_ONLY }

data class ListSource(
    val id: String,
    val label: String,
    val url: String,
    val kind: ListKind,
    val network: NetworkRule,
    val maxBytes: Long,
    val minEntries: Int,
)
