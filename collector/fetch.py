"""목록 받기 — 정해진 HaGeZi 목록 두 개만 받는다.

후보 도메인에는 절대 접속하지 않는다(HTTP 도 DNS 도 없음). 그래서 주소를 받지 않고 이름(nrd/gambling)만 받는다.
상대 서버 예의(절대준수 §2): 하루 한 번, ETag 로 바뀐 것만, 압축해서, 재시도 없음,
403/429 면 그 서버에 하루 동안 묻지 않는다. 우회(User-Agent 바꾸기 등)는 하지 않는다.
"""
import gzip
import io
import urllib.error
import urllib.request
from collections import namedtuple
from datetime import datetime, timedelta
from urllib.parse import urlparse

SOURCES = {
    # HaGeZi 새 도메인 목록 — 7일 전~어제 등록된 도메인, 하루 한 번 갱신 (원자료 Stamus Labs, GPL-3.0)
    "nrd": "https://raw.githubusercontent.com/hagezi/nrd/main/domains/nrd7.txt",
    # HaGeZi 도박 목록 전체판 — 후보가 나중에 여기 오르는지로 정확도를 본다
    "gambling": "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/gambling-onlydomains.txt",
}
MAX_BYTES = 200_000_000  # 2026-10-31 nrd7 은 약 45MB, 도박 목록은 약 11MB
USER_AGENT = "FamilyShield-collector/1.0 (+https://github.com/gOmgOmKing/family-shield)"
TIMEOUT_SECONDS = 120
BLOCK = timedelta(hours=24)

Result = namedtuple("Result", "status text etag")


def fetch(name, state, now, opener=urllib.request.urlopen, max_bytes=MAX_BYTES):
    """state 의 etag·blocked_until 을 읽고, 403/429 면 blocked_until 을 적는다. etag 저장은 부른 쪽이 검증 뒤에 한다."""
    if name not in SOURCES:
        raise ValueError(f"정해진 목록(nrd, gambling)만 받는다: {name!r}")
    url = SOURCES[name]
    host = urlparse(url).hostname
    until = state.get("blocked_until", {}).get(host)
    if until and now < datetime.fromisoformat(until):
        return Result(f"쉼 ({until[:16]} 까지 차단 대기)", None, None)

    headers = {"User-Agent": USER_AGENT, "Accept-Encoding": "gzip"}
    etag = state.get("etag", {}).get(name)
    if etag:
        headers["If-None-Match"] = etag
    try:
        with opener(urllib.request.Request(url, headers=headers), timeout=TIMEOUT_SECONDS) as resp:
            body = resp.read(max_bytes + 1)
            if resp.headers.get("Content-Encoding") == "gzip" and len(body) <= max_bytes:
                body = gzip.GzipFile(fileobj=io.BytesIO(body)).read(max_bytes + 1)
            if len(body) > max_bytes:
                return Result(f"실패: 파일이 {max_bytes:,}바이트보다 큼", None, None)
            return Result("받음", body.decode("utf-8"), resp.headers.get("ETag"))
    except urllib.error.HTTPError as e:
        if e.code == 304:
            return Result("안바뀜", None, None)
        if e.code in (403, 429):
            state.setdefault("blocked_until", {})[host] = (now + BLOCK).isoformat()
            return Result(f"차단 {e.code}", None, None)
        return Result(f"실패: HTTP {e.code}", None, None)
    except (urllib.error.URLError, OSError, EOFError) as e:
        return Result(f"실패: 연결 안 됨 ({type(e).__name__})", None, None)
