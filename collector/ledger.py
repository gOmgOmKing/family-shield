"""후보 장부 — 언제 처음 찾았는지, HaGeZi 도박 목록에 언제 올랐는지 기록하고 정확도 숫자를 낸다.

모르는 값은 모른다고 적는다(§1): 도박 목록을 못 받은 날 찾은 후보는 '모름', 비율을 낼 수 없으면 '아직 없음'.
"""
import csv
import os
from collections import namedtuple

from classify import CATEGORIES

Entry = namedtuple("Entry", "domain display category reason first_seen listed_at_find listed_on")
Stats = namedtuple("Stats", "total already_listed newly_found later_listed unknown rate")

LEDGER_HEADER = ("도메인", "한글표시", "분류", "근거", "처음발견", "발견때_HaGeZi", "HaGeZi_오른날")
DAILY_FIELDS = ("실행시각", "새도메인_상태", "새도메인_판", "새도메인_개수", "도박목록_상태", "도박목록_개수",
                "도박목록_한글주소", "새후보", "새후보_분류별", "누적후보", "누적_HaGeZi확인")
TOTAL = "합계"


def update_ledger(ledger, verdicts, gambling, today):
    """오늘 찾은 후보를 더하고, 도박 목록에 새로 오른 후보에 오늘 날짜를 적는다.

    gambling 이 None 이면 오늘 도박 목록을 못 받은 것이다.
    """
    ledger = dict(ledger)
    new = []
    for domain, v in sorted(verdicts.items()):
        if domain in ledger:
            continue
        if gambling is None:
            listed_at_find, listed_on = "모름", ""
        elif domain in gambling:
            listed_at_find, listed_on = "있음", today
        else:
            listed_at_find, listed_on = "없음", ""
        ledger[domain] = Entry(domain, v.display, v.category, v.reason, today, listed_at_find, listed_on)
        new.append(domain)
    if gambling is not None:
        for domain, e in ledger.items():
            if not e.listed_on and domain in gambling:
                ledger[domain] = e._replace(listed_on=today)
    return ledger, new


def category_stats(ledger):
    groups = {c: [] for c in CATEGORIES}
    for e in ledger.values():
        groups.setdefault(e.category, []).append(e)
    stats = {c: _stats(entries) for c, entries in groups.items()}
    stats[TOTAL] = _stats(list(ledger.values()))
    return stats


def _stats(entries):
    already = sum(e.listed_at_find == "있음" for e in entries)
    newly = [e for e in entries if e.listed_at_find == "없음"]
    later = sum(bool(e.listed_on) for e in newly)
    unknown = sum(e.listed_at_find == "모름" for e in entries)
    rate = later / len(newly) if newly else None
    return Stats(len(entries), already, len(newly), later, unknown, rate)


def read_ledger(path):
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8", newline="") as f:
        rows = list(csv.reader(f, delimiter="\t"))
    return {row[0]: Entry(*row) for row in rows[1:] if row}


def write_ledger(path, ledger):
    entries = sorted(ledger.values(), key=lambda e: (e.first_seen, e.domain))
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(LEDGER_HEADER)
        w.writerows(entries)


def read_daily(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def append_daily(path, row):
    is_new = not os.path.exists(path)
    with open(path, "a", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, DAILY_FIELDS, delimiter="\t", lineterminator="\n")
        if is_new:
            w.writeheader()
        w.writerow(row)


def render_summary(ledger, daily_rows, now_text):
    stats = category_stats(ledger)
    lines = [
        "# 토토 후보 관찰 결과",
        "",
        "자동으로 만든 파일입니다(손으로 고치지 마세요). **관찰만 하고, 차단에는 쓰지 않습니다.**",
        f"마지막 실행: {now_text} (한국 시간). 전체 후보: [candidates.tsv](candidates.tsv), "
        "실행 기록: [daily.tsv](daily.tsv).",
        "",
        "## 분류별 숫자",
        "",
        "| 분류 | 후보 | 찾을 때 이미 HaGeZi 에 있음 | 새로 찾음 | 그중 나중에 HaGeZi 에 오름 | 비율 | 확인 못 함 |",
        "|---|---|---|---|---|---|---|",
    ]
    for name in (*CATEGORIES, TOTAL):
        s = stats[name]
        rate = "아직 없음" if s.rate is None else f"{s.rate:.0%} ({s.later_listed}/{s.newly_found})"
        lines.append(f"| {name} | {s.total} | {s.already_listed} | {s.newly_found} | {s.later_listed} | {rate} | {s.unknown} |")
    hangul_note = ""
    last = next((row for row in reversed(daily_rows) if row.get("도박목록_한글주소")), None)
    if last:
        hangul_note = f" (가장 최근에 받은 도박 목록 {last['도박목록_개수']}개 중 한글 주소 {last['도박목록_한글주소']}개)"
    lines += [
        "",
        "- **비율** = 새로 찾은 후보 중 나중에 HaGeZi 도박 목록에도 오른 비율. 새로 찾은 것이 없으면 '아직 없음'.",
        "- **확인 못 함** = 찾은 날 도박 목록을 받지 못해 비교하지 못한 후보.",
        f"- HaGeZi 는 한글 주소를 거의 싣지 않습니다{hangul_note}. 그래서 한글 분류는 비율이 낮게 나올 수 있습니다 — "
        "아래 이름을 눈으로 보고 '도박 주소가 맞는지' 판단하는 것이 더 정확합니다.",
        "",
        "## 최근 실행 기록 (최근 14번)",
        "",
        "| 실행 시각 | 새 후보 | 새 도메인 목록 | 도박 목록 |",
        "|---|---|---|---|",
    ]
    for row in daily_rows[-14:][::-1]:
        lines.append(f"| {row['실행시각']} | {row['새후보']} | {row['새도메인_상태']} | {row['도박목록_상태']} |")
    lines += [
        "",
        "## 최근 후보 100개 (이름을 한글로 풀어 씀)",
        "",
        "| 처음 발견 | 분류 | 이름 | 근거 | HaGeZi 에 오른 날 |",
        "|---|---|---|---|---|",
    ]
    recent = sorted(ledger.values(), key=lambda e: (e.first_seen, e.domain), reverse=True)[:100]
    for e in recent:
        lines.append(f"| {e.first_seen} | {e.category} | {e.display} | {e.reason} | {e.listed_on or '-'} |")
    return "\n".join(lines) + "\n"
