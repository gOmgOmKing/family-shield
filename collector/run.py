"""하루 한 번 실행: 두 목록을 받고, 새 도메인 이름을 판별해 후보 장부와 요약을 남긴다.

사용: python run.py <결과 폴더>
받기에 실패하면 그 사실을 daily.tsv 와 요약에 적고 종료 코드 1 로 끝난다(GitHub 이 빨간불·메일로 알림).
"""
import json
import os
import sys
import time
import urllib.request
from collections import Counter
from datetime import datetime, timedelta, timezone

from classify import classify, to_unicode
from fetch import fetch
from ledger import append_daily, read_daily, read_ledger, render_summary, update_ledger, write_ledger

KST = timezone(timedelta(hours=9))
MIN_ENTRIES = 100_000  # 2026-10-31 기준 nrd7 267만 개, 도박 목록 62만 개. 이보다 적으면 깨진 파일로 본다
PAUSE_SECONDS = 3
OK_STATUSES = ("받음", "안바뀜")


def run(data_dir, now, opener=urllib.request.urlopen, sleep=time.sleep, min_entries=MIN_ENTRIES):
    state_path = os.path.join(data_dir, "state.json")
    state = _load_json(state_path)
    nrd = _get("nrd", state, now, opener, min_entries)
    sleep(PAUSE_SECONDS)
    gambling = _get("gambling", state, now, opener, min_entries)

    verdicts = {}
    if nrd.domains is not None:
        verdicts = {d: v for d in nrd.domains if (v := classify(d))}
    gambling_set = set(gambling.domains) if gambling.domains is not None else None

    ledger_path = os.path.join(data_dir, "candidates.tsv")
    ledger, new = update_ledger(read_ledger(ledger_path), verdicts, gambling_set, now.strftime("%Y-%m-%d"))
    write_ledger(ledger_path, ledger)

    daily_path = os.path.join(data_dir, "daily.tsv")
    append_daily(daily_path, {
        "실행시각": now.strftime("%Y-%m-%d %H:%M"),
        "새도메인_상태": nrd.status,
        "새도메인_판": nrd.version,
        "새도메인_개수": _count(nrd.domains),
        "도박목록_상태": gambling.status,
        "도박목록_개수": _count(gambling.domains),
        "도박목록_한글주소": _count_hangul(gambling.domains),
        "새후보": str(len(new)) if nrd.status in OK_STATUSES else "",
        "새후보_분류별": ", ".join(f"{c} {n}" for c, n in Counter(ledger[d].category for d in new).items()),
        "누적후보": str(len(ledger)),
        "누적_HaGeZi확인": str(sum(bool(e.listed_on) for e in ledger.values())),
    })
    with open(os.path.join(data_dir, "README.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write(render_summary(ledger, read_daily(daily_path), now.strftime("%Y-%m-%d %H:%M")))
    with open(state_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(state, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    return 0 if nrd.status in OK_STATUSES and gambling.status in OK_STATUSES else 1


class _List:
    def __init__(self, status, domains=None, version=""):
        self.status, self.domains, self.version = status, domains, version


def _get(name, state, now, opener, min_entries):
    """받아서 검증까지 통과해야 ETag 를 저장한다 — 깨진 파일 뒤에 '안바뀜'으로 영영 건너뛰지 않게."""
    r = fetch(name, state, now, opener)
    if r.text is None:
        return _List(r.status)
    domains, version = _parse(r.text)
    if len(domains) < min_entries:
        return _List(f"실패: 개수가 너무 적음 ({len(domains)}개)")
    if r.etag:
        state.setdefault("etag", {})[name] = r.etag
    return _List(r.status, domains, version)


def _parse(text):
    domains, version = [], ""
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("# Version:"):
            version = line.split(":", 1)[1].strip()
        elif line and not line.startswith("#"):
            domains.append(line.lower())
    return domains, version


def _count(domains):
    return "" if domains is None else str(len(domains))


def _count_hangul(domains):
    if domains is None:
        return ""
    return str(sum("xn--" in d and any("가" <= ch <= "힣" for ch in (to_unicode(d) or "")) for d in domains))


def _load_json(path):
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        return json.load(f)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    sys.exit(run(out, datetime.now(KST)))
