"""차단 스위치 시험 — 오너가 켠 분류만, 허용 목록을 빼고, 갑자기 늘면 멈춘다."""
import os
import unittest

from block import MAX_ENTRIES, NEW_PER_DAY_LIMIT, build_block, parse_allow, read_switch, render_block
from classify import CATEGORIES, Verdict
from ledger import update_ledger

SWITCH_PATH = os.path.join(os.path.dirname(__file__), "..", "차단스위치.txt")


def ledger_with(entries, today="2026-11-01"):
    """entries: (도메인, 분류) 목록"""
    ledger, _ = update_ledger({}, {d: Verdict(c, "시험", d) for d, c in entries}, gambling=set(), today=today)
    return ledger


class Switch(unittest.TestCase):
    def test_shipped_switch_file_has_every_category(self):
        # 켜기·끄기는 오너가 고르는 값이라 보지 않는다. 이 시험이 실패하면 수집 전체가 멈춘다(이력 #28).
        with open(SWITCH_PATH, encoding="utf-8") as f:
            lines = [line.split("#", 1)[0] for line in f]
        names = {line.split(":", 1)[0].strip() for line in lines if ":" in line}
        self.assertLessEqual(set(CATEGORIES), names)

    def test_on_category_is_enabled(self):
        self.assertEqual(read_switch("한글도박어: 켜기\n번호돌림: 끄기\n"), {"한글도박어"})

    def test_on_in_english_is_accepted(self):
        self.assertEqual(read_switch("로마자토토어: ON\n"), {"로마자토토어"})

    def test_note_after_hash_is_ignored(self):
        self.assertEqual(read_switch("한글도박어: 켜기   # 11-15 결정\n"), {"한글도박어"})

    def test_ambiguous_value_means_off(self):
        for value in ("켜기요", "켬", "켜기?", "", "끄기 켜기"):
            with self.subTest(value=value):
                self.assertEqual(read_switch(f"한글도박어: {value}\n"), set())

    def test_conflicting_lines_mean_off(self):
        self.assertEqual(read_switch("한글도박어: 켜기\n한글도박어: 끄기\n"), set())

    def test_comment_lines_and_unknown_names_are_ignored(self):
        text = "# 한글도박어: 켜기\n없는분류: 켜기\n.kr도박어: 켜기\n"
        self.assertEqual(read_switch(text), {".kr도박어"})

    def test_missing_switch_file_means_all_off(self):
        self.assertEqual(read_switch(None), set())


class AllowList(unittest.TestCase):
    def test_reads_domains_and_strips_comments(self):
        text = "# 설명\ndhlottery.co.kr            # 동행복권\n\nShop.Example.COM\n"
        self.assertEqual(parse_allow(text), {"dhlottery.co.kr", "shop.example.com"})

    def test_missing_allow_file_is_empty(self):
        self.assertEqual(parse_allow(None), set())


class BuildBlock(unittest.TestCase):
    def setUp(self):
        self.ledger = ledger_with([("a.com", "한글도박어"), ("b.com", "한글주소안내"), ("c.kr", ".kr도박어")])

    def test_nothing_enabled_gives_empty_list(self):
        self.assertEqual(build_block(self.ledger, set(), set(), "2026-11-01").domains, [])

    def test_only_enabled_categories_are_included(self):
        r = build_block(self.ledger, {"한글도박어", ".kr도박어"}, set(), "2026-11-01")
        self.assertEqual(r.domains, ["a.com", "c.kr"])

    def test_allowed_domains_and_their_subdomains_are_left_out(self):
        ledger = ledger_with([("a.com", "한글도박어"), ("x.shop.example.com", "한글도박어")])
        r = build_block(ledger, {"한글도박어"}, {"a.com", "shop.example.com"}, "2026-11-01")
        self.assertEqual(r.domains, [])

    def test_allow_entry_does_not_hide_unrelated_lookalike(self):
        ledger = ledger_with([("bad-a.com", "한글도박어")])
        r = build_block(ledger, {"한글도박어"}, {"a.com"}, "2026-11-01")
        self.assertEqual(r.domains, ["bad-a.com"])

    def test_sudden_jump_of_new_candidates_stops_the_update(self):
        many = [(f"x{i}.com", "한글도박어") for i in range(NEW_PER_DAY_LIMIT + 1)]
        r = build_block(ledger_with(many), {"한글도박어"}, set(), "2026-11-01")
        self.assertIsNone(r.domains)
        self.assertIn("멈춤", r.status)

    def test_many_old_candidates_are_not_a_jump(self):
        many = [(f"x{i}.com", "한글도박어") for i in range(NEW_PER_DAY_LIMIT + 1)]
        r = build_block(ledger_with(many, today="2026-10-01"), {"한글도박어"}, set(), "2026-11-01")
        self.assertEqual(len(r.domains), NEW_PER_DAY_LIMIT + 1)

    def test_jump_in_a_category_that_is_off_does_not_stop(self):
        many = [(f"x{i}.com", "한글주소안내") for i in range(NEW_PER_DAY_LIMIT + 1)]
        r = build_block(ledger_with(many + [("a.com", "한글도박어")]), {"한글도박어"}, set(), "2026-11-01")
        self.assertEqual(r.domains, ["a.com"])

    def test_too_many_entries_stops_the_update(self):
        many = [(f"x{i}.com", "한글도박어") for i in range(MAX_ENTRIES + 1)]
        r = build_block(ledger_with(many, today="2026-10-01"), {"한글도박어"}, set(), "2026-11-01")
        self.assertIsNone(r.domains)
        self.assertIn("멈춤", r.status)


class RenderBlock(unittest.TestCase):
    def domain_lines(self, text):
        return [line for line in text.splitlines() if line and not line.startswith("#")]

    def test_empty_file_has_only_comment_lines(self):
        text = render_block([], set(), "2026-11-01 19:17")
        self.assertEqual(self.domain_lines(text), [])
        self.assertIn("켠 분류: 없음", text)

    def test_file_lists_one_domain_per_line_with_header(self):
        text = render_block(["a.com", "c.kr"], {"한글도박어", ".kr도박어"}, "2026-11-01 19:17")
        self.assertEqual(self.domain_lines(text), ["a.com", "c.kr"])
        self.assertIn("개수: 2", text)
        self.assertIn("한글도박어", text)


if __name__ == "__main__":
    unittest.main()
