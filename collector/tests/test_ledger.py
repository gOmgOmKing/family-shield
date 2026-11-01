"""후보 장부 시험 — 처음 발견일을 지키고, HaGeZi 도박 목록에 오른 날을 기록하고, 정확도 숫자를 낸다."""
import os
import tempfile
import unittest

from classify import Verdict
from ledger import (DAILY_FIELDS, append_daily, category_stats, read_daily, read_ledger, render_summary,
                    update_ledger, write_ledger)


def verdicts(*domains, category="한글도박어"):
    return {d: Verdict(category, "시험 근거", d) for d in domains}


class UpdateLedger(unittest.TestCase):
    def test_new_candidate_gets_today_as_first_seen(self):
        ledger, new = update_ledger({}, verdicts("a.com"), gambling=set(), today="2026-11-01")
        self.assertEqual(ledger["a.com"].first_seen, "2026-11-01")
        self.assertEqual(new, ["a.com"])

    def test_first_seen_is_kept_when_found_again(self):
        ledger, _ = update_ledger({}, verdicts("a.com"), gambling=set(), today="2026-11-01")
        ledger, new = update_ledger(ledger, verdicts("a.com"), gambling=set(), today="2026-11-02")
        self.assertEqual(ledger["a.com"].first_seen, "2026-11-01")
        self.assertEqual(new, [])

    def test_candidate_already_in_gambling_list_is_marked_at_find(self):
        ledger, _ = update_ledger({}, verdicts("a.com"), gambling={"a.com"}, today="2026-11-01")
        self.assertEqual(ledger["a.com"].listed_at_find, "있음")
        self.assertEqual(ledger["a.com"].listed_on, "2026-11-01")

    def test_later_listing_in_gambling_list_records_that_day(self):
        ledger, _ = update_ledger({}, verdicts("a.com"), gambling=set(), today="2026-11-01")
        ledger, _ = update_ledger(ledger, {}, gambling={"a.com"}, today="2026-11-05")
        self.assertEqual(ledger["a.com"].listed_at_find, "없음")
        self.assertEqual(ledger["a.com"].listed_on, "2026-11-05")

    def test_listing_day_is_not_moved_by_later_runs(self):
        ledger, _ = update_ledger({}, verdicts("a.com"), gambling=set(), today="2026-11-01")
        ledger, _ = update_ledger(ledger, {}, gambling={"a.com"}, today="2026-11-05")
        ledger, _ = update_ledger(ledger, {}, gambling={"a.com"}, today="2026-11-06")
        self.assertEqual(ledger["a.com"].listed_on, "2026-11-05")

    def test_unknown_gambling_list_is_recorded_as_unknown_not_absent(self):
        ledger, _ = update_ledger({}, verdicts("a.com"), gambling=None, today="2026-11-01")
        self.assertEqual(ledger["a.com"].listed_at_find, "모름")
        self.assertEqual(ledger["a.com"].listed_on, "")

    def test_candidate_that_left_the_new_domain_list_stays_in_ledger(self):
        ledger, _ = update_ledger({}, verdicts("a.com"), gambling=set(), today="2026-11-01")
        ledger, _ = update_ledger(ledger, verdicts("b.com"), gambling=set(), today="2026-11-09")
        self.assertIn("a.com", ledger)


class CategoryStats(unittest.TestCase):
    def setUp(self):
        ledger, _ = update_ledger({}, verdicts("old.com"), gambling={"old.com"}, today="2026-11-01")
        ledger, _ = update_ledger(ledger, verdicts("x.com", "y.com", "z.com"), gambling=set(), today="2026-11-01")
        ledger, _ = update_ledger(ledger, verdicts("r.kr", category=".kr도박어"), gambling=None, today="2026-11-01")
        self.ledger, _ = update_ledger(ledger, {}, gambling={"old.com", "x.com"}, today="2026-11-03")

    def test_counts_per_category(self):
        s = category_stats(self.ledger)["한글도박어"]
        self.assertEqual((s.total, s.already_listed, s.newly_found, s.later_listed, s.unknown), (4, 1, 3, 1, 0))

    def test_rate_is_later_listed_over_newly_found(self):
        self.assertAlmostEqual(category_stats(self.ledger)["한글도박어"].rate, 1 / 3)

    def test_rate_is_none_when_nothing_newly_found(self):
        s = category_stats(self.ledger)[".kr도박어"]
        self.assertEqual(s.unknown, 1)
        self.assertIsNone(s.rate)

    def test_total_row_adds_all_categories(self):
        s = category_stats(self.ledger)["합계"]
        self.assertEqual((s.total, s.newly_found, s.later_listed, s.unknown), (5, 3, 1, 1))


