"""한국 불법 토토 후보 판별 — 도메인 이름만 본다.

이 파일은 이름 글자만 본다. 주소에 접속하거나 DNS 를 묻지 않는다.
규칙마다 실제 오탐 예시가 tests/test_classify.py 에 고정돼 있다.
"""
import re
from collections import namedtuple

Verdict = namedtuple("Verdict", "category reason display")

CATEGORIES = ("한글도박어", "한글주소안내", "로마자토토어", "번호돌림", ".kr도박어")

# 합법·공식 사이트와 그 하위 주소는 절대 후보에 넣지 않는다.
# 후보에서 빼기만 하는 목록이라, 잘못 들어가도 차단이 늘지 않는다(안전한 방향).
LEGAL_OFFICIAL = (
    "dhlottery.co.kr",    # 동행복권 — 복권 수탁사업자 (lists/allow.txt 에도 있음)
    "nlotto.co.kr",       # 예전 복권 사업자 '나눔로또' 주소
    "betman.co.kr",       # 베트맨 — 체육진흥투표권(스포츠토토) 공식 온라인 발매
    "sportstoto.co.kr",   # 스포츠토토 이름의 .co.kr 주소 (빼는 쪽이라 넣어 둠)
    "kra.co.kr",          # 한국마사회 (경마)
    "kangwonland.co.kr",  # 강원랜드
    "high1.com",          # 하이원리조트 (강원랜드)
)
# 정부·비영리 기관만 받을 수 있는 주소 (복권위원회 bokgwon.go.kr, 국민체육진흥공단 kspo.or.kr 등)
LEGAL_SUFFIXES = (".go.kr", ".or.kr")

GAMBLING_HANGUL = ("토토", "카지노", "바카라", "슬롯", "먹튀", "홀덤", "파워볼", "배팅", "베팅", "벳",
                   "꽁머니", "룰렛", "포커", "사다리")
# 도박어를 품고 있지만 도박이 아닌 낱말 — 먼저 지우고 본다
HANGUL_NOT_GAMBLING = ("알파벳", "티벳", "샤벳", "소르벳", "벳푸", "엘리자벳", "토토로", "포커스", "사다리차")

# 도박·성인·불법 사이트가 바뀐 주소를 알리는 말. 그냥 '주소' 나 '새주소'(도로명)는 넣지 않는다.
ADDRESS_HANGUL = ("평생주소", "최신주소", "접속주소", "우회주소", "주소안내", "주소모음", "평생도메인", "도메인안내")

# 한국 토토 광고에서 쓰는 로마자 ('토토사이트', '먹튀폴리스', '메이저사이트')
ROMANIZED_TOTO = re.compile(r"totosite|toto-site|safetoto|mtpolice|meoktwi|muktwi|majorsite|major-site")

# 한국 표시(kor·kr·krw·won) + 도박어 + 번호: korwin249, 134korwin, krvip88, betkrw347
NUMBER_ROTATION = re.compile(
    r"(?<![a-z0-9])(?:kor|kr|krw|won)(?:win|vip|bet)\d{2,}"
    r"|(?<![a-z])\d{2,}(?:kor|kr)(?:win|vip|bet)(?![a-z])"
    r"|bet-?krw\d{2,}"
)

KR_GAMBLING_EN = re.compile(r"casino|baccarat|bacara|poker|holdem|slot|bet")
EN_NOT_GAMBLING = ("alphabet", "better", "between", "betty", "beta", "bethel", "betta", "betray",
                   "diabet", "tibet", "sherbet", "sloth")


def classify(domain):
    """후보면 Verdict(분류, 근거, 한글로 푼 주소), 아니면 None."""
    domain = domain.strip().lower().rstrip(".")
    if _is_legal(domain):
        return None
    display = to_unicode(domain)
    if display is None:
        return None
    name = display.rsplit(".", 1)[0]  # 맨 끝(.com 등)은 보지 않는다

    word = _first_word(_without(name, HANGUL_NOT_GAMBLING), GAMBLING_HANGUL)
    if word:
        return Verdict("한글도박어", f"한글 '{word}'", display)
    word = _first_word(name, ADDRESS_HANGUL)
    if word:
        return Verdict("한글주소안내", f"한글 '{word}'", display)
    m = ROMANIZED_TOTO.search(name)
    if m:
        return Verdict("로마자토토어", f"로마자 '{m.group()}'", display)
    m = NUMBER_ROTATION.search(name)
    if m:
        return Verdict("번호돌림", f"한국 표시+번호 '{m.group()}'", display)
    if domain.endswith(".kr"):
        m = KR_GAMBLING_EN.search(_without(name, EN_NOT_GAMBLING))
        if m:
            return Verdict(".kr도박어", f".kr 주소에 '{m.group()}'", display)
    return None


def _is_legal(domain):
    if domain.endswith(LEGAL_SUFFIXES):
        return True
    return any(domain == d or domain.endswith("." + d) for d in LEGAL_OFFICIAL)


def to_unicode(domain):
    """xn-- 로 된 칸을 한글로 푼다. 풀 수 없는 주소는 None."""
    labels = []
    for label in domain.split("."):
        if label.startswith("xn--"):
            try:
                label = label[4:].encode("ascii").decode("punycode")
            except (UnicodeError, ValueError):
                return None
        labels.append(label)
    return ".".join(labels)


def _without(text, words):
    for w in words:
        text = text.replace(w, " ")
    return text


def _first_word(text, words):
    return next((w for w in words if w in text), None)
