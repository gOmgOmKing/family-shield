"""판별 규칙 시험 — 이름만 보고 '한국 토토 후보'인지 가린다.

예시 주소 중 punycode(xn--) 로 적힌 것은 2026-10-31 HaGeZi 새 도메인 목록(nrd7)에서 실제로 본 주소다.
"""
import unittest

from classify import CATEGORIES, LEGAL_OFFICIAL, classify


def idn(unicode_domain):
    """한글 주소를 실제 목록에 나오는 모양(xn--...)으로 바꾼다."""
    return unicode_domain.encode("idna").decode("ascii")


class HangulGamblingWords(unittest.TestCase):
    def test_hangul_toto_domain_is_candidate(self):
        v = classify("xn--2o2b43eq1nqrja.com")  # 제로백토토.com
        self.assertEqual(v.category, "한글도박어")
        self.assertEqual(v.display, "제로백토토.com")

    def test_gambling_word_wins_over_address_word(self):
        v = classify("xn--tl3br4agte4c9yae3xqwc7x6a.com")  # 뽀빠이벳평생주소.com
        self.assertEqual(v.category, "한글도박어")

    def test_casino_baccarat_meoktwi_are_candidates(self):
        for name in ("시티카지노.com", "라이브바카라.com", "먹튀검색.com", "파워볼게임.net"):
            with self.subTest(name=name):
                self.assertEqual(classify(idn(name)).category, "한글도박어")

    def test_alphabet_in_hangul_is_not_bet(self):
        self.assertIsNone(classify("xn--tl3b70nz0l.shop"))  # 알파벳.shop

    def test_tibet_and_sherbet_are_not_bet(self):
        for name in ("티벳여행.com", "샤벳가게.com"):
            with self.subTest(name=name):
                self.assertIsNone(classify(idn(name)))

    def test_totoro_is_not_toto(self):
        self.assertIsNone(classify(idn("토토로굿즈.com")))

    def test_focus_is_not_poker(self):
        self.assertIsNone(classify(idn("포커스뉴스.com")))

    def test_ladder_truck_is_not_ladder_game(self):
        self.assertIsNone(classify(idn("부산사다리차콜센터.com")))

    def test_safety_word_alone_is_not_candidate(self):
        for name in ("안전제일.com", "안전가옥.com"):
            with self.subTest(name=name):
                self.assertIsNone(classify(idn(name)))


class HangulAddressGuide(unittest.TestCase):
    def test_lifetime_address_site_is_candidate(self):
        v = classify("xn--2o2b13mwg2ym9njof7yw.com")  # 삐에로평생주소.com
        self.assertEqual(v.category, "한글주소안내")

    def test_plain_address_word_is_not_enough(self):
        self.assertIsNone(classify(idn("플레이스토어주소.com")))

    def test_road_name_new_address_is_not_candidate(self):
        self.assertIsNone(classify(idn("도로명새주소.com")))


class RomanizedTotoWords(unittest.TestCase):
    def test_totosite_family_is_candidate(self):
        for d in ("safetotosite.xyz", "kingdomtoto-site.com", "mtpolice1.xyz", "meoktwi24.com"):
            with self.subTest(domain=d):
                self.assertEqual(classify(d).category, "로마자토토어")

    def test_toto_toilet_company_is_not_candidate(self):
        for d in ("toto.com", "toto.co.jp", "totousa.com", "totokorea.co.kr", "www.toto.co.jp"):
            with self.subTest(domain=d):
                self.assertIsNone(classify(d))

    def test_totoro_fan_site_is_not_candidate(self):
        self.assertIsNone(classify("totoro-goods.com"))


class KoreanNumberRotation(unittest.TestCase):
    def test_kr_marker_with_number_is_candidate(self):
        # 앞 셋은 HaGeZi 도박 목록에 이미 있는 실제 주소, betkrw347.sbs 는 새 도메인 목록에서 본 주소
        for d in ("korwin249.kr", "134korwin.com", "krvip88.net", "betkrw347.sbs"):
            with self.subTest(domain=d):
                self.assertEqual(classify(d).category, "번호돌림")

    def test_similar_letters_without_kr_marker_are_not_candidate(self):
        for d in ("skorbet500.com", "korwingestionale.com", "92pkrwinvip.online", "babilbet655.com"):
            with self.subTest(domain=d):
                self.assertIsNone(classify(d))


class KrDomainWithGamblingWord(unittest.TestCase):
    def test_kr_domain_with_baccarat_is_candidate(self):
        self.assertEqual(classify("baccarat-sites.co.kr").category, ".kr도박어")

    def test_kr_domain_with_bet_is_candidate(self):
        self.assertEqual(classify("1xbet-app.kr").category, ".kr도박어")

    def test_english_words_containing_bet_or_slot_are_not_candidate(self):
        for d in ("alphabet.kr", "betterlife.kr", "better.co.kr", "fastsloth.kr", "between.kr"):
            with self.subTest(domain=d):
                self.assertIsNone(classify(d))

    def test_bet_outside_kr_is_not_candidate_by_itself(self):
        self.assertIsNone(classify("alphabet.com"))
        self.assertIsNone(classify("bestbet.com"))


class LegalOfficialSitesNeverCandidate(unittest.TestCase):
    def test_official_lottery_and_betting_sites_are_never_candidates(self):
        for d in LEGAL_OFFICIAL:
            with self.subTest(domain=d):
                self.assertIsNone(classify(d))

    def test_subdomains_of_official_sites_are_never_candidates(self):
        # 앞에 한글 '카지노' 를 붙여 다른 규칙이 반드시 걸리게 한 뒤, 그래도 빠지는지 본다
        prefix = idn("카지노")
        for d in LEGAL_OFFICIAL:
            with self.subTest(domain=d):
                self.assertIsNone(classify(f"{prefix}.{d}"))

    def test_dhlottery_is_in_official_list(self):
        self.assertIn("dhlottery.co.kr", LEGAL_OFFICIAL)

    def test_betman_would_match_bet_rule_but_is_excluded(self):
        self.assertIsNone(classify("betman.co.kr"))
        self.assertIsNone(classify("www.betman.co.kr"))

    def test_government_and_nonprofit_kr_domains_are_never_candidates(self):
        self.assertIsNone(classify(idn("카지노.example.go.kr")))
        self.assertIsNone(classify("casino.example.or.kr"))


class InputHandling(unittest.TestCase):
    def test_upper_case_and_trailing_dot_are_normalized(self):
        self.assertEqual(classify("SafeTotoSite.XYZ.").category, "로마자토토어")

    def test_broken_punycode_is_ignored_not_crashing(self):
        self.assertIsNone(classify("xn--zz-!!!.com"))

    def test_every_verdict_uses_a_known_category_and_gives_a_reason(self):
        for d in ("xn--2o2b43eq1nqrja.com", "safetotosite.xyz", "korwin249.kr", "baccarat-sites.co.kr",
                  "xn--2o2b13mwg2ym9njof7yw.com"):
            with self.subTest(domain=d):
                v = classify(d)
                self.assertIn(v.category, CATEGORIES)
                self.assertTrue(v.reason)


if __name__ == "__main__":
    unittest.main()