class LedgerFile(unittest.TestCase):
    def test_write_then_read_gives_same_ledger(self):
        ledger, _ = update_ledger({}, verdicts("a.com", "b.com"), gambling={"b.com"}, today="2026-11-01")
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "candidates.tsv")
            write_ledger(path, ledger)
            self.assertEqual(read_ledger(path), ledger)

    def test_missing_file_reads_as_empty_ledger(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(read_ledger(os.path.join(d, "none.tsv")), {})


class DailyFile(unittest.TestCase):
    def test_old_daily_file_gets_new_column_without_losing_rows(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "daily.tsv")
            old_fields = [f for f in DAILY_FIELDS if f != "차단파일"]
            with open(path, "w", encoding="utf-8", newline="") as f:
                f.write("\t".join(old_fields) + "\n" + "\t".join(["2026-11-01 09:35"] + ["x"] * (len(old_fields) - 1)) + "\n")
            append_daily(path, {"실행시각": "2026-11-02 19:17", "차단파일": "끔"})
            rows = read_daily(path)
            self.assertEqual([r["실행시각"] for r in rows], ["2026-11-01 09:35", "2026-11-02 19:17"])
            self.assertEqual((rows[0]["차단파일"], rows[1]["차단파일"]), ("", "끔"))
            self.assertEqual(rows[0]["새도메인_상태"], "x")


class Summary(unittest.TestCase):
    def test_rate_without_data_is_shown_as_not_yet_not_zero(self):
        ledger, _ = update_ledger({}, verdicts("r.kr", category=".kr도박어"), gambling=None, today="2026-11-01")
        text = render_summary(ledger, daily_rows=[], now_text="2026-11-01 19:17")
        row = next(line for line in text.splitlines() if line.startswith("| .kr도박어"))
        self.assertIn("아직 없음", row)
        self.assertNotIn("0%", row)

    def test_hangul_count_note_uses_last_run_that_received_the_list(self):
        received = {"실행시각": "2026-11-01 19:17", "새후보": "3", "새도메인_상태": "받음", "도박목록_상태": "받음",
                    "도박목록_개수": "626739", "도박목록_한글주소": "14"}
        unchanged = dict(received, 실행시각="2026-11-01 20:00", 도박목록_상태="안바뀜", 도박목록_개수="", 도박목록_한글주소="")
        text = render_summary({}, daily_rows=[received, unchanged], now_text="2026-11-01 20:00")
        self.assertIn("한글 주소 14개", text)

    def test_summary_says_observation_only_when_switch_is_all_off(self):
        text = render_summary({}, daily_rows=[], now_text="2026-11-02 19:17", enabled=set())
        self.assertIn("지금은 관찰만", text)
        self.assertNotIn("차단에 쓰입니다", text)

    def test_summary_names_enabled_categories_as_used_for_blocking(self):
        text = render_summary({}, daily_rows=[], now_text="2026-11-02 19:17", enabled={"번호돌림", "한글도박어"})
        self.assertIn("켠 분류(한글도박어, 번호돌림)", text)
        self.assertIn("차단에 쓰입니다", text)
        self.assertNotIn("지금은 관찰만", text)

    def test_run_history_shows_block_file_status(self):
        row = {"실행시각": "2026-11-02 19:17", "새후보": "3", "새도메인_상태": "받음", "도박목록_상태": "받음",
               "차단파일": "끔 (켠 분류 없음)"}
        text = render_summary({}, daily_rows=[row], now_text="2026-11-02 19:17")
        self.assertIn("| 2026-11-02 19:17 | 3 | 받음 | 받음 | 끔 (켠 분류 없음) |", text)

    def test_recent_candidates_are_listed_with_readable_name(self):
        ledger, _ = update_ledger({}, {"xn--2o2b43eq1nqrja.com": Verdict("한글도박어", "한글 '토토'", "제로백토토.com")},
                                  gambling=set(), today="2026-11-01")
        text = render_summary(ledger, daily_rows=[], now_text="2026-11-01 19:17")
        self.assertIn("제로백토토.com", text)


if __name__ == "__main__":
    unittest.main()
