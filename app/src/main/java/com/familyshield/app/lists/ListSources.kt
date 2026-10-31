package com.familyshield.app.lists

/**
 * 폰이 받아 오는 목록 전부. 주소·크기 상한·최소 개수는 여기 한 곳에서만 정한다.
 * (확인일 2026-10-31: HaGeZi Pro 21만·Pop-Up 5만·TIF mini 19만 개, List-KR DNS 약 390줄 / 2026-11-01: HaGeZi 도박 62만 개)
 */
object ListSources {

    private const val MB = 1_000_000L

    /** 자동 업데이트 스위치 파일 이름 (저장소 lists/AUTO_UPDATE.txt). 오너가 '켜기'/'끄기' 한 줄로 켜고 끈다 */
    const val UPDATE_SWITCH_ID = "AUTO_UPDATE"

    /** 오너가 GitHub 에서 직접 고치는 목록과 스위치. 몇 시간마다, 어떤 망에서든 받는다 (파일이 작음) */
    fun own(githubRepo: String): List<ListSource> = listOf(
        own(githubRepo, "block", "직접 관리 차단 목록", ListKind.BLOCK),
        own(githubRepo, "coupang", "쿠팡 파트너스 목록", ListKind.COUPANG),
        own(githubRepo, "allow", "허용 목록", ListKind.ALLOW),
        own(githubRepo, UPDATE_SWITCH_ID, "자동 업데이트 스위치", ListKind.SETTING),
    )

    /** 커뮤니티 목록. 하루 한 번, 와이파이처럼 요금 안 드는 망에서만 받는다 (파일이 큼) */
    val community: List<ListSource> = listOf(
        ListSource(
            "hagezi-pro", "HaGeZi Pro (광고·추적·사기)",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro-onlydomains.txt",
            ListKind.BLOCK, NetworkRule.UNMETERED_ONLY, maxBytes = 16 * MB, minEntries = 50_000,
        ),
        ListSource(
            "hagezi-popupads", "HaGeZi 팝업 광고",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/popupads-onlydomains.txt",
            ListKind.BLOCK, NetworkRule.UNMETERED_ONLY, maxBytes = 8 * MB, minEntries = 10_000,
        ),
        ListSource(
            "hagezi-tif-mini", "HaGeZi 위협 정보 (사기·악성)",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/tif.mini-onlydomains.txt",
            ListKind.BLOCK, NetworkRule.UNMETERED_ONLY, maxBytes = 16 * MB, minEntries = 50_000,
        ),
        // 오너 결정 2026-11-01. 전체판(62만 개, 약 11MB). 폰이 무거우면 중간판 gambling.medium-onlydomains.txt (18만 개)
        ListSource(
            "hagezi-gambling", "HaGeZi 도박 사이트",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/gambling-onlydomains.txt",
            ListKind.BLOCK, NetworkRule.UNMETERED_ONLY, maxBytes = 32 * MB, minEntries = 100_000,
        ),
        ListSource(
            "listkr-dns", "List-KR DNS (한국 광고)",
            "https://cdn.jsdelivr.net/npm/@list-kr/filterslists@latest/dist/filterslist-DNS.txt",
            ListKind.BLOCK, NetworkRule.UNMETERED_ONLY, maxBytes = 2 * MB, minEntries = 100,
        ),
    )

    fun all(githubRepo: String): List<ListSource> = own(githubRepo) + community

    private fun own(repo: String, id: String, label: String, kind: ListKind) = ListSource(
        id, label, "https://raw.githubusercontent.com/$repo/main/lists/$id.txt",
        kind, NetworkRule.ANY, maxBytes = 1 * MB, minEntries = 0,
    )
}
