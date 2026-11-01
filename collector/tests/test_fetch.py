"""목록 받기 시험 — 정해진 두 주소만, 바뀐 것만, 403/429 면 하루 쉰다 (절대준수 §2). 네트워크는 가짜로 바꾼다."""
import gzip
import io
import unittest
import urllib.error
from datetime import datetime, timedelta, timezone

from fetch import SOURCES, fetch

KST = timezone(timedelta(hours=9))
NOW = datetime(2026, 11, 1, 19, 17, tzinfo=KST)


class FakeResponse:
    def __init__(self, body, headers=None):
        self._body = io.BytesIO(body)
        self.headers = headers or {}

    def read(self, n=-1):
        return self._body.read(n)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class FakeOpener:
    """요청을 기록하고, 정해 둔 답(응답 또는 HTTP 오류 코드)을 차례로 돌려준다."""

    def __init__(self, *answers):
        self.answers = list(answers)
        self.requests = []

    def __call__(self, req, timeout=None):
        self.requests.append(req)
        answer = self.answers.pop(0)
        if isinstance(answer, int):
            raise urllib.error.HTTPError(req.full_url, answer, "fake", {}, None)
        if isinstance(answer, Exception):
            raise answer
        return answer


class OnlyFixedSources(unittest.TestCase):
    def test_refuses_any_address_other_than_the_two_lists_without_network(self):
        opener = FakeOpener()
        for name in ("https://xn--2o2b43eq1nqrja.com/", "safetotosite.xyz", "other"):
            with self.subTest(name=name):
                with self.assertRaises(ValueError):
                    fetch(name, {}, NOW, opener)
        self.assertEqual(opener.requests, [])

    def test_both_sources_are_hagezi_lists_on_github(self):
        self.assertEqual(set(SOURCES), {"nrd", "gambling"})
        for url in SOURCES.values():
            self.assertTrue(url.startswith("https://raw.githubusercontent.com/hagezi/"))


class Download(unittest.TestCase):
    def test_returns_text_and_etag(self):
        opener = FakeOpener(FakeResponse(b"a.com\nb.com\n", {"ETag": '"v1"'}))
        r = fetch("nrd", {}, NOW, opener)
        self.assertEqual((r.status, r.text, r.etag), ("받음", "a.com\nb.com\n", '"v1"'))

    def test_gzip_body_is_unpacked(self):
        body = gzip.compress("a.com\n".encode())
        opener = FakeOpener(FakeResponse(body, {"Content-Encoding": "gzip"}))
        self.assertEqual(fetch("nrd", {}, NOW, opener).text, "a.com\n")

    def test_names_itself_and_asks_for_compression(self):
        opener = FakeOpener(FakeResponse(b""))
        fetch("nrd", {}, NOW, opener)
        req = opener.requests[0]
        self.assertIn("FamilyShield", req.get_header("User-agent"))
        self.assertEqual(req.get_header("Accept-encoding"), "gzip")

    def test_too_big_file_is_a_failure(self):
        opener = FakeOpener(FakeResponse(b"x" * 101))
        r = fetch("nrd", {}, NOW, opener, max_bytes=100)
        self.assertTrue(r.status.startswith("실패"))
        self.assertIsNone(r.text)

    def test_too_big_after_unpacking_is_a_failure(self):
        body = gzip.compress(b"x" * 1000)
        opener = FakeOpener(FakeResponse(body, {"Content-Encoding": "gzip"}))
        r = fetch("nrd", {}, NOW, opener, max_bytes=100)
        self.assertTrue(r.status.startswith("실패"))


class OnlyWhatChanged(unittest.TestCase):
    def test_sends_saved_etag(self):
        opener = FakeOpener(304)
        fetch("nrd", {"etag": {"nrd": '"v1"'}}, NOW, opener)
        self.assertEqual(opener.requests[0].get_header("If-none-match"), '"v1"')

    def test_not_modified_is_reported_as_unchanged(self):
        r = fetch("nrd", {"etag": {"nrd": '"v1"'}}, NOW, FakeOpener(304))
        self.assertEqual(r.status, "안바뀜")
        self.assertIsNone(r.text)


class BlockedMeansStopForADay(unittest.TestCase):
    def test_403_and_429_stop_asking_that_server_for_24_hours(self):
        for code in (403, 429):
            with self.subTest(code=code):
                state = {}
                r = fetch("nrd", state, NOW, FakeOpener(code))
                self.assertEqual(r.status, f"차단 {code}")
                later = FakeOpener()
                r2 = fetch("gambling", state, NOW + timedelta(hours=23), later)
                self.assertTrue(r2.status.startswith("쉼"))
                self.assertEqual(later.requests, [])

    def test_asks_again_after_24_hours(self):
        state = {}
        fetch("nrd", state, NOW, FakeOpener(429))
        opener = FakeOpener(FakeResponse(b"a.com\n"))
        r = fetch("nrd", state, NOW + timedelta(hours=24, minutes=1), opener)
        self.assertEqual(r.status, "받음")

    def test_server_error_is_a_failure_but_not_a_block(self):
        state = {}
        r = fetch("nrd", state, NOW, FakeOpener(500))
        self.assertEqual(r.status, "실패: HTTP 500")
        self.assertNotIn("blocked_until", state)

    def test_network_error_is_a_failure(self):
        r = fetch("nrd", {}, NOW, FakeOpener(urllib.error.URLError("no route")))
        self.assertTrue(r.status.startswith("실패"))


if __name__ == "__main__":
    unittest.main()
