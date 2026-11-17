#!/usr/bin/env python3
"""Check one zhuatech-vad-timeline evidence archive in place; no network, extraction or dependencies.
上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
"""
import argparse
import hashlib
import io
import json
import re
import struct
import sys
import uuid
import zipfile
import zlib
from pathlib import Path

PROJECT = "zhuatech-vad-timeline"
PAYLOAD = {"input.json", "result.json", "review.json"}
MEMBERS = PAYLOAD | {"manifest.json"}
LIMIT = 16 * 1024 * 1024


def check(condition, message):
    """Enforce a precondition. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    if not condition:
        raise ValueError(message)


def digest(raw):
    """Compute SHA-256. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    return hashlib.sha256(raw).hexdigest()


def decode(raw):
    """Parse a strict JSON object. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    def unique(pairs):
        """Reject duplicate JSON keys. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        out = {}
        for key, value in pairs:
            check(key not in out, "duplicate JSON key")
            out[key] = value
        return out
    def invalid(_):
        """Reject non-finite numeric constants. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        raise ValueError("non-finite JSON number")
    value = json.loads(raw.decode("utf-8"), object_pairs_hook=unique, parse_constant=invalid)
    check(isinstance(value, dict), "expected JSON object")
    return value


def verify(path, expected=None, run_id=None):
    """Verify a bounded evidence ZIP and return its identity. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    with Path(path).open("rb") as handle:
        raw = handle.read(LIMIT + 65537)
    check(22 <= len(raw) <= LIMIT + 65536, "archive size outside supported limit")
    actual = digest(raw)
    if expected is not None:
        check(isinstance(expected, str) and re.fullmatch("[0-9a-fA-F]{64}", expected) and
              actual == expected.lower(), "external archive SHA-256 mismatch")
    # The producer writes a non-spanned, non-ZIP64 package with exactly four entries and no comment.
    end = struct.unpack("<4s4H2IH", raw[-22:])
    check(end[:5] == (b"PK\x05\x06", 0, 0, 4, 4) and end[-1] == 0 and
          end[5] + end[6] == len(raw) - 22 and end[5] <= 4096, "unexpected ZIP directory")
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        entries = z.infolist()
        check(len(entries) == 4 and {e.filename for e in entries} == MEMBERS, "unexpected ZIP members")
        check(sum(e.file_size for e in entries) <= LIMIT and
              all(0 <= e.file_size <= LIMIT and not e.flag_bits & 1 and
                  e.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED) for e in entries),
              "unsupported member size or encoding")
        parts = {e.filename: z.read(e) for e in entries}
    manifest = decode(parts["manifest.json"])
    check(manifest.get("format") == "zhuatech-evidence/1" and manifest.get("project") == PROJECT,
          "unexpected project or evidence format")
    rid, rev, state = (manifest.get(k) for k in ("runId", "revision", "stage"))
    check(isinstance(rid, str) and str(uuid.UUID(rid)) == rid and
          type(rev) is int and rev >= 1 and state in ("READY", "APPROVED", "REJECTED", "WITHDRAWN"),
          "invalid manifest record")
    if run_id is not None:
        check(run_id == rid, "unexpected batch identifier")
    index = manifest.get("entries")
    check(isinstance(index, list) and len(index) == len(PAYLOAD) and
          all(isinstance(v, dict) and isinstance(v.get("name"), str) for v in index) and
          {v["name"] for v in index} == PAYLOAD, "invalid file manifest")
    for item in index:
        name = item["name"]
        check(type(item.get("bytes")) is int and len(parts[name]) == item["bytes"] and
              digest(parts[name]) == item.get("sha256"), "member checksum mismatch: " + name)
    record, batch, result = (decode(parts[k]) for k in ("review.json", "input.json", "result.json"))
    check((record.get("id"), record.get("revision"), record.get("stage")) == (rid, rev, state) and
          type(record.get("revision")) is int and isinstance(record.get("history"), list),
          "manifest does not match review snapshot")
    check(isinstance(batch.get("rows"), list) and isinstance(batch.get("settings"), dict) and
          isinstance(result.get("rows"), list) and isinstance(result.get("metrics"), dict),
          "invalid batch input or result shape")
    return dict(project=PROJECT, runId=rid, revision=rev, stage=state, sha256=actual)


def main():
    """Run the local CLI. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive")
    parser.add_argument("--sha256", help="expected archive digest from an independent trusted record")
    parser.add_argument("--run", help="expected batch UUID")
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.archive, args.sha256, args.run), sort_keys=True))
    except (ValueError, OSError, zipfile.BadZipFile, EOFError, RecursionError, zlib.error, struct.error):
        print("FAIL: evidence archive, record identity or digest did not verify.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
