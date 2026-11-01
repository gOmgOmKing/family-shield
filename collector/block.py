"""차단 스위치 — 오너가 '켜기' 로 한 분류의 후보만 차단 파일(block.txt)로 낸다.

스위치: collector/차단스위치.txt (main, 오너가 GitHub 웹에서 고침). 애매하면 끄기.
허용 목록(lists/allow.txt)에 있는 주소와 그 하위 주소는 넣지 않는다. 합법 공식 사이트는 classify.py 가 이미 뺀다.
갑자기 많이 늘면 규칙·자료 이상으로 보고 파일을 바꾸지 않는다(어제 파일 유지).
"""
from collections import namedtuple

from classify import CATEGORIES

MAX_ENTRIES = 20_000  # 앱 쪽 1MB 상한 안에 넉넉히 (주소 평균 25바이트 → 약 0.5MB)
NEW_PER_DAY_LIMIT = 500  # 첫 실행(7일치 밀린 것)도 100개였다. 하루에 이보다 많이 늘면 멈춘다

BlockResult = namedtuple("BlockResult", "status domains")  # domains 가 None 이면 '멈춤'


def read_switch(text):
    """켜진 분류의 집합. 한 분류의 모든 줄이 정확히 '켜기'(또는 on)일 때만 켜짐."""
    if text is None:
        return set()
    values = {}
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if ":" not in line:
            continue
        name, value = (part.strip() for part in line.split(":", 1))
        if name in CATEGORIES:
            values.setdefault(name, []).append(value)
    return {name for name, vs in values.items() if all(v == "켜기" or v.lower() == "on" for v in vs)}


def parse_allow(text):
    if text is None:
        return set()
    allow = set()
    for line in text.splitlines():
        domain = line.split("#", 1)[0].strip().lower().rstrip(".")
        if domain:
            allow.add(domain)
    return allow


def build_block(ledger, enabled, allow, today):
    chosen = [e for e in ledger.values() if e.category in enabled]
    new_today = sum(e.first_seen == today for e in chosen)
    if new_today > NEW_PER_DAY_LIMIT:
        return BlockResult(f"멈춤: 오늘 새 후보 {new_today}개 (상한 {NEW_PER_DAY_LIMIT})", None)
    domains = sorted({e.domain for e in chosen if not _allowed(e.domain, allow)})
    if len(domains) > MAX_ENTRIES:
        return BlockResult(f"멈춤: {len(domains)}개 (상한 {MAX_ENTRIES})", None)
    return BlockResult(f"{len(domains)}개" if enabled else "끔 (켠 분류 없음)", domains)


def _allowed(domain, allow):
    labels = domain.split(".")
    return any(".".join(labels[i:]) in allow for i in range(len(labels)))


def render_block(domains, enabled, now_text):
    names = ", ".join(c for c in CATEGORIES if c in enabled) or "없음"
    header = [
        "# 토토 후보 차단 목록 — 자동 생성, 손으로 고치지 마세요",
        "# collector/차단스위치.txt 에서 '켜기' 로 한 분류의 후보만 들어갑니다. 설명: collector/README.md",
        "# 출처: HaGeZi 새 도메인 목록(GPL-3.0, 원자료 Stamus Labs)에서 이름만 보고 고른 후보",
        f"# 만든 시각: {now_text} (한국 시간)",
        f"# 켠 분류: {names}",
        f"# 개수: {len(domains)}",
    ]
    return "\n".join(header + list(domains)) + "\n"
