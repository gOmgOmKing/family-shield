"""하루 실행 전체 시험 — 가짜 네트워크로 받고, 판별하고, 파일에 남기는 흐름을 본다."""
import json
import os
import tempfile
import unittest
from datetime import timedelta

from fetch import SOURCES
from ledger import read_daily, read_ledger
from run import run
from test_fetch import NOW, FakeOpener, FakeResponse

TOTO = "xn--2o2b43eq1nqrja.com"  # 제로백토토.com
NRD_TEXT = f"# Version: 2026.1031.0707.30\n{TOTO}\nexample.com\nsafetotosite.xyz\nnews.com\n"
GAMBLING_TEXT = "# Title: gambling\nsafetotosite.xyz\ncasino.example\n"


def ok(text, etag):
    return FakeResponse(text.encode(), {"ETag": etag})


class DailyRun(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = self._tmp.name
        self.sleeps = []

    def tearDown(self):
        self._tmp.cleanup()

    def run_once(self, opener, now=NOW):
        return run(self.dir, now, opener=opener, sleep=self.sleeps.append, min_entries=2)

    def ledger(self):
        return read_ledger(os.path.join(self.dir, "candidates.tsv"))

    def state(self):
        with open(os.path.join(self.dir, "state.json"), encoding="utf-8") as f:
            return json.load(f)

    def test_first_run_records_candidates_counts_and_summary(self):
        code = self.run_once(FakeOpener(ok(NRD_TEXT, '"n1"'), ok(GAMBLING_TEXT, '"g1"')))
        self.assertEqual(code, 0)
        ledger = self.ledger()
        self.assertEqual(set(ledger), {TOTO, "safetotosite.xyz"})
        self.assertEqual(ledger[TOTO].listed_at_find, "없음")
        self.assertEqual(ledger["safetotosite.xyz"].listed_at_find, "있음")
        row = read_daily(os.path.join(self.dir, "daily.tsv"))[-1]
        self.assertEqual((row["새도메인_상태"], row["도박목록_상태"], row["새후보"]), ("받음", "받음", "2"))
        self.assertEqual(row["새도메인_판"], "2026.1031.0707.30")
        with open(os.path.join(self.dir, "README.md"), encoding="utf-8") as f:
            self.assertIn("제로백토토.com", f.read())

    def test_waits_between_the_two_requests(self):
        self.run_once(FakeOpener(ok(NRD_TEXT, '"n1"'), ok(GAMBLING_TEXT, '"g1"')))
        self.assertEqual(len(self.sleeps), 1)
        self.assertGreaterEqual(self.sleeps[0], 2)

    def test_only_the_two_fixed_lists_are_ever_requested(self):
        opener = FakeOpener(ok(NRD_TEXT, '"n1"'), ok(GAMBLING_TEXT, '"g1"'))
        self.run_once(opener)
        self.assertEqual([r.full_url for r in opener.requests], [SOURCES["nrd"], SOURCES["gambling"]])

    def test_second_run_sends_etags_and_records_unchanged(self):
        self.run_once(FakeOpener(ok(NRD_TEXT, '"n1"'), ok(GAMBLING_TEXT, '"g1"')))
        opener = FakeOpener(304, 304)
        code = self.run_once(opener, NOW + timedelta(days=1))
        self.assertEqual(code, 0)
        self.assertEqual([r.get_header("If-none-match") for r in opener.requests], ['"n1"', '"g1"'])
        rows = read_daily(os.path.join(self.dir, "daily.tsv"))
        self.assertEqual(len(rows), 2)
        self.assertEqual((rows[-1]["새도메인_상태"], rows[-1]["새후보"]), ("안바뀜", "0"))
        self.assertEqual(len(self.ledger()), 2)

    def test_later_gambling_listing_is_recorded(self):
        self.run_once(FakeOpener(ok(NRD_TEXT, '"n1"'), ok(GAMBLING_TEXT, '"g1"')))
        self.run_once(FakeOpener(304, ok(GAMBLING_TEXT + TOTO + "\n", '"g2"')), NOW + timedelta(days=3))
        self.assertEqual(self.ledger()[TOTO].listed_on, "2026-11-04")

    def test_gambling_list_failure_marks_unknown_and_fails_the_run(self):
        code = self.run_once(FakeOpener(ok(NRD_TEXT, '"n1"'), 500))
        self.assertEqual(code, 1)
        self.assertEqual(self.ledger()[TOTO].listed_at_find, "모름")
        self.assertEqual(self.state()["etag"], {"nrd": '"n1"'})

    def test_too_few_entries_is_a_failure_and_etag_is_not_saved(self):
        code = self.run_once(FakeOpener(ok("# empty\nonly.com\n", '"n1"'), ok(GAMBLING_TEXT, '"g1"')))
        self.assertEqual(code, 1)
        self.assertEqual(self.ledger(), {})
        row = read_daily(os.path.join(self.dir, "daily.tsv"))[-1]
        self.assertIn("개수", row["새도메인_상태"])
        self.assertEqual(row["새도메인_개수"], "")
        self.assertNotIn("nrd", self.state()["etag"])

    def test_block_on_first_list_skips_second_request(self):
        opener = FakeOpener(429)
        code = self.run_once(opener)
        self.assertEqual(code, 1)
        self.assertEqual(len(opener.requests), 1)
        row = read_daily(os.path.join(self.dir, "daily.tsv"))[-1]
        self.assertEqual(row["새도메인_상태"], "차단 429")
        self.assertTrue(row["도박목록_상태"].startswith("쉼"))


if __name__ == "__main__":
    unittest.main()
