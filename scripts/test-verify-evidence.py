#!/usr/bin/env python3
"""Offline evidence reader regression fixtures; no customer records. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/"""
import hashlib
import io
import json
import runpy
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

MODULE = runpy.run_path(str(Path(__file__).with_name("verify-evidence.py")))
RUN = "95ff42c5-c805-4a40-845a-0cd5901e122a"


class EvidenceVerifierTest(unittest.TestCase):
    """EvidenceVerifierTest. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    def fixture(self, change=None):
        """Construct a synthetic archive. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        values = {"input.json": {"rows": [], "settings": {}}, "result.json": {"metrics": {}, "rows": []},
                  "review.json": {"id": RUN, "revision": 1, "stage": "READY", "history": []}}
        parts = {k: json.dumps(v).encode() for k, v in values.items()}
        man = {"format": "zhuatech-evidence/1", "project": MODULE["PROJECT"], "revision": 1, "stage": "READY",
               "runId": RUN, "entries": [{"name": k, "bytes": len(v), "sha256": hashlib.sha256(v).hexdigest()}
                                          for k, v in parts.items()]}
        if change:
            change(parts, man)
        members = [*parts.items(), ("manifest.json", json.dumps(man).encode())]
        file = io.BytesIO()
        with warnings.catch_warnings(), zipfile.ZipFile(file, "w", zipfile.ZIP_DEFLATED) as z:
            warnings.simplefilter("ignore", UserWarning)
            for k, v in members:
                z.writestr(k, v)
        return file.getvalue()

    def verify(self, raw, **params):
        """Verify a bounded evidence ZIP and return its identity. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "evidence.zip"
            path.write_bytes(raw)
            return MODULE["verify"](path, **params)

    def test_round_trip_with_independent_digest_and_id(self):
        """Verify round trip with independent digest and id. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        raw = self.fixture()
        proof = self.verify(raw, expected=hashlib.sha256(raw).hexdigest().upper(), run_id=RUN)
        self.assertEqual((proof["runId"], proof["stage"], proof["revision"]), (RUN, "READY", 1))

    def test_changed_payload(self):
        """Verify changed payload. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        def change(p, _):
            """change. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
            p["result.json"] = b'{"metrics":{},"rows":[1]}'
        with self.assertRaises(ValueError):
            self.verify(self.fixture(change))

    def test_unexpected_archive_or_identity(self):
        """Verify unexpected archive or identity. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        for opts in ({"expected": "0"*64}, {"run_id": "a-different-run"}):
            with self.subTest(opts=opts), self.assertRaises(ValueError):
                self.verify(self.fixture(), **opts)

    def test_unexpected_or_duplicate_member(self):
        """Verify unexpected or duplicate member. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        for name in ("extra.txt", "manifest.json"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.verify(self.fixture(lambda p, m: p.update({name: b"{}"})))

    def test_wrong_project_or_inconsistent_manifest(self):
        """Verify wrong project or inconsistent manifest. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        for key, value in (("project", "another-project"), ("stage", "APPROVED"), ("revision", True)):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.verify(self.fixture(lambda p,m: m.update({key: value})))

    def test_incomplete_archive(self):
        """Verify incomplete archive. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        raw = self.fixture()
        for value in (raw[:-17], b"bad archive", raw + bytes(100)):
            with self.assertRaises((ValueError, zipfile.BadZipFile)):
                self.verify(value)

if __name__ == "__main__":
    unittest.main()
